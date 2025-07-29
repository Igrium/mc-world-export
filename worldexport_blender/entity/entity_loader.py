import bpy
from bpy.types import Object
from ..replay_types import ReplayImportContext
from .captured_entity import CapturedEntity
import os
import json
from os import path

# def import_entities(context: ReplayImportContext):
#     entity_folder = path.join(context.replay_root, 'entities')
    
#     for file in os.listdir(entity_folder):
#         if not file.endswith('.anim'): continue
#         name = path.splitext(file)[0]
#         entity = CapturedEntity(name)
        
#         entity.load(entity_folder, context)
#         entity.apply_animation(context)

def import_entities(context: ReplayImportContext):
    dir = context.replay_root
    
    json_path = os.path.join(dir, 'entities.json')
    if not os.path.exists(json_path): return
    
    with open(json_path, 'r') as file:
        entity_json: dict[str, dict[str, str]] = json.load(file)
    
    i = 1
    num_ents = len(entity_json)
    for name, parents in entity_json.items():
        print(f"Importing entity {i}/{num_ents} ({name})")
        entity = CapturedEntity(name)
        entity.parents = parents
        
        entity.load(dir, context)
        entity.apply_animation(context)
        
        i += 1