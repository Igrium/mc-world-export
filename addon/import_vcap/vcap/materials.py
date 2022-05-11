import json
import numbers
from typing import IO, Callable, Type, Union

import bpy
from bpy.types import Image, Material, Node, NodeSocket, NodeTree
from mathutils import Vector
from .context import VCAPContext
from . import util

COLOR = 0
ALPHA = 21
ROUGHNESS = 9
METALLIC = 6
NORMAL = 22

VERTEX_COLOR = "$VERTEX_COLOR"

# They shifted the input IDs in 3.0
if bpy.app.version[0] < 3:
    ALPHA = 19
    ROUGHNESS = 7
    METALLIC = 4
    NORMAL = 20

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
        Material: parsed material
    """
    obj = json.load(file)
    return parse(obj, name, context)

def parse_raw(obj, name: str, image_provider: Callable[[str, bool], Union[Image, None]]) -> Material:
    """Parse a vcap material entry without attaching it to a Vcap Context.

    Args:
        obj (any): Unserialized json
        name (str): Material name
        image_provider (Callable[[str, bool], Image]): Function responsible for retrieving the material's textures.
    """
    
    transparent: bool = False
    if 'transparent' in obj:
        transparent = obj['transparent']
        
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    
    node_tree = mat.node_tree
    
    node_tree.nodes.remove(node_tree.nodes.get('Principled BSDF')) # The generator adds this
    frame, node, alpha = generate_nodes(obj, node_tree, image_provider, name)
    
    mat_output = node_tree.nodes.get('Material Output')
    node_tree.links.new(node.outputs[0], mat_output.inputs[0])
    
    if ('blend_mode' in obj):
        mat.blend_method = str.upper(obj['blend_mode'])
    elif 'transparent' in obj and obj['transparent']:
        # Backwards compatibility
        mat.blend_method = 'HASHED'
        
    return mat


def parse(obj, name: str, context: VCAPContext):
    """Parse a vcap material entry.

    Args:
        obj (any): Unserialized json.
        name (str): Material name.
    """

    mat = bpy.data.materials.new(name)
    mat.use_nodes = True

    node_tree = mat.node_tree
    node_tree.nodes.remove(node_tree.nodes.get('Principled BSDF'))

    group = bpy.data.node_groups.new(name, 'ShaderNodeTree')
    group_inputs = group.nodes.new('NodeGroupInput')
    group_inputs.location = (-800, 0)
    group.inputs.new(name='UV', type='NodeSocketVector')

    def get_image(tex_id: str, is_data: bool):
        return load_texture(tex_id, context, is_data)

    frame, node, alpha = generate_nodes(obj, group, get_image, name, uv_input=group_inputs.outputs[0])

    group_outputs = group.nodes.new('NodeGroupOutput')
    group_outputs.location = (300, 0)

    group.outputs.new(name='Shader', type='NodeSocketShader')
    group.links.new(node.outputs[0], group_outputs.inputs.get('Shader'))

    group.outputs.new(name='Alpha', type='NodeSocketFloat')

    if (alpha != None):
        group.links.new(alpha, group_outputs.inputs['Alpha'])
        context.material_groups[name] = group
    else:
        group_outputs.inputs['Alpha'].default_value = 1


    group_node = node_tree.nodes.new('ShaderNodeGroup')
    group_node.node_tree = group
    material_output = node_tree.nodes.get('Material Output')
    node_tree.links.new(group_node.outputs[0], material_output.inputs[0])

    if ('blend_mode' in obj):
        mat.blend_method = str.upper(obj['blend_mode'])
    elif 'transparent' in obj and obj['transparent']:
        # Backwards compatibility
        mat.blend_method = 'HASHED'
    
    mat.use_backface_culling = True
    return mat

def get_override_prop_name(override_name: str):
    """Given an override name, get the name of the Blender property that must be set.

    Args:
        override_name (str): Override name

    Returns:
        str: Blender property
    """
    return "replay."+override_name

def generate_nodes(obj, node_tree: NodeTree, image_provider: Callable[[str, bool], Image | None], name: str = 'vcap_mat', uv_input: NodeSocket = None):
    """Read a Vcap material and generate a node structure from it.

    Args:
        obj ([type]): Material to read.
        node_tree (NodeTree): The node tree to add the nodes to.
        context (VCAPContext): The current Vcap context.
        name (str, optional): Name of the material. Defaults to 'vcap_mat'.
    
    Returns:
        tuple[Node, Node]: (Node Frame, Output Node, Alpha Socket)
    """

    def parse_field(value, target: Node, index: int, is_data = False):
        if isinstance(value, numbers.Number):
            target.inputs[index].default_value = value
        elif isinstance(value, str):
            tex: Node = node_tree.nodes.new('ShaderNodeTexImage')
            node_tree.links.new(tex.outputs[0], target.inputs[index])

            tex.image = image_provider(value, is_data)
            tex.interpolation = 'Closest'
            tex.parent = frame

            if uv_input:
                node_tree.links.new(uv_input, tex.inputs[0])
            return tex

        elif isinstance(value, list):
            target.inputs[index].default_value = (value[0], value[1], value[2])
        else:
            print(f'Cannot add input with type {type(value)} from material {name}.')

    if 'overrides' in obj:
        overrides = obj['overrides']
    else:
        overrides = {}
    
    # Backwards compatibility
    if 'useVertexColors' in obj and obj['useVertexColors']:
        overrides['color2'] = VERTEX_COLOR
    
    def parse_override(id: str, target: Node, index: int, is_data = False):
        if (id == VERTEX_COLOR):
            node = node_tree.nodes.new('ShaderNodeVertexColor')
            node.parent = frame
        else:
            node = node_tree.nodes.new('ShaderNodeAttribute')
            node.attribute_type = 'OBJECT'
            node.attribute_name = get_override_prop_name(id)
            node.parent = frame
        
        node_tree.links.new(node.outputs[0], target.inputs[index])
        return node
        
    def load_field(name: str, target: Node, index: int, is_data = False):
        if name in overrides:
            return parse_override(overrides[name], target, index)
        elif name in obj:
            return parse_field(obj[name], target, index, is_data)



    principled_node = node_tree.nodes.new('ShaderNodeBsdfPrincipled')
    frame = node_tree.nodes.new(type='NodeFrame')

    # Handle color 1 and color 2
    mix = node_tree.nodes.new('ShaderNodeMixRGB')
    mix.blend_type = 'MULTIPLY'
    mix.inputs[0].default_value = 1
    mix.inputs[1].default_value = (1, 1, 1, 1)
    mix.inputs[2].default_value = (1, 1, 1, 1)
    mix.parent = frame
    mix.location = Vector((-300, 150))

    if 'color2_blend_mode' in obj:
        mix.blend_type = str.upper(obj['color2_blend_mode'])

    node_tree.links.new(mix.outputs[0], principled_node.inputs[COLOR])

    color_tex = load_field('color', mix, 1, False)
    load_field('color2', mix, 2, False)
    
    load_field('roughness', principled_node, ROUGHNESS, True)
    load_field('metallic', principled_node, METALLIC, True)
    if 'normal' in obj and isinstance(obj['normal'], str):
        normal = node_tree.nodes.new('ShaderNodeNormalMap')
        node_tree.links.new(normal.outputs[0], principled_node.inputs[NORMAL])
        normal.parent = frame

        tex = node_tree.nodes.new('ShaderNodeTexImage')
        node_tree.links.new(tex.outputs[0], normal.inputs[1])
        tex.image = image_provider(obj['normal'], True)
        tex.interpolation = 'Closest'
        tex.parent = frame

        if uv_input:
            node_tree.links.new(uv_input, tex.inputs[0])

    frame.name = name
    frame.label = name
    principled_node.parent = frame

    alpha_socket = None
    if (color_tex.bl_idname == 'ShaderNodeTexImage'):
        alpha_socket = color_tex.outputs[1]
        node_tree.links.new(alpha_socket, principled_node.inputs[ALPHA])

    return (frame, principled_node, alpha_socket)

def create_composite_material(name: str, context: VCAPContext, mat1: str, mat2: str):
    """Create a face layer composite material.

    Args:
        name (str): The name to give the material.
        context (VCAPContext) Context to look for raw materials in.
        mat1 (str): Vcap name of material 1.
        mat2 (str): Vcap name of material 2.

    Returns:
        Material: The material.
    """

    mat = bpy.data.materials.new(name)
    mat.use_nodes = True

    node_tree = mat.node_tree
    node_tree.nodes.remove(node_tree.nodes.get('Principled BSDF'))

    group = bpy.data.node_groups.new(name, 'ShaderNodeTree')
    group_inputs = group.nodes.new('NodeGroupInput')
    group_inputs.location = (-800, 0)
    group.inputs.new(name='UV', type='NodeSocketVector')

    group_outputs = group.nodes.new('NodeGroupOutput')
    group_outputs.location = (300, 0)

    group.outputs.new(name='Shader', type='NodeSocketShader')

    mix = group.nodes.new('ShaderNodeMixShader')
    mix.location = Vector((-150, 0))
    group.links.new(mix.outputs[0], group_outputs.inputs[0])

    mat0 = context.material_groups[mat1]
    mat0_node = group.nodes.new('ShaderNodeGroup')
    mat0_node.node_tree = mat0
    mat0_node.location = (-300, 100)

    mat1 = context.material_groups[mat2]
    mat1_node = group.nodes.new('ShaderNodeGroup')
    mat1_node.node_tree = mat1
    mat1_node.location = (-300, -100)

    uv_node = group.nodes.new('ShaderNodeUVMap')
    uv_node.location = (-450,-100)
    uv_node.uv_map = 'flayer_1' # TODO: no hardcoding

    group.links.new(mat0_node.outputs[0], mix.inputs[1])
    group.links.new(mat1_node.outputs[0], mix.inputs[2])
    group.links.new(mat1_node.outputs[1], mix.inputs[0])
    group.links.new(uv_node.outputs[0], mat1_node.inputs[0])

    group_node = node_tree.nodes.new('ShaderNodeGroup')
    group_node.node_tree = group
    material_output = node_tree.nodes.get('Material Output')
    node_tree.links.new(group_node.outputs[0], material_output.inputs[0])

    context.material_groups[name] = group

    # mat0 = context.raw_materials[mats[0]]
    # frame0, node0, tex = generate_nodes(mat0, node_tree, context, mats[0])

    # frame0.location = frame0.location + Vector((-600, -500))
    # node_tree.links.new(node0.outputs[0], mix.inputs[1])

    # mat1 = context.raw_materials[mats[1]]
    # frame1, node1, color_tex = generate_nodes(mat1, node_tree, context, mats[1], 'flayer_1')

    # frame1.location = frame1.location + Vector((-600, 500))
    # node_tree.links.new(node1.outputs[0], mix.inputs[2])
    # if color_tex:
    #     node_tree.links.new(color_tex.outputs[1], mix.inputs[0])

    # nodes.clear()

    return mat

    # for layer in args:
    #     layer_out = layer.node_tree.nodes.get('Material Output')
    #     base_node = layer_out.inputs[0].links[0].from_node
    #     layer.node_tree.nodes[0].cop
