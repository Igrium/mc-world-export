from zipfile import ZipFile

import bmesh
from bmesh.types import BMesh
from bpy.types import Collection, Context, Image, Material, Mesh, Object

from . import import_obj

class VCAPContext:
    archive: ZipFile
    collection: Collection
    context: Context
    name: str

    materials: dict[str, Material] = {}
    models: dict[str, Mesh] = {}

    textures: dict[str, Image]

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
        self.textures = {}

        self.collection = collection

        self.target = bmesh.new()
        self.target.loops.layers.color.new('color')
    
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
        (meshes, mats) = import_obj.load(self.context, file, name=model_id, unique_materials=self.materials, use_split_objects=False)
        file.close()
        if (len(meshes) > 1):
            raise RuntimeError("Only one obj object is allowed per model in VCAP.")
        
        self.models[model_id] = meshes[0]
        self.materials = mats # Likely won't do anything
        return meshes[0]