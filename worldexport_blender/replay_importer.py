import bpy
from dataclasses import dataclass
from bpy.types import Context, Collection
from .replay_types import ReplayImportSettings, ReplayImportContext
import os
from . import materials

from . import world_importer
from .entity import entity_loader


def import_replay(replay_root: str, settings: ReplayImportSettings, bl_context: Context):
    existing_mats = set(bpy.data.materials)
    
    scene = bl_context.scene
    if not scene: return
    
    world_collection = bpy.data.collections.new("World")
    scene.collection.children.link(world_collection)
    
    entity_collection = bpy.data.collections.new("Entities")
    scene.collection.children.link(entity_collection)
    
    context = ReplayImportContext(replay_root, bl_context, world_collection, entity_collection)
    
    world_importer.import_world(replay_root)
    entity_loader.import_entities(context)
    
    new_mats = set(bpy.data.materials) - existing_mats
    
    if settings.process_materials:
        materials.process_materials(new_mats)
    
    