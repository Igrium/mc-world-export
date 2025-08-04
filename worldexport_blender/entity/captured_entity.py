import bpy
import struct
import json
import os.path
from ..mesh import import_obj
from .. import common
from ..anim.animation_curve import AnimationCurve
from ..replay_types import ReplayImportSettings, ReplayImportContext
from typing import BinaryIO
from bpy.types import Object, EditBone, ArmatureModifier

ROOT_NAME = 'transform'

class CapturedEntity:
    """An in-memory representation of a replay entity before it's applied to Blender objects
    """
    name: str
    
    parents: dict[str, str]
    """A map of model part names and their parent (optional)
    """
    
    # curves: list[tuple[str, AnimationCurve]]
    curves: dict[str, list[AnimationCurve]]
    
    armature: Object | None
    
    mesh: Object | None
    
    part_meshes: dict[str, Object] = {}
    """A collection of all the mesh objects created as a result of model parts needing to be split.
    """
    
    def __init__(self, name: str) -> None:
        self.name = name
        self.parents = {}
        self.curves = {}
        self.armature = None
        self.mesh = None
        
    def load_anim_file(self, entity_folder: str, context: ReplayImportContext):
        path = os.path.join(entity_folder, self.name + '.anim')
        with open(path, 'rb') as f:
            read_anim_file(f, self.curves)
            
    # def load_relations_file(self, entity_folder: str, context: ReplayImportContext):
    #     path = os.path.join(entity_folder, self.name + '.json')
    #     if not os.path.exists(path):
    #         return
        
    #     with open(path, 'r') as f:
    #         self.parents = json.load(f)
            
    def load_mesh(self, entity_folder: str, context: ReplayImportContext):
        path = os.path.join(entity_folder, self.name + '.obj')
        if not os.path.exists(path):
            return
        
        imported = set(common.load_obj_python(context.bl_context, path, import_vertex_groups=True).values())
        if (imported):
            self.mesh = imported.pop()
            if self.armature:
                self.mesh.parent = self.armature
                
                if self.armature.type == 'ARMATURE':
                    mod = self.mesh.modifiers.new('Armature', 'ARMATURE')
                    mod.object = self.armature # type: ignore
                    
                
                
    
    def gen_armature(self, context: ReplayImportContext):
        # TODO: Actually implement armature shit
        
        if (len(self.curves) == 1):
            empty_obj = bpy.data.objects.new(self.name, None)
            empty_obj.empty_display_size = 1
            
            context.entity_collection.objects.link(empty_obj)
            self.armature = empty_obj
            
            # if (self.mesh):
            #     self.mesh.parent = empty_obj
            return
        
        armature = bpy.data.armatures.new(self.name)
        obj = bpy.data.objects.new(self.name, armature)
        
        context.entity_collection.objects.link(obj)
        
        context.bl_context.view_layer.objects.active = obj # pyright: ignore[reportOptionalMemberAccess]
        
        bpy.ops.object.mode_set(mode='OBJECT', toggle=False)
        bpy.ops.object.mode_set(mode='EDIT', toggle=False)
        
        edit_bones = armature.edit_bones
        bl_bones: dict[str, EditBone] = {}
        
        for name in self.curves.keys():
            if name == ROOT_NAME: continue
            
            bone = edit_bones.new(name)
            bone.head = [0, 0, 0]
            bone.tail = [0, 0, 0.16]
            
            bl_bones[name] = bone
        
        for name, bone in edit_bones.items():
            parent_name = self.parents.get(name)
            if not parent_name: continue # Yeah I could use :=, but this is more readable
            
            parent = bl_bones.get(parent_name)
            if not parent: continue
            
            bone.parent = parent
        
        bpy.ops.object.mode_set(mode='OBJECT', toggle=False)
        
        self.armature = obj
        # if (self.mesh):
        #         self.mesh.parent = obj
        
    
    def load(self, entity_folder: str, context: ReplayImportContext):
        self.load_anim_file(entity_folder, context)
        # self.load_relations_file(entity_folder, context)
        self.gen_armature(context)
        self.load_mesh(entity_folder, context)

    
    def apply_animation(self, context: ReplayImportContext):
        if (self.armature == None):
            raise Exception("Armature has not been generated yet!")
        
        anim_data = self.armature.animation_data_create()
        action = bpy.data.actions.new(name=f'{self.name}_action')
        anim_data.action = action
        
        flattened_curves: dict[tuple[str, int], list[float]] = {}
        
        # Assemble keyframes from all replay curves into one array.
        for name, curve_list in self.curves.items():
            if name == ROOT_NAME:
                data_prefix = ''
                transform_operator = common.convert_coords
            else:
                data_prefix = f'pose.bones["{name}"].'
                transform_operator = None
                
            for curve in curve_list:
                for ref, vals in curve.to_key_arrays(data_prefix, context, transform_operator).items():
                    existing = flattened_curves.get(ref)
                    if existing:
                        existing.extend(vals)
                    else:
                        flattened_curves[ref] = vals
                
                curve_start = curve.tick_offset
                curve_end = curve.tick_offset + curve.length
                
                # TODO: Don't create visibility keyframes 
            

        # Apply anim curves
        for (data_path, index), keys in flattened_curves.items():
            # curve = action.fcurves.find(data_path, index=index)
            # if not curve:
            curve = action.fcurves.new(data_path, index=index)
            
            keyframe_points = curve.keyframe_points
            keyframe_points.add(len(keys) // 2)
            keyframe_points.foreach_set('co', keys)
            keyframe_points.foreach_set('interpolation', [1] * (len(keys) // 2))
            
        
def read_anim_file(f: BinaryIO, curves: dict[str, list[AnimationCurve]]):
    size: int = struct.unpack('>i', f.read(4))[0]
    for i in range(0, size): 
        name = common.read_utf(f)
        curve = AnimationCurve()
        curve.read(f)
        
        if name in curves:
            curve_list = curves[name]
        else:
            curve_list = []
            curves[name] = curve_list
        
        curve_list.append(curve)
    