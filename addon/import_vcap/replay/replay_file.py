import io
import json
from logging import warn, warning
import os
from re import S
import sys
import time
from typing import IO, Union
from zipfile import ZipFile
import zipfile

import numpy as np
import bpy

from bpy.types import Collection, Context, Image, Material
from ..vcap import util
from . import entity
from ..vcap.context import VCAPSettings
from ..vcap import vcap_importer
from ..vcap import materials as matlib

do_profiling = False

class ReplaySettings:
    __slots__ = (
        'world',
        'entities',
        'separate_parts',
        'vcap_settings',
        'hide_entities'
    )

    world: bool
    entities: bool
    separate_parts: bool
    vcap_settings: VCAPSettings
    hide_entities: bool

    def __init__(self, world=True, entities=True, vcap_settings=VCAPSettings(merge_verts=False), separate_parts=False, hide_entities=True) -> None:
        self.world = world
        self.entities = entities
        self.vcap_settings = vcap_settings
        self.separate_parts = separate_parts
        self.hide_entities = hide_entities


def load_replay(file: Union[str, IO[bytes]],
                context: Context,
                collection: Collection,
                settings: ReplaySettings = ReplaySettings()):
    if do_profiling:
        import cProfile
        import pstats
        pr = cProfile.Profile()
        pr.enable()

    textures: dict[str, Image] = {}
    materials: dict[str, Material] = {}

    context.window_manager.progress_begin(min=0, max=1)
    context.window_manager.progress_update(0)
    start_time = time.time()
    with ZipFile(file, 'r') as archive:
        # World
        if settings.world:
            def wold_progress_function(progress):
                context.window_manager.progress_update(progress * .5)

            world_collection = bpy.data.collections.new('world')
            collection.children.link(world_collection)
            with archive.open('world.vcap') as world_file:
                vcap_importer.load(world_file,
                                   world_collection,
                                   context,
                                   settings=settings.vcap_settings,
                                   progress_function=wold_progress_function)

        # Materials
        def load_texture(tex_name: str, is_data=False):
            if tex_name in textures:
                return textures[tex_name]

            filename = f'tex/{tex_name}.png'
            if filename not in archive.namelist():
                warning(f'{tex_name} missing from replay archive!')
                return None
            
            with archive.open(filename) as file:
                image = util.import_image(file, os.path.basename(tex_name), is_data=is_data)
                textures[tex_name] = image
                return image

        for entry in archive.filelist:
            n = entry.filename
            if n.startswith('mat/') and n.endswith('.json'):
                defname = os.path.splitext(n[(n.find('/') + 1):])[0] # Remove 'mat/'
                materials[defname] = matlib.parse_raw(
                    json.load(archive.open(entry)),
                    os.path.basename(n), load_texture)


        # Entities
        if settings.entities:
            print("Parsing entities...")
            ent_collection = bpy.data.collections.new('entities')
            collection.children.link(ent_collection)

            ent_folder = zipfile.Path(archive, 'entities/')
            if not ent_folder.is_dir():
                raise RuntimeError("'entities' entry in replay must be a directory!")

            entity_files = [path for path in ent_folder.iterdir() if path.name.endswith('.xml')]
            # entity_files = [file for file in archive.filelist if file.filename.endswith(".xml")]

            for index, entry in enumerate(entity_files):
                context.window_manager.progress_update((.5 * index / len(entity_files)) + .5)
                with entry.open('r') as e:
                    entity.load_entity(e, context, ent_collection, materials, separate_parts=settings.separate_parts, autohide=settings.hide_entities)

        print(f"Imported replay in {time.time() - start_time} seconds.")
        context.window_manager.progress_end()

    if do_profiling:
        pr.disable()
        prof_savedir = os.path.join(os.path.dirname(__file__), "prof")
        prof_modelname = "replay"
        prof_savepath = os.path.join(prof_savedir, f"{prof_modelname}.prof")
        if not os.path.exists(prof_savedir):
            os.mkdir(prof_savedir)

        ps = pstats.Stats(pr, stream=sys.stdout).sort_stats('cumulative')
        print("")
        print("Partial profiling results:")
        print("")
        ps.print_stats(20)

        pr.dump_stats(prof_savepath)
        print("")
        print(f"Saved code profiling data to {prof_savepath}")
        print(f"View with: snakeviz \"{prof_savepath}\"")