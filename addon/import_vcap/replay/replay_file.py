import io
import os
import sys
import time
from typing import IO, Union
from zipfile import ZipFile
import zipfile

import numpy as np
import bpy

from bpy.types import Collection, Context
from . import entity
from ..vcap.context import VCAPSettings
from ..vcap import vcap_importer

do_profiling = False

class ReplaySettings:
    __slots__ = (
        'world',
        'entities',
        'vcap_settings'
    )

    world: bool
    entities: bool
    vcap_settings: VCAPSettings

    def __init__(self, world=True, entities=True, vcap_settings=VCAPSettings(merge_verts=False)) -> None:
        self.world = world
        self.entities = entities
        self.vcap_settings = vcap_settings


def load_replay(file: Union[str, IO[bytes]],
                context: Context,
                collection: Collection,
                settings: ReplaySettings = ReplaySettings()):
    if do_profiling:
        import cProfile
        import pstats
        pr = cProfile.Profile()
        pr.enable()

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
                        entity.load_entity(e, context, ent_collection)
            
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