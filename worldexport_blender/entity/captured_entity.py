import bpy
import struct
import json
import os.path
from .. import common
from ..anim.animation_curve import AnimationCurve
from ..replay_types import ReplayImportSettings, ReplayImportContext
from typing import BinaryIO, TextIO
from bpy.types import Object, Context

class CapturedEntity:
    """An in-memory representation of a replay entity before it's applied to Blender objects
    """
    name: str
    
    parents: dict[str, str] = {}
    """A map of model part names and their parent (optional)
    """
    
    curves: list[tuple[str, AnimationCurve]] = []
    
    armature: Object | None = None
    
    mesh: Object | None = None
    
    def __init__(self, name: str) -> None:
        self.name = name
        
    def load_anim_file(self, entity_folder: str, context: ReplayImportContext):
        path = os.path.join(entity_folder, self.name + '.anim')
        with open(path, 'rb') as f:
            read_anim_file(f, self.curves)
            
    def load_relations_file(self, entity_folder: str, context: ReplayImportContext):
        path = os.path.join(entity_folder, self.name + '.json')
        if not os.path.exists(path):
            return
        
        with open(path, 'r') as f:
            parents = json.load(f)
            
    def load_mesh(self, entity_folder: str, context: ReplayImportContext):
        path = os.path.join(entity_folder, self.name + '.json')
        if not os.path.exists(path):
            return
        
        imported = common.import_obj(path)
        if (imported):
            self.mesh = imported.pop()
    
    def gen_armature(self, context: ReplayImportContext):
        # TODO: Actually implement armature shit
        empty_obj = bpy.data.objects.new(self.name, None)
        empty_obj.empty_display_size = 1
        
        context.entity_collection.objects.link(empty_obj)
        self.armature = empty_obj
        
        if (self.mesh != None):
            self.mesh.parent = empty_obj
    
    def load(self, entity_folder: str, context: ReplayImportContext):
        self.load_anim_file(entity_folder, context)
        self.load_relations_file(entity_folder, context)
        self.load_mesh(entity_folder, context)
        self.gen_armature(context)
    
    def apply_animation(self, context: ReplayImportContext):
        if (self.armature == None):
            raise Exception("Armature has not been generated yet!")
        
        anim_data = self.armature.animation_data_create()
        action = bpy.data.actions.new(name=f'{self.name}_action')
        anim_data.action = action
        
        for (name, curve) in self.curves:
            if name == 'root':
                data_prefix = ''
            else:
                data_prefix = f'pose.bones["{name}"].'
            
            curve.apply(action, data_prefix, context)
        
        
def read_anim_file(f: BinaryIO, curves: list[tuple[str, AnimationCurve]]):
    size: int = struct.unpack('>i', f.read(4))[0]
    for i in range(0, size): 
        name = common.read_utf(f)
        curve = AnimationCurve()
        curve.read(f)
        curves.append((name, curve))
    