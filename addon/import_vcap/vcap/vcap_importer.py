import json
import math
import os
import time
from typing import IO, Any, Callable, Union
from zipfile import ZipFile
from .anim import TesselatedFrame

from numpy import ndarray

import bmesh
import bpy
from bmesh.types import BMesh
from bpy.types import Collection, Context, Image, Material, Mesh, Object, Struct
from bpy_extras.wm_utils.progress_report import ProgressReport
from .context import VCAPContext, VCAPSettings
from mathutils import Matrix, Vector

from .. import amulet_nbt
from ..amulet_nbt import TAG_Byte_Array, TAG_Compound, TAG_List, TAG_String, TAG_Int_Array
from . import util, materials, import_mesh
from .world import VcapFrame, load_frame

def load(file: Union[str, IO[bytes]], collection: Collection, context: Context, name='world', settings: VCAPSettings=VCAPSettings()):
    """Import a vcap file.

    Args:
        filename (str | IO[bytes]): File to import from.
        collection (Collection): Collection to add to.
        context (bpy.context): Blender context.
    """
    # Init
    wm = context.window_manager
    wm.progress_begin(0, 5)
    
    with ZipFile(file, 'r') as archive:
        for obj in context.view_layer.objects.selected:
            obj.select_set(False)

        vcontext = VCAPContext(archive, collection, context, name)
        wm.progress_update(1)

        # Materials
        for entry in archive.filelist:
            if entry.filename.startswith('mat/'):
                mat_id = os.path.splitext(os.path.basename(entry.filename))[0]
                print("Reading material: "+mat_id)

                f = archive.open(entry)
                obj = json.load(f)
                mat = materials.parse(obj, mat_id, vcontext)
                vcontext.materials[mat_id] = mat
                f.close()
        print(vcontext.materials)
        # Meshes
        loadMeshes(archive, vcontext)
        wm.progress_update(2)

        # Blocks
        world_dat = archive.open('world.dat')
        readWorld(world_dat, vcontext, settings, lambda progress: wm.progress_update(progress + 2))
        world_dat.close()
        wm.progress_update(3)

        # Object
        if (settings.merge_verts):
            bmesh.ops.remove_doubles(vcontext.target, verts=vcontext.target.verts, dist=.0001)
        outMesh = bpy.data.meshes.new(vcontext.name)
        vcontext.target.to_mesh(outMesh)

        obj = bpy.data.objects.new(vcontext.name, outMesh)
        collection.objects.link(obj)
        obj.rotation_euler = (math.radians(90), 0, 0)

        for mat in vcontext.materials.values():
            print("Appending material ", mat)
            obj.data.materials.append(mat)

        wm.progress_update(4)

        # Clean up
        for mesh in vcontext.models.values():
            context.blend_data.meshes.remove(mesh)

    wm.progress_end()

def loadMeshes(archive: ZipFile, context: VCAPContext):
    for file in archive.filelist:
        if file.filename.startswith('mesh/'):
            model_id = os.path.splitext(os.path.basename(file.filename))[0]

            loaded_file = context.archive.open(file)
            context.models[model_id] = import_mesh.load(context, model_id, loaded_file)
            loaded_file.close()

def readWorld(world_dat: IO[bytes], vcontext: VCAPContext, settings: VCAPSettings, progress_function: Callable[[float], None] = None):
    nbt: amulet_nbt.NBTFile = amulet_nbt.load(world_dat.read(), compressed=False)
    print("Loading world...")
    nbt_frames: TAG_List = nbt.get('frames')
    frames: list[VcapFrame] = []
    for i in range(0, len(nbt_frames)):
        frames.append(load_frame(nbt_frames[i], i))

    overrides: dict[Any, set[Vector]] = dict()
    blame: dict[Any, TesselatedFrame] = dict()
    loaded_frames: list[TesselatedFrame] = []

    for i in reversed(range(0, len(frames))): # Go backward because overrides affect past frames.
        def pfunction(progress: float):
            if (progress_function): progress_function((i + progress) / len(frames))

        frame = frames[i]
        for id in overrides:
            frame.overrides[id] = overrides[id]
        meshes = frame.get_meshes(
            vcontext,
            settings,
            progress_function = pfunction)
        
        final_frame = TesselatedFrame()
        print(f"Wrote frame ${i} with {len(overrides)} overrides.")

        for id in meshes:
            obj = bpy.data.objects.new(meshes[id].name, meshes[id])
            final_frame.objects[id] = obj
            vcontext.collection.objects.link(obj)
            obj.rotation_euler = (math.radians(90), 0, 0)

            for mat in vcontext.materials.values():
                obj.data.materials.append(mat)

        final_frame.time = frame.time
        override_id = f'frame{i}'
        overrides[override_id] = frame.get_declared_override()
        blame[override_id] = final_frame

        loaded_frames.append(final_frame)

    loaded_frames.reverse()

    def add_keyframe(obj: Object, value: bool, frame: float):
        obj.hide_viewport = not value
        obj.hide_render = not value
        obj.keyframe_insert('hide_viewport', frame=frame)
        obj.keyframe_insert('hide_render', frame=frame)

    def seconds_to_frames(seconds: float):
        render = vcontext.context.scene.render
        return seconds * (render.fps / render.fps_base)

    # KEYFRAMES
    for frame in loaded_frames:
        for id in frame.objects:
            obj = frame.objects[id]

            if frame.time != 0:
                add_keyframe(obj, False, 0)
            add_keyframe(obj, True, seconds_to_frames(frame.time))
            if (id in blame):
                add_keyframe(obj, False, seconds_to_frames(blame[id].time))

            for kf in obj.animation_data.action.fcurves[0].keyframe_points:
                kf.interpolation = 'CONSTANT'




    # if progressFunction:
    #     progressFunction(0)

    # frame = world.get_frame(0)
    # sections: TAG_List = frame['sections']
    # for i in range(0, len(sections)):
    #     # print(f'Parsing section {i + 1} / {len(sections)}')
    #     readIntracodedSection(sections[i], vcontext, settings)
    #     if progressFunction:
    #         progressFunction((i + 1) / len(sections))

def _read_unsigned(array: ndarray, index: int, bit_depth: int=8):
    item = array.item(index)
    if (item < 0):
        return item + 2**bit_depth
    else:
        return item

def place(model_id: str, pos: tuple[float, float, float], vcontext: VCAPContext, color: list[float]=[1, 1, 1, 1]):
    mesh = vcontext.models[model_id]
    if (len(mesh.vertices) == 0): return

    util.add_mesh(vcontext.target, mesh, Matrix.Translation(pos), color)
