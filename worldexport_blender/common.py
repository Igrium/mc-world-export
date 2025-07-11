from contextvars import Context
from typing import Literal
import bpy
from bpy.types import Context, Object

# def import_obj(filepath: str, context: int | str | None = None, global_scale: float | None = 1,
#                 clamp_size: float | None = 0,
#                 forward_axis: Literal['X', 'Y', 'Z', 'NEGATIVE_X', 'NEGATIVE_Y', 'NEGATIVE_Z'] | None = "NEGATIVE_Z",
#                 up_axis: Literal['X', 'Y', 'Z', 'NEGATIVE_X', 'NEGATIVE_Y', 'NEGATIVE_Z'] | None = "Y",
#                 use_split_objects: bool | None = True,
#                 use_split_groups: bool | None = False,
#                 import_vertex_groups: bool | None = False,
#                 validate_meshes: bool | None = True,
#                 close_spline_loops: bool | None = True,
#                 collection_separator: str = "",
#                 mtl_name_collision_mode: Literal['MAKE_UNIQUE', 'REFERENCE_EXISTING'] | None = "MAKE_UNIQUE"
#     ):
    
#     existing = set(bpy.data.objects)
#     bpy.ops.wm.obj_import(context, filepath=filepath, global_scale=global_scale,
#                           clamp_size=clamp_size, forward_axis=forward_axis, up_axis=up_axis,
#                           use_split_objects=use_split_objects, use_split_groups=use_split_groups,
#                           import_vertex_groups=import_vertex_groups, validate_meshes=validate_meshes,
#                           close_spline_loops=close_spline_loops, collection_separator=collection_separator,
#                           mtl_name_collision_mode=mtl_name_collision_mode)
    
#     return (set(bpy.data.objects) - existing).pop()

def import_obj(filepath: str):
    existing = set(bpy.data.objects)
    bpy.ops.wm.obj_import(filepath=filepath)
    return (set(bpy.data.objects) - existing).pop()

def tick_to_frame(tick: int, context: Context) -> float:
    scene = context.scene
    if (scene == None):
        return tick
    
    return (tick * scene.render.fps) / scene.render.fps_base

def add_vis_keyframe(obj: Object, visible: bool, frame: float):
    obj.hide_viewport = not visible
    obj.hide_render = not visible
    obj.keyframe_insert('hide_viewport', frame=frame)
    obj.keyframe_insert('hide_render', frame=frame)
