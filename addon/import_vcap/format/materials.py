import json
import numbers
from typing import IO

import bpy
from bpy.types import Material, Node

def read(file: IO, name: str):
    """Read a vcap material entry.

    Args:
        file (IO): File input stream.
        name (str): Material name.

    Returns:
        [type]: [description]
    """
    obj = json.load(file)
    return parse(obj, name)

def parse(obj, name: str):
    """Parse a vcap material entry.

    Args:
        obj ([type]): Unserialized json.
        name (str): Material name.
    """
    def parse_field(value, target: Node, index: int):
        if isinstance(value, numbers.Number):
            target.inputs[index].default_value = value
        elif isinstance(value, str):
            tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
            mat.node_tree.links.new(tex.outputs[0], target.inputs[index])
            # TODO: Add texture
        elif isinstance(value, list):
            target.inputs[index].default_value = (value[0], value[1], value[2])
        else:
            print(f'Cannot add input with type {type(value)} to material {name}')

    transparent: bool = False
    if 'transparent' in obj:
        transparent = obj['transparent']

    use_vertex_colors: bool = False
    if 'useVertexColors' in obj:
        use_vertex_colors = obj['useVertexColors']

    mat = bpy.data.materials.new(name)
    mat.use_nodes = True

    principled_node = mat.node_tree.nodes.get('Principled BSDF')

    # Special case for color because we need to connect transparency.
    if 'color' in obj:
        color = obj['color']
        if isinstance(color, str):
            tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
            if use_vertex_colors:
                mix = mat.node_tree.nodes.new('ShaderNodeMixRGB')
                mix.blend_type = 'MULTIPLY'
                mix.inputs[0].default_value = 1
                vcolor = mat.node_tree.nodes.new('ShaderNodeVertexColor')

                mat.node_tree.links.new(tex.outputs[0], mix.inputs[1]) # Tex to mix
                mat.node_tree.links.new(vcolor.outputs[0], mix.inputs[2]) # Vertex color to mix
                mat.node_tree.links.new(mix.outputs[0], principled_node.inputs[0]) # Mix to principled
            else:
                mat.node_tree.links.new(tex.outputs[0], principled_node.inputs[0])

            mat.node_tree.links.new(tex.outputs[1], principled_node.inputs[19]) # Alpha
            # TODO: Add texture
        else:
            parse_field(color, principled_node, 0)
    
    if 'roughness' in obj:
        parse_field(obj['roughness'], principled_node, 7)
    if 'metallic' in obj:
        parse_field(obj['metallic'], principled_node, 4)
    if 'normal' in obj:
        normal = mat.node_tree.nodes.new('ShaderNodeNormalMap')
        mat.node_tree.links.new(normal.outputs[0], principled_node.inputs[20])

        tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
        mat.node_tree.links.new(tex.outputs[0], normal.inputs[1])
        # TODO: Add texture
    
    return mat