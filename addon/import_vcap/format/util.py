from typing import Sequence
from bmesh.types import BMesh
from bpy.types import Mesh
from mathutils import Matrix, Vector

def add_mesh(mesh1: BMesh, mesh2: Mesh, matrix: Matrix=Matrix.Identity(4)):
    """Add the contents of a mesh into another mesh.

    Args:
        mesh1 (Mesh): The base mesh.
        mesh2 (Mesh): The mesh to add.
        offset (Sequence[float, float, float]): Offset vector
    """
    mesh2.transform(matrix)
    mesh1.from_mesh(mesh2)
    mesh2.transform(matrix.inverted())
