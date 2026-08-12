import os

from dataclasses import dataclass
import json
from bpy.types import Object
from . import common, obj_loader

from .replay_types import ReplayImportContext

@dataclass
class WorldMesh:
    name: str
    start_tick: int | None
    end_tick: int | None
    offset: tuple[float, float, float] = (0, 0, 0)
    """Mesh origin, in Minecraft coordinate space."""


def import_world(context: ReplayImportContext):

    json_path = os.path.join(context.replay_root, 'world.json')
    if not os.path.exists(json_path): return

    with open(json_path, 'r') as file:
        world_json: dict[str, dict] = json.load(file)

    for name, meta in world_json.items():
        offset = meta.get('offset') or (0, 0, 0)
        mesh = WorldMesh(name, meta.get('startTick'), meta.get('endTick'), tuple(offset))
        _import_world_mesh(mesh, context)

def _import_world_mesh(mesh: WorldMesh, context: ReplayImportContext) -> Object | None:
    obj_path = os.path.join(context.replay_root, mesh.name + '.obj')

    imported = obj_loader.load_obj_native(obj_path)
    if not imported:
        return None
    obj = imported.pop()

    # TODO: link obj into context.world_collection; the import op uses the active collection.

    # Section meshes are exported with section-local vertices; world.json holds their origin.
    obj.location = common.convert_coords(*mesh.offset)

    # Add keyframes
    if mesh.start_tick is None and mesh.end_tick is None:
        return obj

    if mesh.start_tick is not None and mesh.start_tick != 0:
        common.add_vis_keyframe(obj, False, 0)
        common.add_vis_keyframe(obj, True, context.tick_to_frame(mesh.start_tick))
    else:
        common.add_vis_keyframe(obj, True, 0)

    if mesh.end_tick is not None:
        common.add_vis_keyframe(obj, False, context.tick_to_frame(mesh.end_tick + 1))
    
    return obj