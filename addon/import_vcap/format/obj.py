# ##### BEGIN GPL LICENSE BLOCK #####
#
#  This program is free software; you can redistribute it and/or
#  modify it under the terms of the GNU General Public License
#  as published by the Free Software Foundation; either version 2
#  of the License, or (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program; if not, write to the Free Software Foundation,
#  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
#
# ##### END GPL LICENSE BLOCK #####

"""
Imports OBJ files as a part of the VCAP importer. Derived from the stock OBJ importer.
"""

import array
import os
import time
import io
from typing import IO
import bpy
from bpy.types import Context, Material, Mesh, Object
import mathutils

from bpy_extras.io_utils import unpack_list
from bpy_extras.image_utils import load_image
from bpy_extras.wm_utils.progress_report import ProgressReport

def line_value(line_split):
    """
    Returns 1 string representing the value for this line
    None will be returned if there's only 1 word
    """
    length = len(line_split)
    if length == 1:
        return None

    elif length == 2:
        return line_split[1]

    elif length > 2:
        return b' '.join(line_split[1:])

def strip_slash(line_split: bytes):
    if line_split[-1][-1] == 92: # '\' char
        if len(line_split[-1]) == 1:
            line_split.pop()
        else:
            line_split[-1] = line_split[-1][:-1]
        return True
    return False

def get_float_func(file: IO[bytes]):
    file.seek(0)

    for line in file.readlines():
        line = line.lstrip()
        if line.startswith(b'v'): # vn vt v
            if b',' in line:
                return lambda f: float(f.replace(b',', b'.'))
            elif b'.' in line:
                return float
    
    # in case all vert values were ints
    return float

def face_is_edge(face):
    """Simple check to test whether given (temp, working) data is an edge, and not a real face."""
    face_vert_loc_indices = face[0]
    face_vert_nor_indices = face[1]
    return len(face_vert_nor_indices) == 1 or len(face_vert_loc_indices) == 2


def sort_mesh(verts_loc, faces, unique_materials, name):
    use_verts_nor = any(f[1] for f in faces)
    use_verts_tex = any(f[2] for f in faces)
    return (verts_loc, faces, unique_materials, name, use_verts_nor, use_verts_tex)

def create_mesh(use_edges,
                verts_loc,
                verts_nor,
                verts_tex,
                faces,
                unique_materials,
                unique_smooth_groups,
                vertex_groups,
                dataname) -> Mesh:
    """
    Takes all the data gathered and generates a mesh.
    deals with ngons, sharp edges and assigning materials
    """

    if unique_smooth_groups:
        sharp_edges = set()
        smooth_group_users = {context_smooth_group: {} for context_smooth_group in unique_smooth_groups.keys()}
        context_smooth_group_old = -1

    fgon_edges = set() # Used for storing fgon keys when we need to tessellate/untessellate them (ngons with hole).
    edges = []
    tot_loops = 0

    context_object_key = None

    # reverse loop through face indices
    for f_idx in range(len(faces) - 1, -1, -1):
        face = faces[f_idx]

        (face_vert_loc_indices,
        face_vert_nor_indices,
        face_vert_tex_indices,
        context_material,
        context_smooth_group,
        context_object_key,
        face_invalid_blenpoly) = face

        len_face_vert_loc_indices = len(face_vert_loc_indices)

        if len_face_vert_loc_indices == 1:
            faces.pop(f_idx) # cant add single vert faces
        
        # Face with a single item in face_vert_nor_indices is actually a polyline!
        elif face_is_edge(face):
            if (use_edges):
                edges.extend((face_vert_loc_indices[i], face_vert_loc_indices[i + 1])
                        for i in range(len_face_vert_loc_indices - 1))
        
        else:
            # Smooth Group
            if unique_smooth_groups and context_smooth_group:
                # Is a part of a smooth group and is a face
                if context_smooth_group_old is not context_smooth_group:
                    edge_dict = smooth_group_users[context_smooth_group]
                    context_smooth_group_old = context_smooth_group
                
                prev_vidx = face_vert_loc_indices[-1]
                for vidx in face_vert_loc_indices:
                    edge_key = (prev_vidx, vidx) if (prev_vidx < vidx) else (vidx, prev_vidx)
                    prev_vidx = vidx
                    edge_dict[edge_key] = edge_dict.get(edge_key, 0) + 1

            # NGons into triangles
            if face_invalid_blenpoly:
                # ignore triangles with invalid indices
                if len(face_vert_loc_indices) > 3:
                    from bpy_extras.mesh_utils import ngon_tessellate
                    ngon_face_indices = ngon_tessellate(verts_loc, face_vert_loc_indices, debug_print=bpy.app.debug)
                    faces.extend([([face_vert_loc_indices[ngon[0]],
                                    face_vert_loc_indices[ngon[1]],
                                    face_vert_loc_indices[ngon[2]],
                                    ],
                                [face_vert_nor_indices[ngon[0]],
                                    face_vert_nor_indices[ngon[1]],
                                    face_vert_nor_indices[ngon[2]],
                                    ] if face_vert_nor_indices else [],
                                [face_vert_tex_indices[ngon[0]],
                                    face_vert_tex_indices[ngon[1]],
                                    face_vert_tex_indices[ngon[2]],
                                    ] if face_vert_tex_indices else [],
                                context_material,
                                context_smooth_group,
                                context_object_key,
                                [],
                                )
                                for ngon in ngon_face_indices]
                                )
                    tot_loops += 3 * len(ngon_face_indices)

                    # edges to make ngons
                    if len(ngon_face_indices) > 1:
                        edge_users = set()
                        for ngon in ngon_face_indices:
                            prev_vidx = face_vert_loc_indices[ngon[-1]]
                            for ngidx in ngon:
                                vidx = face_vert_loc_indices[ngidx]
                                if vidx == prev_vidx:
                                    continue  # broken OBJ... Just skip.
                                edge_key = (prev_vidx, vidx) if (prev_vidx < vidx) else (vidx, prev_vidx)
                                prev_vidx = vidx
                                if edge_key in edge_users:
                                    fgon_edges.add(edge_key)
                                else:
                                    edge_users.add(edge_key)

                faces.pop(f_idx)
            else:
                tot_loops += len_face_vert_loc_indices
    
    # Build sharp edges
    if unique_smooth_groups:
        for edge_dict in smooth_group_users.values():
            for key, users in edge_dict.items():
                if users == 1: # This edge is on the boundry of a group
                    sharp_edges.add(key)
    
    me: Mesh = bpy.data.meshes.new(dataname)

    material_mapping = {name: i for i, name in enumerate(unique_materials)}  # enumerate over unique_materials keys()
    materials = [None] * len(unique_materials)

    for mat in materials:
        me.materials.append(mat)

    me.vertices.add(len(verts_loc))
    me.loops.add(tot_loops)
    me.polygons.add(len(faces))

    # verts_loc is a list of (x, y, z) tuples
    me.vertices.foreach_set("co", unpack_list(verts_loc))

    loops_vert_idx = tuple(vidx for (face_vert_loc_indices, _, _, _, _, _, _) in faces for vidx in face_vert_loc_indices)
    faces_loop_start = []
    lidx = 0
    for f in faces:
        faces_vert_loc_indices = f[0]
        nbr_vidx = len(face_vert_loc_indices)
        faces_loop_start.append(lidx)
        lidx += nbr_vidx
    faces_loop_total = tuple(len(face_vert_loc_indices) for (face_vert_loc_indices, _, _, _, _, _, _) in faces)

    me.loops.foreach_set("vertex_index", loops_vert_idx)
    me.polygons.foreach_set("loop_start", faces_loop_start)
    me.polygons.foreach_set("loop_total", faces_loop_total)

    faces_ma_index = tuple(material_mapping[context_material] for (_, _, _, context_material, _, _, _) in faces)
    me.polygons.foreach_set("material_index", faces_ma_index)

    faces_use_smooth = tuple(bool(context_smooth_group) for (_, _, _, _, context_smooth_group, _, _) in faces)
    me.polygons.foreach_set("use_smooth", faces_use_smooth)

    if verts_nor and me.loops:
        # Note: we store 'temp' normals in loops, since validate() may alter final mesh,
        #       we can only set custom lnors *after* calling it.
        me.create_normals_split()
        loops_nor = tuple(no for (_, face_vert_nor_indices, _, _, _, _, _) in faces
                             for face_noidx in face_vert_nor_indices
                             for no in verts_nor[face_noidx])
        me.loops.foreach_set("normal", loops_nor)
    
    if verts_tex and me.polygons:
        verts_tex = [uv if len(uv) == 2 else uv + [0.0] for uv in verts_tex]
        me.uv_layers.new(do_init=False)
        loops_uv = tuple(uv for (_, _, face_vert_tex_indices, _, _, _, _) in faces
                            for face_uvidx in face_vert_tex_indices
                            for uv in verts_tex[face_uvidx])
        me.uv_layers[0].data.foreach_set("uv", loops_uv)
    
    use_edges = use_edges and bool(edges)
    if use_edges:
        me.edges.add(len(edges))
        me.edges.foreach_set('vertices', unpack_list(edges))
    
    me.validate(clean_customdata=False) # *Very* important to not remove lnors here!
    me.update(calc_edges=use_edges, calc_edges_loose=use_edges)

    # Un-tessellate as much as possible, in case we had to triangulate some ngons...
    if fgon_edges:
        import bmesh
        bm = bmesh.new()
        bm.from_mesh(me)
        verts = bm.verts[:]
        get = bm.edges.get
        edges = [get((verts[vidx1], verts[vidx2])) for vidx1, vidx2 in fgon_edges]
        try:
            bmesh.ops.dissolve_edges(bm, edges=edges, use_verts=False)
        except:
            import traceback
            traceback.print_exc()
        
        bm.to_mesh(me)
        bm.free()
    
    # XXX If validate changes the geometry, this is likely to be broken...
    if unique_smooth_groups and sharp_edges:
        for e in me.edges:
            if e.key in sharp_edges:
                e.use_edge_sharp = True
    
    if verts_nor:
        clnors = array.array('f', [0.0] * (len(me.loops) * 3))
        me.loops.foreach_get("normal", clnors)

        if not unique_smooth_groups:
            me.polygons.foreach_set("use_smooth", [True] * len(me.polygons))

        me.normals_split_custom_set(tuple(zip(*(iter(clnors),) * 3)))
        me.use_auto_smooth = True
    
    return me

def load(context: Context, file: IO[bytes], name: str = 'block', *, global_clamp_size=0.0,
        use_smooth_groups=True, use_edges=True, global_matrix: mathutils.Matrix = None, materials: dict[str, Material] = {}):
    
    def handle_vec(line_start, context_multi_line, line_split, tag, data, vec, vec_len):
        ret_context_multi_line = tag if strip_slash(line_split) else b''
        if line_start == tag:
            vec[:] = [float_func(v) for v in line_split[1:]]
        elif context_multi_line == tag:
            vec += [float_func(v) for v in line_split]
        if not ret_context_multi_line:
            data.append(tuple(vec[:vec_len]))
        return ret_context_multi_line
    
    def create_face(context_material, context_smooth_group, context_object_key):
        face_vert_loc_indices = []
        face_vert_nor_indices = []
        face_vert_tex_indices = []
        return (
            face_vert_loc_indices,
            face_vert_nor_indices,
            face_vert_tex_indices,
            context_material,
            context_smooth_group,
            context_object_key,
            [],  # If non-empty, that face is a Blender-invalid ngon (holes...), need a mutable object for that...
        )

    if global_matrix is None:
        global_matrix = mathutils.Matrix()

    verts_loc = []
    verts_nor = []
    verts_tex = []
    faces = []  # tuples of the faces
    material_libs = set()  # filenames to material libs this OBJ uses
    vertex_groups = {}  # when use_groups_as_vgroups is true

    # Get the string to float conversion func for this file- is 'float' for almost all files.
    float_func = get_float_func(file)
    
    # Context variables
    context_material = None
    context_smooth_group = None
    context_object_key = None
    context_object_obpart = None
    context_vgroup = None

    object_names = set()

    # Until we can use sets
    use_default_material = False
    unique_materials = {}
    unique_smooth_groups = {}

    # when there are faces that end with \
    # it means they are multiline-
    # since we use xreadline we cant skip to the next line
    # so we need to know whether
    context_multi_line = b''

    # Per-face handling data.
    face_vert_loc_indices = None
    face_vert_nor_indices = None
    face_vert_tex_indices = None
    verts_loc_len = verts_nor_len = verts_tex_len = 0
    face_items_usage = set()
    face_invalid_blenpoly = None
    prev_vidx = None
    face = None
    vec = []

    quick_vert_failures = 0
    skip_quick_vert = False

    file.seek(0)
    f = io.BufferedReader(file, 64)
    for line in f:
        line_split = line.split()

        if not line_split: continue

        line_start = line_split[0]  # we compare with this a _lot_

        if len(line_split) == 1 and not context_multi_line and line_split != b'end':
            print("WARNING, skipping malformatted line: %s" % line.decode('UTF-8', 'replace').rstrip())
            continue
        
        # Handling vertex data are pretty similar, factorize that.
        # Also, most OBJ files store all those on a single line, so try fast parsing for that first,
        # and only fallback to full multi-line parsing when needed, this gives significant speed-up
        # (~40% on affected code).
        if line_start == b'v':
            vdata, vdata_len, do_quick_vert = verts_loc, 3, not skip_quick_vert
        elif line_start == b'vn':
            vdata, vdata_len, do_quick_vert = verts_nor, 3, not skip_quick_vert
        elif line_start == b'vt':
            vdata, vdata_len, do_quick_vert = verts_tex, 2, not skip_quick_vert
        elif context_multi_line == b'v':
            vdata, vdata_len, do_quick_vert = verts_loc, 3, False
        elif context_multi_line == b'vn':
            vdata, vdata_len, do_quick_vert = verts_nor, 3, False
        elif context_multi_line == b'vt':
            vdata, vdata_len, do_quick_vert = verts_tex, 2, False
        else:
            vdata_len = 0
        
        if vdata_len:
            if do_quick_vert:
                try:
                    vdata.append(list(map(float_func, line_split[1:vdata_len + 1])))
                except:
                    do_quick_vert = False
                    # In case we get too many failures on quick parsing, force fallback to full multi-line one.
                    # Exception handling can become costly...
                    quick_vert_failures += 1
                    if quick_vert_failures > 10000:
                        skip_quick_vert = True
            if not do_quick_vert:
                context_multi_line = handle_vec(line_start, context_multi_line, line_split,
                                                context_multi_line or line_start,
                                                vdata, vec, vdata_len)
        
        elif line_start == b'f' or context_multi_line == b'f':
            if not context_multi_line:
                line_split = line_split[1:]
                # Instansiate a face
                face = create_face(context_material, context_smooth_group, context_object_key)
                (face_vert_loc_indices, face_vert_nor_indices, face_vert_tex_indices,
                _1, _2, _3, face_invalid_blenpoly) = face
                if context_material == None:
                    use_default_material = True
                
            for v in line_split:
                obj_vert = v.split(b'/')
                idx = int(obj_vert[0]) # Note that we assume here we cannot get OBJ invalid 0 index...
                vert_loc_index = (idx + verts_loc_len) if (idx < 1) else idx - 1
                
                if not face_invalid_blenpoly:
                    # If we use more than once a same vertex, invalid ngon is suspected.
                    if vert_loc_index in face_items_usage:
                        face_invalid_blenpoly.append(True)
                    else:
                        face_items_usage.add(vert_loc_index)
                face_vert_loc_indices.append(vert_loc_index)

                # formatting for faces with normals and textures is
                # loc_index/tex_index/nor_index
                if len(obj_vert) > 1 and obj_vert[1] and obj_vert[1] != b'0':
                    idx = int(obj_vert[1])
                    face_vert_tex_indices.append((idx + verts_tex_len) if (idx < 1) else idx - 1)
                else:
                    face_vert_tex_indices.append(0)
            
            if not context_multi_line:
                # Means we have finished a face, we have to do final check if ngon is suspected to be blender-invalid...
                if face_invalid_blenpoly:
                    face_invalid_blenpoly.clear()
                    face_items_usage.clear()
                    prev_vidx = face_vert_loc_indices[-1]
                    for vidx in face_vert_loc_indices:
                        edge_key = (prev_vidx, vidx) if (prev_vidx < vidx) else (vidx, prev_vidx)
                        if edge_key in face_items_usage:
                            face_invalid_blenpoly.append(True)
                            break
                        face_items_usage.add(edge_key)
                        prev_vidx = vidx
            
        elif use_edges and (line_start == b'l' or context_multi_line == b'l'):
            # very similar to the face load function above with some parts removed
            if not context_multi_line:
                line_split = line_split[1:]
                # Instantiate a face
                face = create_face(context_material, context_smooth_group, context_object_key)
                face_vert_loc_indices = face[0]
                # XXX A bit hackish, we use special 'value' of face_vert_nor_indices (a single True item) to tag this
                #     as a polyline, and not a regular face...
                face[1][:] = [True]
                faces.append(face)
                if context_material is None:
                    use_default_material = True
            
            context_multi_line = b'l' if strip_slash(line_split) else b''

            for v in line_split:
                obj_vert = v.split(b'/')
                idx = int(obj_vert[0]) - 1
                face_vert_loc_indices.append((idx + len(verts_loc) + 1) if (idx < 0) else idx)
        
        elif line_start == b's':
            if use_smooth_groups:
                context_smooth_group = line_value(line_split)
                if context_smooth_group == b'off':
                    context_smooth_group = None
                elif context_smooth_group: # is not None
                    unique_smooth_groups[context_smooth_group] = None
        
        elif line_start == b'usemtl':
            context_material = line_value(line.split())
            unique_materials[context_material] = None
    
    if use_default_material:
        unique_materials[None] = None
    
    if bpy.ops.object.select_all.poll():
        bpy.ops.object.select_all(action='DESELECT')

    data = sort_mesh(verts_loc, faces, unique_materials, name)
    (verts_loc_split, faces_split, unique_materials_split, dataname, use_vnor, use_vtex) = data
    mesh: Mesh = create_mesh(use_edges,
                verts_loc_split,
                verts_nor if use_vnor else [],
                verts_tex if use_vtex else [],
                faces_split,
                unique_materials_split,
                unique_smooth_groups,
                vertex_groups,
                dataname)
    
    view_layer = context.view_layer
    collection = view_layer.active_layer_collection.collection

    obj: Object = bpy.data.objects.new(name, mesh)
    collection.objects.link(obj)
    