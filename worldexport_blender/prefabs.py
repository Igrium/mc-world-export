import bpy
from os import path

from pprint import pprint
import inspect

from bpy.types import NodeTree
from .replay_types import PrefabDatablocks

prefab_file = path.join(path.dirname(path.realpath(__file__)), 'prefabs.blend')

def load(filepath: str):
    with bpy.data.libraries.load(filepath, assets_only=True) as (data_from, data_to):
        data_to.node_groups = ['MulVertexColor', 'GrassTintPre', 'GrassTintPost']

    datablocks: PrefabDatablocks = PrefabDatablocks()

    datablocks.mul_vertex_color = data_to.node_groups[0] # type: ignore
    datablocks.mul_vertex_color.asset_clear()

    datablocks.grass_tint_pre = data_to.node_groups[1] # type: ignore
    datablocks.grass_tint_pre.asset_clear()

    datablocks.grass_tint_post = data_to.node_groups[2] # type: ignore
    datablocks.grass_tint_post.asset_clear()

    return datablocks