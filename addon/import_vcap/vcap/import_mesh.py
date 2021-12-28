from typing import IO, List

import bmesh
from bmesh.types import BMFace, BMLayerCollection, BMLoop, BMVert
from .context import VCAPContext
from . import import_obj, materials


def load(context: VCAPContext, name: str, file: IO[bytes]):
    (meshes, mats, vgroups) = import_obj.load(context.context, file, name=name, unique_materials=context.materials, use_split_objects=False, use_split_groups=True)
    context.materials = mats # Likely won't do anything.

    if (len(meshes) > 1):
        # Compile face layers
        bm = bmesh.new()
        bm.from_mesh(meshes[0])

        base_mats: dict[BMFace, str] = {} # Keep track of the original materials for each face.

        uvs: BMLayerCollection = bm.loops.layers.uv
        uv_lay = uvs.active
        for i in range(1, len(meshes)):
            layer_uv = uvs.new(get_flayer_uv(i))

            oldFaces: list[BMFace] = []
            for face in bm.faces:
                oldFaces.append(face)
                
            bm.from_mesh(meshes[i])
            
            newFaces: list[BMFace] = []
            for face in bm.faces:
                if not (face in oldFaces):
                    newFaces.append(face)
            doubleFaces = find_double_faces(oldFaces, newFaces)
            doubles: dict[BMLoop, BMLoop] = {}
            for face in doubleFaces:
                doubles.update(find_double_loops(face.loops, doubleFaces[face].loops))
            if len(doubles) == 0:
                continue
            
            for oldLoop in doubles:
                oldLoop[layer_uv].uv = doubles[oldLoop][uv_lay].uv

            for face1 in doubleFaces:
                face2 = doubleFaces[face1]
                if (face1 in base_mats):
                    mat1 = base_mats[face1]
                else:
                    mat1: str = _get_nth_key(context.materials, face1.material_index)
                    base_mats[face1] = mat1
                mat2: str = _get_nth_key(context.materials, face2.material_index)

                if (mat1 != mat2):
                    gen_comp_mat(context, mat1, mat2)
                face1.material_index = len(context.materials) - 1

            to_remove = []
            for face in doubleFaces.values():
                if not (face in to_remove):
                    to_remove.append(face)
            bmesh.ops.delete(bm, geom=to_remove, context='FACES')
            # bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=.001)
            context.context.blend_data.meshes.remove(meshes[i])
            
        bm.to_mesh(meshes[0])
        bm.free()

        meshes[0].name = name
        return meshes[0]
    else:
        return meshes[0]

def find_double_faces(input: List[BMFace], comparator: List[BMFace]):
    out: dict[BMFace, BMFace] = {}

    for face in input:
        for tester in comparator:
            if _are_faces_equal(face, tester):
                out[face] = tester
    
    return out

def find_double_loops(input: list[BMLoop], comparator: list[BMLoop]):
    out: dict[BMLoop, BMLoop] = {}
    # Really high complexity; should not be run on large meshes.

    for loop in input:
        for tester in comparator:
            if (loop.vert.co == tester.vert.co):
                out[loop] = tester
    
    return out

def gen_comp_mat(context: VCAPContext, mat1: str, mat2: str):
    name = f'comp_{mat1}.{mat2}'
    
    if name in context.materials:
        return context.materials[name]
    
    mat = materials.create_composite_material(name, context, mat1, mat2)
    context.materials[name] = mat
    return mat

def get_flayer_uv(layer: int):
    return f'flayer_{str(layer)}'

def _are_faces_equal(face1: BMFace, face2: BMFace):
    for vert in face1.verts:
        vert: BMVert
        found = False
        for vert2 in face2.verts:
            vert2: BMVert
            if vert2.co == vert.co:
                found = True
                break
        
        if not found:
            return False
    
    return True
        
def _get_nth_key(dictionary, n=0):
    if n < 0:
        n += len(dictionary)
    for i, key in enumerate(dictionary.keys()):
        if i == n:
            return key
    raise IndexError("dictionary index out of range") 