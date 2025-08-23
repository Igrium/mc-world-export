import bpy
import struct
import os.path
from .. import common
from ..anim.animation_curve import AnimationCurve
from ..anim import timeline_bounds
from ..anim.timeline_bounds import TimelineRange
from ..mesh import mesh_utils

from ..replay_types import ReplayImportContext, CurveLike, AnimationProvider
from typing import BinaryIO, Iterable
from bpy.types import Object, EditBone

ROOT_NAME = 'transform'

class CapturedEntity(AnimationProvider):
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
    """The base object mesh. The only mesh if there's no part visibility toggles.
    """
    
    part_meshes: dict[str, Object]
    """A collection of all the mesh objects created as a result of model parts needing to be split.
    """
    
    entity_ranges: list[TimelineRange]
    """The timeline ranges where the entity is visible. Certain parts may be hidden within these ranges.
    """
    
    def __init__(self, name: str) -> None:
        self.name = name
        self.parents = {}
        self.curves = {}
        self.armature = None
        self.mesh = None
        self.part_meshes = {}
        self.entity_ranges = []
        
    def load_anim_file(self, entity_folder: str, context: ReplayImportContext):
        path = os.path.join(entity_folder, self.name + '.anim')
        with open(path, 'rb') as f:
            read_anim_file(f, self.curves)
        
        self.entity_ranges = timeline_bounds.merge_ranges([TimelineRange.from_curve(r) for c in self.curves.values() for r in c])
    
    def get_curves(self):
        return self.curves
    
    def get_split_parts(self):
        """Based on the entity's animation curves, 
        determine which model parts need to be split due to having separate visibility toggles.

        Returns:
            set[str]: All split part names
        """
        split_parts: set[str] = set()
        
        for pname, pcurves in self.curves.items():
            ranges = timeline_bounds.merge_ranges(TimelineRange.from_curve(c) for c in pcurves)
            
            if ranges_need_split(ranges, self.entity_ranges):
                split_parts.add(pname)
        
        return split_parts
    
    
    # def load_relations_file(self, entity_folder: str, context: ReplayImportContext):
    #     path = os.path.join(entity_folder, self.name + '.json')
    #     if not os.path.exists(path):
    #         return
        
    #     with open(path, 'r') as f:
    #         self.parents = json.load(f)
            
    def load_mesh(self, entity_folder: str, context: ReplayImportContext, split_parts: set[str] | None = None):
        path = os.path.join(entity_folder, self.name + '.obj')
        if not os.path.exists(path):
            return
        
        context.bl_context.collection
        imported = set(common.load_obj_python(context.bl_context, path, import_vertex_groups=True).values())
        if (imported):
            self.mesh = imported.pop()
            # split parts
            split_parts = self.get_split_parts()
            if (split_parts):
                print(split_parts)
                self.part_meshes = mesh_utils.split_vertex_groups(self.mesh, lambda p: p in split_parts)
                print(self.part_meshes)
            
            for m in self.part_meshes.values():
                context.entity_collection.objects.link(m)
            
            if self.mesh and len(self.mesh.data.vertices) > 0: # type: ignore
                context.entity_collection.objects.link(self.mesh)

            
            if self.armature:
                for m in [self.mesh] + list(self.part_meshes.values()):
                    m.parent = self.armature
                    
                    if self.armature.type == 'ARMATURE':
                        mod = m.modifiers.new('Armature', 'ARMATURE')
                        mod.object = self.armature # type: ignore
                                
    
    def gen_armature(self, context: ReplayImportContext):
        
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
        for part_name, curve_list in self.curves.items():
            if part_name == ROOT_NAME:
                data_prefix = ''
                transform_operator = common.convert_coords
            else:
                data_prefix = f'pose.bones["{part_name}"].'
                transform_operator = None
            
            
            for curve in curve_list:
                for ref, vals in curve.to_key_arrays(data_prefix, context, transform_operator).items():
                    existing = flattened_curves.get(ref)
                    if existing:
                        existing.extend(vals)
                    else:
                        flattened_curves[ref] = vals
                        
                # TODO: Don't create visibility keyframes 
            
            # Part visibility
            part_mesh = self.part_meshes.get(part_name)
            if part_mesh:
                ranges = timeline_bounds.merge_ranges((TimelineRange.from_curve(c) for c in curve_list))
                first_range = True
                for r in ranges:
                    start_frame = context.tick_to_frame(r.start_tick)
                    end_frame = start_frame + context.tick_to_frame(r.length)
                    
                    if first_range:
                        common.add_vis_keyframe(part_mesh, False, start_frame - 1)
                        first_range = False
                    common.add_vis_keyframe(part_mesh, True, start_frame)
                    common.add_vis_keyframe(part_mesh, False, end_frame)
                    
        # Apply anim curves
        for (data_path, index), keys in flattened_curves.items():
            # curve = action.fcurves.find(data_path, index=index)
            # if not curve:
            curve = action.fcurves.new(data_path, index=index)
            
            keyframe_points = curve.keyframe_points
            keyframe_points.add(len(keys) // 2)
            keyframe_points.foreach_set('co', keys)
            keyframe_points.foreach_set('interpolation', [1] * (len(keys) // 2))
        
        # Entity visibility keyframes
        
        if self.mesh:
            first_range = True
            for r in self.entity_ranges:
                start_frame = context.tick_to_frame(r.start_tick)
                end_frame = start_frame + context.tick_to_frame(r.length)
                
                if first_range:
                    common.add_vis_keyframe(self.mesh, False, start_frame - 1)
                    first_range = False
                common.add_vis_keyframe(self.mesh, True, start_frame)
                common.add_vis_keyframe(self.mesh, False, end_frame)
        
            
        
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
    
def ranges_need_split(child: Iterable[TimelineRange], parent: Iterable[TimelineRange]) -> bool:
    """Check if a model part needs splitting by comparing its timeline ranges with that of the parent entity.
    If there are any instances of a child timeline range where no parent timeline range fits completely,
    it means the child range is smaller than its encapsulating parent ranges and therefore must be split.

    Args:
        child (Iterable[TimelineRange]): Timeline ranges of the model part.
        parent (Iterable[TimelineRange]): Timeline ranges of the entity.

    Returns:
        bool: If the part needs to be split.
    """
    
    for crange in child:
        for prange in parent:
            if crange.contains(prange):
                return False # We only need one instance where the child spans the entire parent
    
    return True