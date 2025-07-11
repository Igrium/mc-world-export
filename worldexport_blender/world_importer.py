import os
import bpy

from dataclasses import dataclass
import json
from bpy.types import Object
from . import common

@dataclass
class WorldMesh:
    name: str
    start_tick: int | None
    end_tick: int | None
    

def import_world(world_dir: str):
    for local in os.listdir(world_dir):
        if not local.endswith('.obj'): continue
        
        glob = os.path.join(world_dir, local)
        if not os.path.isfile(glob): continue
        
        name = os.path.splitext(local)[0]
        
        mesh = _parse_world_mesh(name, world_dir)
        _import_world_mesh(mesh, world_dir)
    ...

def _parse_world_mesh(name: str, world_dir: str) -> WorldMesh:
    json_path = os.path.join(world_dir, name + '.json')
    if os.path.exists(json_path):
        with open(json_path, 'r') as file:
            data: dict = json.load(file)
        return WorldMesh(name, data.get('startTick'), data.get('endTick'))
    else:
        return WorldMesh(name, None, None)
    
def _import_world_mesh(mesh: WorldMesh, world_dir: str) -> Object:
    obj_path = os.path.join(world_dir, mesh.name + '.obj')
    # obj = common.import_obj(obj_path, mtl_name_collision_mode='REFERENCE_EXISTING')
    obj = common.import_obj(obj_path)
    
    # Add keyframes
    if mesh.start_tick == None and mesh.end_tick == None:
        return obj
    
    if mesh.start_tick != None and mesh.start_tick != 0:
        common.add_vis_keyframe(obj, False, 0)
        common.add_vis_keyframe(obj, True, mesh.start_tick)
    else:
        common.add_vis_keyframe(obj, True, 0)
    
    if mesh.end_tick != None:
        common.add_vis_keyframe(obj, False, mesh.end_tick + 1)
    
    return obj