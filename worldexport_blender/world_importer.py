import os
import bpy

from dataclasses import dataclass
import json
from bpy.types import Object
from . import common

from .replay_types import ReplayImportContext

@dataclass
class WorldMesh:
    name: str
    start_tick: int | None
    end_tick: int | None


def import_world(context: ReplayImportContext):

    json_path = os.path.join(context.replay_root, 'world.json')
    if not os.path.exists(json_path): return
    
    with open(json_path, 'r') as file:
        world_json: dict[str, dict] = json.load(file)

    for name, meta in world_json.items():
        mesh = WorldMesh(name, meta.get('startTick'), meta.get('endTick'))
        _import_world_mesh(mesh, context)
    ...
    
def _import_world_mesh(mesh: WorldMesh, context: ReplayImportContext) -> Object | None:
    obj_path = os.path.join(context.replay_root, mesh.name + '.obj')
    # obj = common.import_obj(obj_path, mtl_name_collision_mode='REFERENCE_EXISTING')
    imported = common.load_obj(obj_path)
    if not imported:
        return None
    obj = imported.pop()
    
    # Add keyframes
    if mesh.start_tick == None and mesh.end_tick == None:
        return obj
    
    if mesh.start_tick != None and mesh.start_tick != 0:
        common.add_vis_keyframe(obj, False, 0)
        common.add_vis_keyframe(obj, True, context.tick_to_frame(mesh.start_tick))
    else:
        common.add_vis_keyframe(obj, True, 0)
    
    if mesh.end_tick != None:
        common.add_vis_keyframe(obj, False, context.tick_to_frame(mesh.end_tick) + 1)
    
    return obj