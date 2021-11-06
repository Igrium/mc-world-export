from typing import IO, Sequence
from bmesh.types import BMesh
import bpy
from bpy.types import Image, Mesh, MeshLoopColor
from mathutils import Matrix, Vector

COLOR_LAYER = "tint"

def add_mesh(mesh1: BMesh, mesh2: Mesh, matrix: Matrix=Matrix.Identity(4), color: list[float]=[1,1,1,1]):
    """Add the contents of a mesh into another mesh.

    Args:
        mesh1 (Mesh): The base mesh.
        mesh2 (Mesh): The mesh to add.
        offset (Sequence[float, float, float]): Offset vector
    """
    if not COLOR_LAYER in mesh2.vertex_colors:
        mesh2.vertex_colors.new(name=COLOR_LAYER)
    
    vcolors = mesh2.vertex_colors[COLOR_LAYER]
    for key in mesh2.loops.keys():
        col = vcolors.data[key]
        col.color = color.copy()

    mesh2.transform(matrix)
    mesh1.from_mesh(mesh2)
    mesh2.transform(matrix.inverted())

def import_image(file: IO[bytes], name: str, alpha=True, is_data=False) -> Image:
    """Pack an image from an IO stream into the current blend.

    Args:
        file (IO[bytes]): Raw data of PNG file.
        name (str): Name to give the datablock.
        alpha (bool, optional): Use alpha channel. Defaults to True.
        is_data (bool, optional): Create image with non-color data color space. Defaults to False.

    Returns:

        [type]: Loaded image datablock.
    """
    print(f'Loading image: {name}')
    data = file.read()

    image = bpy.data.images.new(name, 1024, 1024, alpha=alpha, is_data=is_data)
    image.file_format = 'PNG'
    image.pack(data=data, data_len=len(data))
    image.source = 'FILE'

    return image