"""The two ways this addon loads OBJ files.

Blender's built-in importer is faster, but it only exposes an operator, so the objects
it creates land in the active collection and have to be recovered by diffing
`bpy.data.objects`. The bundled Python importer returns its objects directly, which is
what the entity loader needs.
"""

import bpy

from bpy.types import Context, Object
from bpy_extras.io_utils import axis_conversion

from .mesh import import_obj


def load_obj_native(filepath: str, use_split_objects=True, use_split_groups=False,
                    import_vertex_groups=False, validate_meshes=True) -> set[Object]:
    """Load an OBJ with Blender's built-in importer, returning the objects it created."""
    existing = set(bpy.data.objects)
    bpy.ops.wm.obj_import(filepath=filepath, use_split_objects=use_split_objects, use_split_groups=use_split_groups,
                          import_vertex_groups=import_vertex_groups, validate_meshes=validate_meshes)
    new = set(bpy.data.objects)
    return new - existing

def load_obj_bundled(context: Context, filepath: str, use_split_objects=False, use_split_groups=False,
                     import_vertex_groups=False, validate_meshes=True) -> set[Object]:
    """Load an OBJ with the bundled Python importer, returning the objects it created."""
    objects = import_obj.load(context, filepath, use_split_objects=use_split_objects, use_split_groups=use_split_groups,
                              use_groups_as_vgroups=import_vertex_groups, global_matrix=axis_conversion('-Z', 'Y').to_4x4())
    return set(objects.values())
