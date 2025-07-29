import bpy
from dataclasses import dataclass
from bpy.types import Context, Collection
import os
import tempfile
import zipfile
from . import materials
from .replay_types import ReplayImportSettings, ReplayImportContext

from . import world_importer
from .entity import entity_loader


def import_replay(file: str, settings: ReplayImportSettings, bl_context: Context, extract_zip: bool | None = None):
    if extract_zip == None:
        extract_zip = os.path.isfile(file)
    
    if extract_zip:
        temp_dir = tempfile.TemporaryDirectory()
        replay_root = temp_dir.name
        
        with zipfile.ZipFile(file, 'r') as zip:
            zip.extractall(replay_root)
            print(f'Extracted replay file to {replay_root}')
    else:
        temp_dir = None
        replay_root = file
    
    existing_tex = set(bpy.data.images)
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
    new_tex = set(bpy.data.images) - existing_tex
    
    if settings.process_materials:
        materials.process_materials(new_mats)
        
    if temp_dir:
        
        for tex in new_tex:
            tex.pack()
        
        temp_dir.cleanup()
