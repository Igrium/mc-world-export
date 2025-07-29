from bpy.types import Material, Mesh, ID
from typing import Iterable, Any
import bpy
import re
import os
import json

def process_materials(replay_root: str, mats: Iterable[Material]):
    custom_props: dict[str, dict] = {}
    for root, dirs, files in os.walk(replay_root):
        for file in files:
            if not file.endswith('.mtl.json'): continue
            
            with open(os.path.join(replay_root, file), 'r') as f:
                json_data: dict[str, dict] = json.load(f)
            
            for mat_name, mat_props in json_data.items():
                props = custom_props.get(mat_name)
                if not props:
                    custom_props[mat_name] = mat_props
                else:
                    props.update(mat_props)
                    
    for mat in mats:
        process_material(mat, custom_props.get(mat.name))
    
def process_material(mat: Material, custom_props: dict[str, Any] | None = None):
    print(custom_props)
    node_tree = mat.node_tree
    if not node_tree: return
    
    for node in node_tree.nodes:
        if node.type == 'TEX_IMAGE':
            node.interpolation = 'Closest'
    
    if custom_props:
        apply_custom_props(mat, custom_props)

def apply_custom_props(mat: Material, custom_props: dict[str, Any]):
    if custom_props.get('renderMode') == 'dithered':
        mat.surface_render_method = 'DITHERED'
    elif custom_props.get('renderMode') == 'blended':
        mat.surface_render_method = 'BLENDED'
    
    ...

def merge_duplicate_materials(meshes: Iterable[Mesh]):
    base_names: dict[str, list[str]] = {}
    
    for mesh in meshes:
        for mat in mesh.materials:
            
            bname = get_base_name(mat.name)
            bname_list = base_names.get(bname)
            if not bname_list:
                bname_list = []
                base_names[bname] = bname_list
            
            bname_list.append(mat.name)
    
    original_variants = {
        bname: _find_original_variant(bname_list) 
        for bname, bname_list in base_names.items()
    }
    
    for mesh in meshes:
        for i in range(0, len(mesh.materials)):
            orig = mesh.materials[i]
            if not orig: continue
            
            mesh.materials[i] = bpy.data.materials[original_variants[get_base_name(orig.name)]]
 
def get_base_name(name: str) -> str:
    """Get the base name of a datablock that has a numeric extension.

    Args:
        name (str): Datablock name (ex: `mesh.001`)

    Returns:
        str: Base name (ex: `mesh`)
    """
    match = re.match(r"^(.*?)(\.\d{3})?$", name)
    if match:
        return match.group(1)
    else: return name   
    

def _find_original_variant(variants: Iterable[str]):
    """
    From a list of datablock variants, return the one without .00x suffix if it exists.
    Otherwise, return the variant with the lowest .00x number.
    """
    # Prefer the name with no numeric suffix.
    for variant in variants:
        if not re.search(r"\.\d{3}$", variant):
            return variant

    # Otherwise, return the one with the lowest number.
    def suffix_number(variant):
        match = re.search(r"\.(\d{3})$", variant)
        return int(match.group(1)) if match else float("inf")

    return min(variants, key=suffix_number)
