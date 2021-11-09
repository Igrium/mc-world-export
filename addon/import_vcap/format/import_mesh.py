from typing import IO

import bmesh
from . import import_obj


def load(context, name: str, file: IO[bytes]):
    (meshes, mats) = import_obj.load(context.context, file, name=name, unique_materials=context.materials, use_split_objects=False, use_split_groups=True)
    context.materials = mats # Likely won't do anything.
    
    if (len(meshes) > 1):
        # Compile face layers
        bm = bmesh.new()
        bm.from_mesh(meshes[0])
        for i in range(1, len(meshes)):
            oldVerts = bm.verts
            bm.from_mesh(meshes[i])
        
        print(bmesh.ops.find_doubles(bm, verts=bm.verts, dist=.001))
    return(meshes[0])