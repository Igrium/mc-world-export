import bpy
from bpy.types import Object
from ..replay_types import ReplayImportContext
from .captured_entity import CapturedEntity
import os
from os import path

def import_entities(context: ReplayImportContext):
    entity_folder = path.join(context.replay_root, 'entities')
    
    for file in os.listdir(entity_folder):
        if not file.endswith('.anim'): continue
        name = path.splitext(file)[0]
        entity = CapturedEntity(name)
        
        entity.load(entity_folder, context)
        entity.apply_animation(context)