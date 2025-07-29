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
    
    existing_meshes = set(bpy.data.meshes)
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
    
    new_meshes = set(bpy.data.meshes) - existing_meshes
    new_mats = set(bpy.data.materials) - existing_mats
    new_tex = set(bpy.data.images) - existing_tex
    
    materials.merge_duplicate_materials(new_meshes)
    
    if settings.process_materials:
        materials.process_materials(new_mats)
        
    # Cleanup excess datablocks
    orphaned_meshes = [m for m in new_meshes if m.users == 0]
    bpy.data.batch_remove(orphaned_meshes)
    
    orphaned_mats = [m for m in new_mats if m.users == 0]
    bpy.data.batch_remove(orphaned_mats)
    
    orphaned_tex = [t for t in new_tex if t.users == 0]
    bpy.data.batch_remove(orphaned_tex)
    
    if temp_dir:
        
        for tex in new_tex:
            if tex not in orphaned_tex:
                tex.pack()
        
        temp_dir.cleanup()
        
