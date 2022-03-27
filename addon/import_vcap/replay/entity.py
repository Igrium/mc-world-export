from io import BytesIO
import math
import time
import itertools
from typing import IO, Generic, Sequence, TypeVar
from bmesh import new
from bpy.types import Mesh, Collection, Context, Material, Object, PoseBone
from mathutils import Euler, Matrix, Quaternion, Vector

from ..vcap.import_obj import load as load_obj
import xml.etree.ElementTree as ET
import bpy  

class AnimChannel:
    __slots__ = (
        # Datapath of the fcurve
        'datapath',
        # Name of the bone in the file. ROOT for the root bone.
        'bone_name',
        # A dict of all the keyframes in the channel.
        'keyframes'
    )
    
    datapath: str
    bone_name: str
    keyframes: dict[int, Sequence[float]]
    
    def __init__(self, bone_name: str, datapath: str) -> None:
        self.bone_name = bone_name
        self.datapath = datapath
        self.keyframes = {}

def _simple_load_obj(context: Context, file_contents: str, unique_materials: dict[str, Material]):
    obj = BytesIO(bytes(file_contents, 'utf-8'))
    return load_obj(context, obj, use_split_objects=False, use_split_groups=False, use_groups_as_vgroups=True, unique_materials=unique_materials)

def load_entity(file: IO[str], context: Context, collection: Collection, materials: dict[str, Material] = {}):
    """Load a replay entity into Blender

    Args:
        file (IO[str]): Raw XML file
        context (Context): Blender context
        collection (Collection): collection to add to.

    Raises:
        Exception: If the XML is malformatted
    """
    
    def convert_vector(input=(0, 0, 0)):
       return Vector((input[0], -input[2], input[1]))
    
    start_time = time.time()
    tree = ET.parse(file)
    entity = tree.getroot()
    name = entity.get('name')
    if not name:
        name = 'entity'
    
    # MODELS
    model = entity.find('model')
    if model is None:
        raise Exception("Entity XML is missing model tag.")
    
    mesh_tag = model.find('mesh')
    parsed_objs: list[Object] = []

    multipart = ('rig-type' in model.attrib) and (model.attrib['rig-type'] == 'multipart')
    
    if multipart:
        armature_obj, bone_def, meshes = parse_multipart(model, context, collection, name=f'{name}.bones', materials=materials)

        for mesh in meshes.keys():
            obj = bpy.data.objects.new(f'{name}.mesh', mesh)
            collection.objects.link(obj)

            group = obj.vertex_groups.new(name=bone_def[meshes[mesh]])
            group.add(range(0, len(mesh.vertices)), 1, type='REPLACE')

            parsed_objs.append(obj)
            ...
        
    else:
        armature_obj, bone_def = parse_armature(model, context, collection, name=f'{name}.bones')

        if mesh_tag is not None:
            meshes, mats, vertex_groups = _simple_load_obj(context, mesh_tag.text, materials)
            
            for mesh in meshes:
                new_object = bpy.data.objects.new(f'{name}.mesh', mesh)
                collection.objects.link(new_object)
                # new_object.rotation_euler[0] = math.radians(90) // Armature handles this
                
                for group_name, group_indices in vertex_groups.items():
                    group = new_object.vertex_groups.new(name=group_name.decode('utf-8', "replace"))
                    group.add(group_indices, 1.0, 'REPLACE')
                parsed_objs.append(new_object)
        else:
            print("Warning: no mesh found in entity XML.")
    
    # Parent mesh to armature

    for obj in parsed_objs:
        obj.parent = armature_obj
        obj.parent_type = 'ARMATURE'  
    
    # ANIMATION
    anim = entity.find('anim')
    if (anim is not None):
        root_pos = AnimChannel('ROOT', 'location')
        root_rot = AnimChannel('ROOT', 'rotation_quaternion')
        root_scale = AnimChannel('ROOT', 'scale')
        
        root_scale.keyframes[0] = [1, 1, 1] # If the file doesn't have any scale keyframes.
        
        pos_channels: dict[PoseBone, AnimChannel] = {}
        rot_channels: dict[PoseBone, AnimChannel] = {}
        scale_channels: dict[PoseBone, AnimChannel] = {}
        
        armature_obj.rotation_mode = 'QUATERNION'
        render = context.scene.render
        scene_framerate = render.fps / render.fps_base
        
        fps = anim.get('fps')
        anim_start_time = float(anim.get('start-time', "0"))

        if fps:
            framerate = float(fps)
        else:
            framerate = scene_framerate
        
        animtext = anim.text # cache the text for optimization
        for index, frame in enumerate(animtext.splitlines()):
            frame = frame.strip()
            
            # scene_frame = index / framerate * scene_framerate
            # Note: gonna have to support framerate matching later.
            scene_frame = index
            
            bones = frame.split(';')
            
            # Root transform
            root_str = bones[0].strip()
            if len(root_str) > 0:
                root_vals = list(map(lambda i: float(i), root_str.split(' ')))
                length = len(root_vals)
                if length >= 4:
                    rotation = Quaternion(root_vals[0:4])
                    rotation.rotate(Euler((math.radians(90), 0, 0)))
                    root_rot.keyframes[scene_frame] = rotation
                
                if length >= 7:
                    location = root_vals[4:7]
                    # Switch coordinate space
                    root_pos.keyframes[scene_frame] = (location[0], -location[2], location[1])
                
                if length >= 10:
                    root_scale.keyframes[scene_frame] = root_vals[7:10]
                    # armature_obj.scale = convert_vector(root_vals[7:10])
                    # armature_obj.keyframe_insert('scale', frame=scene_frame )
            
            for def_index, bone_str in enumerate(bones[1:]):
                bone_str = bone_str.strip()
                if (len(bone_str) == 0): continue
                
                bone_vals = [float(i) for i in bone_str.split(' ')]
                if len(bone_vals) == 0: continue
                
                # Get the pose bone based on the definition order.
                bone = armature_obj.pose.bones[bone_def[def_index]]
                
                if len(bone_vals) >= 4:
                    if bone in rot_channels:
                        channel = rot_channels[bone]
                    else:
                        channel = AnimChannel(bone.name, f'pose.bones["{bone.name}"].rotation_quaternion')
                        rot_channels[bone] = channel
                    
                    channel.keyframes[scene_frame] = bone_vals[0:4]
                    # bone.rotation_quaternion = bone_vals[0:4]
                    # bone.keyframe_insert('rotation_quaternion', frame=scene_frame)
                
                if len(bone_vals) >= 7:
                    if bone in pos_channels:
                        channel = pos_channels[bone]
                    else:
                        channel = AnimChannel(bone.name, f'pose.bones["{bone.name}"].location')
                        pos_channels[bone] = channel
                    
                    channel.keyframes[scene_frame] = bone_vals[4:7]      
                    # bone.location = bone_vals[4:7]
                    # bone.keyframe_insert('location', frame=scene_frame)
                
                if len(bone_vals) >= 10:
                    if bone in scale_channels:
                        channel = scale_channels[bone]
                    else:
                        channel = AnimChannel(bone.name, f'pose.bones["{bone.name}"].scale')
                        channel.keyframes[0] = (1, 1, 1) # If the bone doesn't have have any scale keyframes.
                        scale_channels[bone] = channel
                    
                    channel.keyframes[scene_frame] = bone_vals[7:10]
                    # bone.scale = bone_vals[7:10]
                    # bone.keyframe_insert('scale', frame=scene_frame)
                    
        anim_data = armature_obj.animation_data_create()
        action = bpy.data.actions.new(name=f"{name}_action")
        anim_data.action = action
        
        # Add F curves
        def add_curve(channel: AnimChannel, index: int):
            curve = action.fcurves.new(data_path=channel.datapath, index=index)
            keyframe_points = curve.keyframe_points
            
            # Gotta love data manipulation.
            keyframes = [(
                (frame / framerate + anim_start_time) * scene_framerate,
                val[index]
            ) for frame, val in channel.keyframes.items()]
            
            
            keyframe_points.add(len(keyframes))
            keyframe_points.foreach_set('co', list(itertools.chain.from_iterable(keyframes)))
            keyframe_points.foreach_set('interpolation', [1] * len(keyframes))
        
        for i in range(0, 3): add_curve(root_pos, i)
        for i in range(0, 4): add_curve(root_rot, i)
        for i in range(0, 3): add_curve(root_scale, i)
        
        for channel in pos_channels.values():
            for i in range(0, 3): add_curve(channel, i)
        
        for channel in rot_channels.values():
            for i in range(0, 4): add_curve(channel, i)
            
        for channel in scale_channels.values():
            for i in range(0, 3): add_curve(channel, i)
        
    print(f"Parsed entity {name} in {time.time() - start_time} seconds.")

def parse_armature(model: ET.Element, context: Context, collection: Collection, name="entity") -> tuple[Object, list[str]]:
    """Load an armature from a model XML element.

    Args:
        model (ET.Element): Element to load (should be `model` element)
        context (Context): Blender context.
        name (str, optional): Name of the armature. Defaults to "entity".

    Returns:
        tuple[Armature, list[str]]: The generated armature object and the bone names in definition order.
    """
    armature = bpy.data.armatures.new(name)
    obj = bpy.data.objects.new(name, armature)

    collection.objects.link(obj)
    context.view_layer.objects.active = obj
    
    definition_order: list[str] = []

    bpy.ops.object.mode_set(mode='EDIT')
    edit_bones = armature.edit_bones
    id = 0
    
    def load_bone(element: ET.Element):
        if element.tag != 'bone': return
        nonlocal id

        attrib = element.attrib
        
        if 'name' in attrib.keys():
            name = attrib['name']
        else:
            name = f'bone{id}'
        
        if 'len' in attrib.keys():
            length = float(attrib['len'])
        else:
            length = '.16'

        bone = edit_bones.new(name)
        bone.head = [0, 0, 0]
        bone.tail = [0, length, 0]

        if 'pos' in attrib:
            pos = Vector(map(float, attrib['pos'].split(',')))
        else:
            pos = Vector()
        
        if 'rot' in attrib:
            rot = Quaternion(map(float, attrib['rot'].split(',')))
        else:
            rot = Quaternion()

        transformation: Matrix = Matrix.Translation(pos) @ rot.to_matrix().to_4x4()
        bone.transform(transformation)
        
        id += 1
        definition_order.append(name)
        
        for child in element:
            load_bone(child)
        
        
    for element in model:
        load_bone(element)

    bpy.ops.object.mode_set(mode='OBJECT')
    obj.rotation_euler[0] = math.radians(90)
    return (obj, definition_order)
    
def parse_multipart(model: ET.Element, context: Context, collection: Collection, name="entity", materials: dict[str, Material] = {}) -> tuple[Object, list[str], dict[Mesh, int]]:
    """Load an armature from a multipart model XML element.

    Args:
        model (ET.Element): Element to load (should be `model` element)
        context (Context): Blender context.
        name (str, optional): Name of the armature. Defaults to "entity".

    Returns:
        tuple[Armature, list[str], list[Mesh]]: The generated armature object,
        the bone names in definition order, and mapping of parsed meshes and the
        bone indices they belong to.
    """

    armature = bpy.data.armatures.new(name)
    obj = bpy.data.objects.new(name, armature)

    collection.objects.link(obj)
    context.view_layer.objects.active = obj

    definition_order: list[str] = []
    meshes: dict[Mesh, int] = {}

    bpy.ops.object.mode_set(mode = 'EDIT')
    edit_bones = armature.edit_bones
    id = 0

    def load_bone(element: ET.Element):
        if element.tag != 'part': return
        nonlocal id

        attrib = element.attrib

        if 'name' in attrib.keys():
            name = attrib['name']
        else:
            name = f'bone{id}'
        
        length = 0.16

        bone = edit_bones.new(name)
        bone.head = [0, 0, 0]
        bone.tail = [0, length, 0]

        definition_order.append(name)

        mesh_tag = element.find('mesh')
        if (mesh_tag is not None):
            n_meshes, mats, vertex_groups = _simple_load_obj(context, mesh_tag.text, materials)
            for mesh in n_meshes:
                meshes[mesh] = id
        else:
            print(f'Model part {name} is missing a mesh!')

        id += 1
        for child in element:
            load_bone(child)

    
    for element in model:
        load_bone(element)
    
    bpy.ops.object.mode_set(mode='OBJECT')
    obj.rotation_euler[0] = math.radians(90)
    return (obj, definition_order, meshes)

