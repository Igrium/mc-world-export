import math
import os
from typing import IO, Callable
from zipfile import ZipFile

from numpy import ndarray

import bmesh
import bpy
from bmesh.types import BMesh
from bpy.types import Collection, Context, Image, Material, Mesh, Object
from bpy_extras.wm_utils.progress_report import ProgressReport
from .context import VCAPContext, VCAPSettings
from mathutils import Matrix, Vector

from .. import amulet_nbt
from ..amulet_nbt import TAG_Byte_Array, TAG_Compound, TAG_List, TAG_String, TAG_Int_Array
from . import util, materials
from .world import VCAPWorld

def load(file: str, collection: Collection, context: Context, settings: VCAPSettings=VCAPSettings()):
    """Import a vcap file.

    Args:
        filename (str): File to import from.
        collection (Collection): Collection to add to.
        context (bpy.context): Blender context.
    """
    # Init
    wm = context.window_manager
    wm.progress_begin(0, 5)

    archive = ZipFile(file, 'r')
    for obj in context.view_layer.objects.selected:
        obj.select_set(False)
    
    vcontext = VCAPContext(archive, collection, context, os.path.basename(file))
    wm.progress_update(1)

    # Materials
    for entry in archive.filelist:
        if entry.filename.startswith('mat/'):
            mat_id = os.path.splitext(os.path.basename(entry.filename))[0]
            print("Reading material: "+mat_id)
            
            f = archive.open(entry)
            mat = materials.read(f, mat_id, vcontext)
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
            context.get_mesh(model_id)

def readWorld(world_dat: IO[bytes], vcontext: VCAPContext, settings: VCAPSettings, progressFunction: Callable[[float], None] = None):
    nbt: amulet_nbt.NBTFile = amulet_nbt.load(world_dat.read(), compressed=False)
    world = VCAPWorld(nbt.value)
    print("Loading world...")

    if progressFunction:
        progressFunction(0)

    frame = world.get_frame(0)
    sections: TAG_List = frame['sections']
    for i in range(0, len(sections)):
        # print(f'Parsing section {i + 1} / {len(sections)}')
        readSection(sections[i], vcontext, settings)
        if progressFunction:
            progressFunction((i + 1) / len(sections))

def readSection(section: TAG_Compound, vcontext: VCAPContext, settings: VCAPSettings):
    palette: TAG_List = section['palette']
    offset: tuple[int, int, int] = (section['x'].value, section['y'].value, section['z'].value)
    blocks: TAG_Int_Array = section['blocks']
    bblocks = blocks.value
    
    use_colors = False
    if settings.use_vertex_colors and ('colors' in section) and ('colorPalette' in section):
        color_palette_tag: TAG_Byte_Array = section['colorPalette']
        color_palette = color_palette_tag.value
        colors_tag: TAG_Byte_Array = section['colors']
        colors = colors_tag.value
        use_colors = True

    for y in range(0, 16):
        for z in range(0, 16):
            for x in range(0, 16):
                index = bblocks.item((y * 16 + z) * 16 + x)
                model_id: TAG_String = palette[index]

                if use_colors:
                    i = colors.item((y * 16 + z) * 16 + x)
                    r = _read_unsigned(color_palette, i, 8) / 255
                    g = _read_unsigned(color_palette, i + 1, 8) / 255
                    b = _read_unsigned(color_palette, i + 2, 8) / 255
                    color = [r, g, b, 1]
                else:
                    color = [1, 1, 1, 1]
                    
                place(model_id.value, pos=(offset[0] * 16 + x, offset[1] * 16 + y, offset[2] * 16 + z), vcontext=vcontext, color=color)

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
