"""Generates various node groups needed by the addon.
"""

import bpy
from bpy.types import NodeTree, Node
from bpy.types import NodeSocketFloat, NodeSocketVector, ShaderNodeMath

SPRITESHEET_MAPPING = "Spritesheet Mapping"

def spritesheet_mapping():
    if SPRITESHEET_MAPPING in bpy.data.node_groups:
        return bpy.data.node_groups[SPRITESHEET_MAPPING]

    node_tree: NodeTree = bpy.data.node_groups.new(SPRITESHEET_MAPPING, 'ShaderNodeTree')
    
    # IO
    input_vector = node_tree.interface.new_socket('Vector', in_out='INPUT', socket_type='NodeSocketVector')
    input_vector.hide_value = True
    
    input_frame_count: NodeSocketFloat = node_tree.interface.new_socket("Frame Count", in_out='INPUT', socket_type='NodeSocketFloat')
    input_frame_count.default_value = 1
    input_frame_count.min_value = 1
    
    input_frame: NodeSocketFloat = node_tree.interface.new_socket("Frame", in_out='INPUT', socket_type='NodeSocketFloat')
    
    output_uv: NodeSocketVector = node_tree.interface.new_socket("Sprite UV", in_out='OUTPUT', socket_type='NodeSocketVector')
    output_uv.hide_value = True
    
    # NODE TREE
    group_in = node_tree.nodes.new('NodeGroupInput')
    group_in.location = (-460, 0)
    
    group_out = node_tree.nodes.new('NodeGroupOutput')
    group_out.location = (560, 140)
    
    seperate = node_tree.nodes.new('ShaderNodeSeparateXYZ')
    seperate.location = (-240, 140)
    
    node_tree.links.new(group_in.outputs[0], seperate.inputs[0])
    
    combine = node_tree.nodes.new('ShaderNodeCombineXYZ')
    combine.location = (360, 160)
    
    node_tree.links.new(seperate.outputs[0], combine.inputs[0])
    node_tree.links.new(seperate.outputs[2], combine.inputs[2])
    node_tree.links.new(combine.outputs[0], group_out.inputs[0])
    
    frame_count: ShaderNodeMath = node_tree.nodes.new('ShaderNodeMath')
    frame_count.location = (-240, -40)
    frame_count.name = 'frame_count'
    frame_count.label = "Frame Count"
    frame_count.operation = 'FLOOR'
    
    node_tree.links.new(group_in.outputs[1], frame_count.inputs[0])
    
    current_frame: ShaderNodeMath = node_tree.nodes.new('ShaderNodeMath')
    current_frame.location = (-240, -180)
    current_frame.name = 'current_frame'
    current_frame.label = "Current Frame"
    current_frame.operation = 'FLOOR'
    
    node_tree.links.new(group_in.outputs[2], current_frame.inputs[0])
    
    divide1: ShaderNodeMath = node_tree.nodes.new('ShaderNodeMath')
    divide1.location = (-40, 20)
    divide1.name = 'divide1'
    divide1.operation = 'DIVIDE'
    
    node_tree.links.new(seperate.outputs[1], divide1.inputs[0])
    node_tree.links.new(frame_count.outputs[0], divide1.inputs[1])
    
    divide2: ShaderNodeMath = node_tree.nodes.new('ShaderNodeMath')
    divide2.location = (-40, -140)
    divide2.name = 'divide2'
    divide2.operation = 'DIVIDE'
    
    node_tree.links.new(current_frame.outputs[0], divide2.inputs[0])
    node_tree.links.new(frame_count.outputs[0], divide2.inputs[1])
    
    subtract: ShaderNodeMath = node_tree.nodes.new('ShaderNodeMath')
    subtract.location = (160, -20)
    subtract.name = 'subtract'
    subtract.operation = 'SUBTRACT'
    
    node_tree.links.new(divide1.outputs[0], subtract.inputs[0])
    node_tree.links.new(divide2.outputs[0], subtract.inputs[1])
    
    node_tree.links.new(subtract.outputs[0], combine.inputs[1])
    
    return node_tree

if __name__ == '__main__':
    spritesheet_mapping()