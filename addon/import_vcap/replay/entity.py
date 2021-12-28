from io import BytesIO
import math
from typing import IO
from bmesh import new
from bpy.types import Armature, Context, Object
from mathutils import Euler, Matrix, Quaternion, Vector

from ..vcap.import_obj import load as load_obj
import xml.etree.ElementTree as ET
import bpy

def load_entity(file: IO[str], context: Context):
    """Load a replay entity into Blender

    Args:
        file (IO[str]): Raw XML file
        context (Context): Blender context

    Raises:
        Exception: If the XML is malformatted
    """
    
    def convert_vector(input=(0, 0, 0)):
       return Vector((input[0], -input[2], input[1]))
    
    tree = ET.parse(file)
    entity = tree.getroot()
    
    model = entity.find('model')
    if model is None:
        raise Exception("Entity XML is missing model tag.")
    
    mesh = model.find('mesh')

    if mesh is not None:
        obj = BytesIO(bytes(mesh.text, 'utf-8'))
        meshes, mats, vertex_groups = load_obj(context, obj, use_split_objects=False, use_split_groups=False, use_groups_as_vgroups=True)
        
        for mesh in meshes:
            new_object = bpy.data.objects.new(mesh.name, mesh)
            context.scene.collection.objects.link(new_object)
            new_object.rotation_euler[0] = math.radians(90)
            
            for group_name, group_indices in vertex_groups.items():
                group = new_object.vertex_groups.new(name=group_name.decode('utf-8', "replace"))
                group.add(group_indices, 1.0, 'REPLACE')
    else:
        print("Warning: no mesh found in entity XML.")

    armature_obj, bone_def = parse_armature(model, context)
    
    # ANIMATION
    anim = entity.find('anim')
    if (anim is not None):
        armature_obj.rotation_mode = 'QUATERNION'
        render = context.scene.render
        scene_framerate = render.fps / render.fps_base
        
        if 'fps' in anim.attrib:
            framerate = float(anim.attrib['fps'])
        else:
            framerate = scene_framerate
        
        for index, frame in enumerate(anim.text.splitlines()):
            frame = frame.strip()
            
            scene_frame = index / framerate * scene_framerate
            
            bones = frame.split(';')
            
            # Root transform
            root_str = bones[0].strip()
            if len(root_str) > 0:
                root_vals = [float(i) for i in root_str.split(' ')]
                
                if len(root_vals) >= 4:
                    rotation = Quaternion(root_vals[0:4])
                    rotation.rotate(Euler((math.radians(90), 0, 0)))
                    armature_obj.rotation_quaternion = rotation
                    armature_obj.keyframe_insert('rotation_quaternion', frame=scene_frame)
                
                if len(root_vals) >= 7:
                    armature_obj.location = convert_vector(root_vals[4:7])
                    armature_obj.keyframe_insert('location', frame=scene_frame)
                
                if len(root_vals) >= 10:
                    armature_obj.scale = convert_vector(root_vals[7:10])
                    armature_obj.keyframe_insert('scale', frame=scene_frame )
            
            for def_index, bone_str in enumerate(bones[1:]):
                bone_str = bone_str.strip()
                if (len(bone_str) == 0): continue
                
                bone_vals = [float(i) for i in bone_str.split(' ')]
                if len(bone_vals) == 0: continue
                
                # Get the pose bone based on the definition order.
                bone = armature_obj.pose.bones[bone_def[def_index]]
                
                if len(bone_vals) >= 4:
                    bone.rotation_quaternion = bone_vals[0:4]
                    bone.keyframe_insert('rotation_quaternion', frame=scene_frame)
                
                if len(bone_vals) >= 7:
                    bone.location = bone_vals[4:7]
                    bone.keyframe_insert('location', frame=scene_frame)
                
                if len(bone_vals) >= 10:
                    bone.scale = bone_vals[7:10]
                    bone.keyframe_insert('scale', frame=scene_frame)
                
                
            


def parse_armature(model: ET.Element, context: Context, name="entity") -> tuple[Object, list[str]]:
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

    context.scene.collection.objects.link(obj)
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
    