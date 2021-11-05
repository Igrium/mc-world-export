import json
import numbers
from typing import IO

import bpy
from bpy.types import Node
from .context import VCAPContext
from . import util

def load_texture(tex_id: str, context: VCAPContext, is_data=False):
    if tex_id in context.textures:
        return context.textures[tex_id]

    filename = f'tex/{tex_id}.png'
    file = context.archive.open(filename)

    image = util.import_image(file, tex_id, is_data=is_data)
    context.textures[tex_id] = image
    return image

def read(file: IO, name: str, context: VCAPContext):
    """Read a vcap material entry.

    Args:
        file (IO): File input stream.
        name (str): Material name.

    Returns:
        [type]: [description]
    """
    obj = json.load(file)
    return parse(obj, name, context)

def parse(obj, name: str, context: VCAPContext):
    """Parse a vcap material entry.

    Args:
        obj ([type]): Unserialized json.
        name (str): Material name.
    """
    def parse_field(value, target: Node, index: int, is_data: False):
        if isinstance(value, numbers.Number):
            target.inputs[index].default_value = value
        elif isinstance(value, str):
            tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
            mat.node_tree.links.new(tex.outputs[0], target.inputs[index])

            tex.image = load_texture(value, context, is_data)
            tex.interpolation = 'Closest'

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
            tex.image = load_texture(color, context, False)
            tex.interpolation = 'Closest'
        else:
            parse_field(color, principled_node, 0)
    
    if 'roughness' in obj:
        parse_field(obj['roughness'], principled_node, 7, True)
    if 'metallic' in obj:
        parse_field(obj['metallic'], principled_node, 4, True)
    if 'normal' in obj and isinstance(obj['normal'], str):
        normal = mat.node_tree.nodes.new('ShaderNodeNormalMap')
        mat.node_tree.links.new(normal.outputs[0], principled_node.inputs[20])

        tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
        mat.node_tree.links.new(tex.outputs[0], normal.inputs[1])
        tex.image = load_texture(obj['normal'], context, True)
        tex.interpolation = 'Closest'
    return mat
    