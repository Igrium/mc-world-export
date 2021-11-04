import math
import os
from typing import IO, Callable
from zipfile import ZipFile

import bmesh
import bpy
from bmesh.types import BMesh
from bpy.types import Collection, Context, Material, Mesh, Object
from bpy_extras.wm_utils.progress_report import ProgressReport
from mathutils import Matrix, Vector

from .. import amulet_nbt
from ..amulet_nbt import TAG_Byte_Array, TAG_Compound, TAG_List, TAG_String
from . import import_obj, util, materials
from .world import VCAPWorld


class VCAPContext:
    archive: ZipFile
    collection: Collection
    context: Context
    name: str

    materials: dict[str, Material] = {}
    models: dict[str, Mesh] = {}

    target: BMesh
    
    def __init__(self, archive: ZipFile, collection: Collection, context: Context, name: str) -> None:
        """Create a VCAP context

        Args:
            archive (ZipFile): Loaded VCAP archive.
            collection (Collection): Collection to import into.
            context (Context): Blender context.
        """
        self.archive = archive
        self.context = context
        self.name = name
        self.models = {}
        self.materials = {}        

        self.collection = bpy.data.collections.new('vcap_import')
        collection.children.link(self.collection)

        self.target = bmesh.new()
    
    def get_mesh(self, model_id: str):
        """Ensure we have a mesh installed.

        Args:
            model_id (str): Mesh's ID string

        Returns:
            [Mesh]: The loaded mesh
        """
        if (model_id in self.models):
            return self.models[model_id]
        else:
            return self._import_mesh(model_id)

    def _import_mesh(self, model_id: str):
        file = self.archive.open(f'mesh/{model_id}.obj', 'r')
        (meshes, mats) = import_obj.load(self.context, file, name=model_id, unique_materials=self.materials)
        file.close()
        if (len(meshes) > 1):
            raise RuntimeError("Only one obj object is allowed per model in VCAP.")
        
        self.models[model_id] = meshes[0]
        self.materials = mats # Likely won't do anything
        return meshes[0]

        # tmpname = self.archive.extract(member=f'mesh/{model_id}.obj', path=tempfile.gettempdir())
        # print("Extracted to "+tmpname)
        # objects: list[Object] = import_obj.load(context=self.context, filepath=tmpname)
        # if (len(objects) > 1):
        #     raise RuntimeError("Only one obj object is allowed per model in VCAP.")
        
        # obj = objects[0]
        # mesh: Mesh = obj.data
        # if not isinstance(mesh, Mesh):
        #     raise RuntimeError("Imported object is not a mesh.")

        # self.models[model_id] = mesh
        # bpy.data.objects.remove(obj, do_unlink=True)
        # return mesh


def load(file: str, collection: Collection, context: Context):
    """Import a vcap file.

    Args:
        filename (str): File to import from.
        collection (Collection): Collection to add to.
        context (bpy.context): Blender context.
    """
    # Init
    wm = context.window_manager
    wm.progress_begin(0, 4)

    archive = ZipFile(file, 'r')
    for obj in context.view_layer.objects.selected:
        obj.select_set(False)
    
    vcontext = VCAPContext(archive, collection, context, os.path.basename(file))
    wm.progress_update(1)

    # Meshes
    loadMeshes(archive, vcontext)
    wm.progress_update(2)

    # Blocks
    world_dat = archive.open('world.dat')
    readWorld(world_dat, vcontext, lambda progress: wm.progress_update(progress + 2))
    world_dat.close()
    wm.progress_update(3)

    # Object
    bmesh.ops.remove_doubles(vcontext.target, verts=vcontext.target.verts, dist=.0001)
    outMesh = bpy.data.meshes.new(vcontext.name)
    vcontext.target.to_mesh(outMesh)

    obj = bpy.data.objects.new(vcontext.name, outMesh)
    collection.objects.link(obj)
    obj.rotation_euler = (math.radians(90), 0, 0)
    wm.progress_update(4)

    # Materials
    num_materials = len(vcontext.materials)
    print(vcontext.materials)
    for i in range(0, num_materials):
        mat_id = list(vcontext.materials)[i].decode('ascii')
        filename = f'mat/{mat_id}.json'
        print("Reading material: "+filename)

        f = archive.open(filename)
        mat = materials.read(f, mat_id)
        f.close()

        obj.data.materials.append(mat)
        
        wm.progress_update(4 + (i / num_materials))

    # Clean up
    for mesh in vcontext.models.values():
        context.blend_data.meshes.remove(mesh)

    wm.progress_end()

def loadMeshes(archive: ZipFile, context: VCAPContext):
    for file in archive.filelist:
        if file.filename.startswith('mesh/'):
            model_id = os.path.splitext(os.path.basename(file.filename))[0]
            context.get_mesh(model_id)

def readWorld(world_dat: IO[bytes], vcontext: VCAPContext, progressFunction: Callable[[float], None] = None):
    nbt: amulet_nbt.NBTFile = amulet_nbt.load(world_dat.read(), compressed=False)
    world = VCAPWorld(nbt.value)
    print("Loading world...")

    if progressFunction:
        progressFunction(0)

    frame = world.get_frame(0)
    sections: TAG_List = frame['sections']
    for i in range(0, len(sections)):
        # print(f'Parsing section {i + 1} / {len(sections)}')
        readSection(sections[i], vcontext)
        if progressFunction:
            progressFunction((i + 1) / len(sections))

def readSection(section: TAG_Compound, vcontext: VCAPContext):
    palette: TAG_List = section['palette']
    offset: tuple[int, int, int] = (section['x'].value, section['y'].value, section['z'].value)
    blocks: TAG_Byte_Array = section['blocks']
    bblocks = blocks.value

    for y in range(0, 16):
        for z in range(0, 16):
            for x in range(0, 16):
                index = bblocks.item((y * 16 + z) * 16 + x)
                model_id: TAG_String = palette[index]

                place(model_id.value, pos=(offset[0] * 16 + x, offset[1] * 16 + y, offset[2] * 16 + z), vcontext=vcontext)


def place(model_id: str, pos: tuple[float, float, float], vcontext: VCAPContext):
    # if not (model_id in vcontext.models):
    #     raise RuntimeError(f'Model {model_id} does not have a mesh!')
    mesh = vcontext.models[model_id]
    if (len(mesh.vertices) == 0): return

    util.add_mesh(vcontext.target, mesh, Matrix.Translation(pos))

    
