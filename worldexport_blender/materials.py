from bpy.types import Material, Mesh, ShaderNodeGroup, ShaderNodeBsdfPrincipled
from typing import Iterable, Any, cast
import bpy
import os
import json

from . import prefabs
from .replay_types import PrefabDatablocks, ReplayImportContext

def process_materials(mats: Iterable[Material], context: ReplayImportContext):
    
    prefabs.load(prefabs.prefab_file)
    
    custom_props: dict[str, dict] = {}
    for root, dirs, files in os.walk(context.replay_root):
        for file in files:
            if not file.endswith('.mtl.json'): continue
            
            with open(os.path.join(context.replay_root, file), 'r') as f:
                json_data: dict[str, dict] = json.load(f)
            
            for mat_name, mat_props in json_data.items():
                props = custom_props.get(mat_name)
                if not props:
                    custom_props[mat_name] = mat_props
                else:
                    props.update(mat_props)
                    
    for mat in mats:
        process_material(mat, custom_props.get(mat.name), context)
    
def process_material(mat: Material, custom_props: dict[str, Any] | None, context: ReplayImportContext):
    print(custom_props)
    node_tree = mat.node_tree
    if not node_tree: return
    
    for node in node_tree.nodes:
        if node.type == 'TEX_IMAGE':
            node.interpolation = 'Closest'
    
    if custom_props:
        apply_custom_props(mat, custom_props, context)

def apply_custom_props(mat: Material, custom_props: dict[str, Any], context: ReplayImportContext):
    if custom_props.get('renderMode') == 'blended':
        mat.surface_render_method = 'BLENDED'
    else:
        mat.surface_render_method = 'DITHERED'
    
    node_tree = mat.node_tree
    if not node_tree: return
    
    principled_node = cast(ShaderNodeBsdfPrincipled, node_tree.nodes["Principled BSDF"])
    if not principled_node or principled_node.bl_idname != 'ShaderNodeBsdfPrincipled':
        print("Unable to find principled node!")
        return
    
    if custom_props.get('vertexTint'):
        vert_tint_node = cast(ShaderNodeGroup, node_tree.nodes.new('ShaderNodeGroup'))
        vert_tint_node.node_tree = context.prefabs.mul_vertex_color
        vert_tint_node.location = (-160.0, 300.0)

        color_socket = principled_node.inputs['Base Color']
        color_link = color_socket.links[0] # type: ignore
        
        node_tree.links.new(color_link.from_socket, vert_tint_node.inputs[0]) 
        node_tree.links.new(vert_tint_node.outputs[0], color_socket)
    ...
