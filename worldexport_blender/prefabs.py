import bpy
from os import path

from pprint import pprint
import inspect

from bpy.types import NodeTree
from .replay_types import PrefabDatablocks

prefab_file = path.join(path.dirname(path.realpath(__file__)), 'prefabs.blend')

def load(filepath: str):
    with bpy.data.libraries.load(filepath, assets_only=True) as (data_from, data_to):
        data_to.node_groups = ['MulVertexColor']

    datablocks: PrefabDatablocks = PrefabDatablocks()
    datablocks.mul_vertex_color = data_to.node_groups[0] # type: ignore
    datablocks.mul_vertex_color.asset_clear()
    return datablocks