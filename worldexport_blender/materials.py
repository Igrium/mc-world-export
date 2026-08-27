import json
import os
from typing import Any, Iterable, TypedDict, cast

from bpy.types import (
    Material,
    ShaderNodeBsdfPrincipled,
    ShaderNodeGroup,
    ShaderNodeTexImage,
    ShaderNodeTree,
)

from .replay_types import ReplayImportContext


def process_materials(mats: Iterable[Material], context: ReplayImportContext):
    custom_props: dict[str, dict] = {}
    for root, dirs, files in os.walk(context.replay_root):
        for file in files:
            if not file.endswith('.mtl.json'): continue

            with open(os.path.join(context.replay_root, file), 'r') as f:
                json_data: dict[str, dict] = json.load(f)

            for mat_name, mat_props in json_data.items():
                custom_props.setdefault(mat_name, {}).update(mat_props)

    for mat in mats:
        process_material(mat, custom_props.get(mat.name), context)

def process_material(mat: Material, custom_props: dict[str, Any] | None, context: ReplayImportContext):
    node_tree = mat.node_tree
    if not node_tree: return
    
    for node in node_tree.nodes:
        if node.type == 'TEX_IMAGE':
            cast(ShaderNodeTexImage, node).interpolation = 'Closest'
    
    if custom_props:
        apply_custom_props(mat, custom_props, context)

def apply_custom_props(mat: Material, custom_props: dict[str, Any], context: ReplayImportContext):
    if custom_props.get('renderMode') == 'blended':
        mat.surface_render_method = 'BLENDED'
    else:
        mat.surface_render_method = 'DITHERED'
    
    node_tree = mat.node_tree
    if not node_tree: return
    
    principled_node = node_tree.nodes['Principled BSDF']
    
    if not isinstance(principled_node, ShaderNodeBsdfPrincipled):
        print('Unable to find principled node!')
        return
    
    grass_overlay = cast(tuple[float, float], (custom_props.get('grassOverlayU', 0), custom_props.get('grassOverlayV', 0)))

    principled_inputs = principled_node.inputs
    if not principled_inputs: return

    if grass_overlay[0] or grass_overlay[1]:
        apply_grass_overlay(node_tree, principled_node, grass_overlay, context)

    elif custom_props.get('vertexTint'): # Grass overlay handles its own tint
        vert_tint_node = cast(ShaderNodeGroup, node_tree.nodes.new('ShaderNodeGroup'))
        vert_tint_node.node_tree = context.prefabs.mul_vertex_color
        vert_tint_node.location = (-160.0, 300.0)

        color_socket = principled_node.inputs['Base Color']  # pyright: ignore[reportOptionalSubscript]
        color_links = color_socket.links
        from_socket = color_links[0].from_socket if color_links else None
        if not from_socket:
            return

        node_tree.links.new(from_socket, vert_tint_node.inputs[0])
        node_tree.links.new(vert_tint_node.outputs[0], color_socket)

    if custom_props.get('glint'):
        apply_glint(node_tree, principled_node, context)
    
    spritesheet = custom_props.get('spritesheet')
    if spritesheet:
        apply_spritesheet(node_tree, spritesheet, context)

    render_mode = custom_props.get('renderMode')
    if isinstance(render_mode, str) and render_mode.lower() == 'blended':
        mat.surface_render_method = 'BLENDED'


def apply_grass_overlay(node_tree: ShaderNodeTree, principled: ShaderNodeBsdfPrincipled, overlay: tuple[float, float], context: ReplayImportContext):
    nodes = node_tree.nodes
    if not principled.inputs: return
    color_socket = principled.inputs['Base Color']
    color_links = color_socket.links
    if (not color_links or len(color_links) == 0):
        print('No color link found')
        return

    base_tex = color_links[0].from_node
    if (not isinstance(base_tex, ShaderNodeTexImage)):
        print('image texture must be plugged into image texture')
        return
    base_tex.location = (-580, 480)


    overlay_tex = cast(ShaderNodeTexImage, nodes.new('ShaderNodeTexImage'))
    overlay_tex.image           = base_tex.image
    overlay_tex.interpolation   = base_tex.interpolation
    overlay_tex.extension       = base_tex.extension
    overlay_tex.projection      = base_tex.projection

    overlay_tex.location = (-580, 200)

    pre = cast(ShaderNodeGroup, nodes.new('ShaderNodeGroup'))
    pre.node_tree = context.prefabs.grass_tint_pre
    pre.location = (-900, 260)
    pre.inputs['Offset'].default_value = (overlay[0], -overlay[1], 0)

    post = cast(ShaderNodeGroup, nodes.new('ShaderNodeGroup'))
    post.node_tree = context.prefabs.grass_tint_post
    post.location = (-280, 280)

    links = node_tree.links
    
    links.new(pre.outputs['UV0'], base_tex.inputs['Vector'])  # pyright: ignore[reportOptionalSubscript]
    links.new(pre.outputs['UV1'], overlay_tex.inputs['Vector'])  # pyright: ignore[reportOptionalSubscript]
    links.new(base_tex.outputs['Color'], post.inputs['Color'])  # pyright: ignore[reportOptionalSubscript]
    links.new(overlay_tex.outputs['Color'], post.inputs['Overlay'])  # pyright: ignore[reportOptionalSubscript]
    links.new(overlay_tex.outputs['Alpha'], post.inputs['OverlayMask'])  # pyright: ignore[reportOptionalSubscript]
    links.new(post.outputs['Result'], principled.inputs['Base Color'])

class SpritesheetData(TypedDict):
    frames: int
    frametime: int | None
    interpolate: bool

def apply_glint(node_tree: ShaderNodeTree, principled: ShaderNodeBsdfPrincipled, context: ReplayImportContext):
    nodes = node_tree.nodes
    if not principled.inputs: return

    group = cast(ShaderNodeGroup, nodes.new('ShaderNodeGroup'))
    group.node_tree = context.prefabs.glint
    group.location = (-500, -20)
    group.name = "Glint"

    # It looks good with 200 at 30 fps; remap that to real fps
    divisor = (context.fps() * 200) / 30

    fcurve = group.inputs['W'].driver_add('default_value')
    driver = fcurve.driver
    driver.type = 'SCRIPTED'
    driver.expression = f'frame / {divisor}'

    links = node_tree.links

    links.new(group.outputs['Color'], principled.inputs['Emission Color'])
    links.new(group.outputs['Strength'], principled.inputs['Emission Strength'])

    # Identify the base color output so we can use as input to the node group
    color_socket = principled.inputs['Base Color']  # pyright: ignore[reportOptionalSubscript]
    color_links = color_socket.links
    from_socket = color_links[0].from_socket if color_links else None
    if not from_socket:
        return

    links.new(from_socket, group.inputs['Texture'])

def apply_spritesheet(node_tree: ShaderNodeTree, data: SpritesheetData, context: ReplayImportContext) -> None:
    nodes = node_tree.nodes
    tex_nodes = [cast(ShaderNodeTexImage, node) for node in nodes if node.type == 'TEX_IMAGE']

    interp = (data['interpolate'] or False) and context.settings.interp_spritesheets

    group = cast(ShaderNodeGroup, nodes.new('ShaderNodeGroup'))
    group.node_tree = context.prefabs.spritesheet_interp if interp else context.prefabs.spritesheet
    group.location = (-980, 180)
    group.name = "Spritesheet"
    
    group.inputs['Frames'].default_value = data['frames'] 

    render = context.bl_context.scene.render

    frametime = data.get('frametime') or 1

    fcurve = group.inputs['FrameIdx'].driver_add('default_value')
    driver = fcurve.driver
    driver.type = 'SCRIPTED'
    driver.expression = f'(frame * 20 * {render.fps_base}) / ({render.fps} * {frametime})'

    out_socket = group.outputs['Vector']
    links = node_tree.links

    for node in tex_nodes:
        links.new(out_socket, node.inputs['Vector'])