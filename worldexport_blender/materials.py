from bpy.types import Material
from typing import Iterable

def process_materials(mats: Iterable[Material]):
    for mat in mats:
        process_material(mat)
    
def process_material(mat: Material):
    node_tree = mat.node_tree
    if not node_tree: return
    
    for node in node_tree.nodes:
        if node.type == 'TEX_IMAGE':
            node.interpolation = 'Closest'