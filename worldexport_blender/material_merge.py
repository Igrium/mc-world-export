import bpy
import re

from bpy.types import Mesh
from typing import Iterable

def merge_duplicate_materials(meshes: Iterable[Mesh]):
    base_names: dict[str, list[str]] = {}
    
    for mesh in meshes:
        for mat in mesh.materials:
            base_names.setdefault(get_base_name(mat.name), []).append(mat.name)
    
    original_variants = {
        bname: _find_original_variant(bname_list) 
        for bname, bname_list in base_names.items()
    }
    
    for mesh in meshes:
        for i, orig in enumerate(mesh.materials):
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
