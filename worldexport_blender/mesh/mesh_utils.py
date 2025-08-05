import bpy
import bmesh
from bmesh.types import BMesh, BMVert

from bpy.types import Object, Mesh
from typing import Callable, cast

def split_mesh_by_vgroups(obj: Object, should_split: Callable[[str], bool]) -> dict[str, Object]:
    """Split a mesh object into multiple objects based on its vertex groups. 
    If a vertex is part of multiple applicable groups, only the first one is used.

    Args:
        obj (Object): Object to split
        should_split (Callable[[str], bool]): Predicate for whether a group should be split
        
    Returns:
        dict[str, Object]: A dict with all split group names and their corrisponding objects
    """
    if obj.type != 'MESH':
        raise Exception("Object is not a mesh!")
    
    mesh = cast(Mesh, obj.data)
    
    split_groups: set[int] = set()
    group_names: dict[int, str] = {}
    for vg in obj.vertex_groups:
        group_names[vg.index] = vg.name
        if should_split(vg.name):
            split_groups.add(vg.index)
    
    
    # Find vertices belonging to each group
    vg_assignment: dict[int, set[int]] = {}
    for v in mesh.vertices:
        for vg in v.groups:
            # Only respects the first applicable group (should only ever be one)
            if vg.group in split_groups:
                vg_assignment.setdefault(v.groups[0].group, set()).add(v.index)
                # break

    bm = bmesh.new()
    bm.from_mesh(mesh)

    # Map original vert indices to bmesh verts
    bm.verts.ensure_lookup_table()
    
    bm_new_map: dict[int, BMesh] = {}
    
    for vg, vg_verts in vg_assignment.items():
        faces_to_extract = [f for f in bm.faces if all(v.index in vg_verts for v in f.verts)]
        if not faces_to_extract:
            continue
        
        bm_new = bmesh.new()
        old_to_new_verts: dict[BMVert, BMVert] = {}
        
        for f in faces_to_extract:
            new_face_verts: list[BMVert] = []
            for v in f.verts:
                if v not in old_to_new_verts:
                    v_new = bm_new.verts.new(v.co)
                    v_new.normal = v.normal
                    old_to_new_verts[v] = v_new
                new_face_verts.append(old_to_new_verts[v])
            try:
                bm_new.faces.new(new_face_verts)
            except ValueError:
                print("ValueError: face already exists")
                pass
        
        # bmesh.ops.split(bm, geom=faces_to_extract, dest=bm_new)
            
        bm_new.verts.index_update()
        bm_new.verts.ensure_lookup_table()
        bm_new.faces.ensure_lookup_table()
        
        bmesh.ops.delete(bm, geom=faces_to_extract, context='FACES')
        
        # TODO: Faces
        
        bm_new_map[vg] = bm_new
    
    # Update base mesh
    bm.to_mesh(mesh)    
    result: dict[str, Object] = {}
    
    for vg, bm in bm_new_map.items():
        name = group_names[vg]
        mesh = bpy.data.meshes.new(name)
        bm.to_mesh(mesh)
        
        obj = bpy.data.objects.new(name, mesh)
        result[name] = obj
    
    return result
    
    # faces_to_extract = [f for f in bm.faces if all(v.index in vg_verts for v in f.verts)]