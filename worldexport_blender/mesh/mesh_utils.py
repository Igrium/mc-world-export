import bmesh
from bmesh.types import BMFace

from bpy.types import Object, Mesh
from typing import Callable, cast

def split_vertex_groups(obj: Object, should_split: Callable[[str], bool]) -> dict[str, Object]:
    """Split a mesh object into multiple objects based on its vertex groups. 
    If a vertex is part of multiple applicable groups, only the first one is used.

    Args:
        obj (Object): Object to split
        should_split (Callable[[str], bool]): Predicate for whether a group should be split
        
    Returns:
        dict[str, Object]: A dict with all split group names and their corrisponding objects
    """
    if obj.type != 'MESH':
        raise TypeError("Object is not a mesh!")
    
    mesh = cast(Mesh, obj.data)
    
    split_groups: set[int] = set() # Indices of the groups to split
    group_names: dict[int, str] = {}
    for vg in obj.vertex_groups:
        group_names[vg.index] = vg.name
        if should_split(vg.name):
            split_groups.add(vg.index)
    
    # Find vertices belonging to each group
    vg_assignment: dict[int, set[int]] = {} # A dict of group indices and the vertices that will be split with it.
    for v in mesh.vertices:
        for vg in v.groups:
            # Only respects the first applicable group (should only ever be one)
            if vg.group in split_groups:
                vg_assignment.setdefault(v.groups[0].group, set()).add(v.index)
                break
    
    bm = bmesh.new()
    bm.from_mesh(mesh)

    # Map original vert indices to bmesh verts
    bm.verts.ensure_lookup_table()
    result: dict[str, Object] = {}
    
    to_delete: list[BMFace] = []
    
    for vg, vg_verts in vg_assignment.items():
        bm.faces.ensure_lookup_table()
        extract_faces = [f for f in bm.faces if all(v.index in vg_verts for v in f.verts)]
        if not extract_faces:
            print(f"Vertex group {group_names[vg]} has no faces!")
            continue
        
        bm_new = bm.copy()
        bm_new.faces.ensure_lookup_table()
        
        retain_faces = [bm_new.faces[f.index] for f in bm.faces if f not in extract_faces]
        
        to_delete.extend(extract_faces)
        bmesh.ops.delete(bm_new, geom=retain_faces, context='FACES')
        
        name = group_names[vg]

        mesh_new = mesh.copy()
        mesh_new.name = name
        bm_new.to_mesh(mesh_new)
        bm_new.free()
        
        obj_new = obj.copy()
        obj_new.name = name
        obj_new.data = mesh_new
        
        result[name] = obj_new
    
    bmesh.ops.delete(bm, geom=to_delete, context='FACES')
    
    bm.to_mesh(mesh)
    bm.free()
    return result
