import json
from typing import Collection

import bpy
from bpy.types import Context, Scene
import os
import tempfile
import zipfile
from .replay_types import ReplayImportSettings, ReplayImportContext

from . import world_importer
from . import prefabs
from . import material_merge
from . import materials

from .entity import entity_loader

REPLAY_FORMAT_VERSION = (2, 1)
"""The replay format version this addon-implements (see doc/ReplayFormat.md)
"""

def read_replay_version(file: str, extract_zip: bool | None = None) -> tuple[int, int] | None:
    """Extract the major and minor version from a replay zip file

    Args:
        file (str): The file to read
        extract_zip (bool | None, optional): If `true`, attempt to extract it as a zip file.
        If `None`, detect automatically.

    Returns:
        tuple[int, int] | None: The major and minor versions. 
        `None` if it couldn't be loaded for whatever reason.
    """
    if extract_zip == None:
        extract_zip = os.path.isfile(file)
    
    try:
        if extract_zip:
            with zipfile.ZipFile(file, 'r') as zip:
                raw = zip.read('meta.json')
        else:
            with open(os.path.join(file, 'meta.json'), 'r') as f:
                raw = f.read()
        
        version = str(json.loads(raw)['version'])
        major, minor = version.split('.')

        return (int(major), int(minor))
    except Exception:
        return None

def import_replay(file: str, settings: ReplayImportSettings, bl_context: Context, extract_zip: bool | None = None):
    if extract_zip is None:
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
    
    scene: Scene = bl_context.scene
    if not scene: return
    
    world_collection = bpy.data.collections.new("World")
    if not world_collection:
        print("Unable to create world collection")
        return
    scene.collection.children.link(world_collection)
    
    entity_collection = bpy.data.collections.new("Entities")
    if not entity_collection:
        print("Unable to create entity collection")
        return
    scene.collection.children.link(entity_collection)
    
    prefab_datablocks = prefabs.load(prefabs.prefab_file)

    context = ReplayImportContext(replay_root, bl_context, world_collection, entity_collection, prefab_datablocks, settings)
    
    if context.settings.import_world:
        world_importer.import_world(context)

    if context.settings.import_entities:
        entity_loader.import_entities(context)
    
    new_meshes = set(bpy.data.meshes) - existing_meshes
    new_mats = set(bpy.data.materials) - existing_mats
    new_tex = set(bpy.data.images) - existing_tex
    
    material_merge.merge_duplicate_materials(new_meshes)
    
    # Cleanup excess datablocks
    orphaned_meshes = [m for m in new_meshes if m.users == 0]
    bpy.data.batch_remove(orphaned_meshes)
    
    orphaned_mats = [m for m in new_mats if m.users == 0]
    bpy.data.batch_remove(orphaned_mats)
    
    orphaned_tex = [t for t in new_tex if t.users == 0]
    bpy.data.batch_remove(orphaned_tex)
    
    if settings.process_materials:
        materials.process_materials(filter(lambda m: m not in orphaned_mats, new_mats), context)
    
    
    if temp_dir:
        for tex in new_tex:
            if tex not in orphaned_tex: 
                tex.pack()
        
        temp_dir.cleanup()

    prefab_datablocks.clear_unused()
