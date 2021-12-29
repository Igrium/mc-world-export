import io
from typing import IO, Union
from zipfile import ZipFile
import bpy

from bpy.types import Collection, Context
from . import entity
from ..vcap.context import VCAPSettings
from ..vcap import vcap_importer


def load_replay(file: Union[str, IO[bytes]], context: Context, collection: Collection):
    with ZipFile(file, 'r') as archive:
        # World
        world_collection = bpy.data.collections.new('world')
        collection.children.link(world_collection)
        with archive.open('world.vcap') as world_file:
            vcap_importer.load(world_file, world_collection, context, settings=VCAPSettings(merge_verts=False))
        
        # Entities
        ent_collection = bpy.data.collections.new('entities')
        collection.children.link(ent_collection)
        
        for entry in archive.filelist:
            if (entry.filename.endswith('.xml')):
                with io.TextIOWrapper(archive.open(entry), 'utf-8') as e:
                    entity.load_entity(e, context, ent_collection)
        ...