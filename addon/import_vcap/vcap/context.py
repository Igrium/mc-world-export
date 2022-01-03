from zipfile import ZipFile

import bmesh
from bmesh.types import BMesh
from bpy.types import Collection, Context, Image, Material, Mesh, NodeTree, Object

class VCAPContext:
    archive: ZipFile
    """The zip file we're reading from.
    """

    collection: Collection
    """The collection to import into.
    """

    context: Context
    """Current Blender context.
    """

    name: str
    """The name of the file.
    """

    materials: dict[str, Material] = {}
    material_groups: dict[str, NodeTree] = {}
    models: dict[str, Mesh] = {}

    textures: dict[str, Image]

    target: BMesh
    """The BMesh we're building the final mesh in.
    """
    
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
        self.material_groups = {}
        self.textures = {}

        self.collection = collection

        self.target = bmesh.new()

class VCAPSettings:
    __slots__ = (
        'use_vertex_colors',
        'merge_verts'
    )
    
    use_vertex_colors: bool
    merge_verts: bool

    def __init__(self, use_vertex_colors=True, merge_verts=True):
        self.use_vertex_colors = use_vertex_colors
        self.merge_verts = merge_verts