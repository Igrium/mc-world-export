import bpy
from os import path

from bpy.types import ShaderNodeTree

prefab_file = path.join(path.dirname(path.realpath(__file__)), 'prefabs.blend')


class PrefabDatablocks:
    mul_vertex_color: ShaderNodeTree
    grass_tint_pre: ShaderNodeTree
    grass_tint_post: ShaderNodeTree
    glint: ShaderNodeTree

    def clear_unused(self):
        """Remove any prefab node group that nothing ended up referencing.

        Repeats until nothing more can be removed: the prefabs nest (GrassTintPost
        contains a MulVertexColor node), so dropping one group can be what finally
        orphans another.
        """
        remaining = [self.mul_vertex_color, self.grass_tint_pre, self.grass_tint_post, self.glint]

        removed_any = True
        while removed_any:
            removed_any = False
            for group in list(remaining):
                if group and group.users == 0:
                    bpy.data.node_groups.remove(group)
                    remaining.remove(group)
                    removed_any = True


def load(filepath: str):
    with bpy.data.libraries.load(filepath, assets_only=True) as (data_from, data_to):
        data_to.node_groups = ['MulVertexColor', 'GrassTintPre', 'GrassTintPost', 'Glint']

    datablocks: PrefabDatablocks = PrefabDatablocks()

    datablocks.mul_vertex_color = data_to.node_groups[0] # type: ignore
    datablocks.mul_vertex_color.asset_clear()

    datablocks.grass_tint_pre = data_to.node_groups[1] # type: ignore
    datablocks.grass_tint_pre.asset_clear()

    datablocks.grass_tint_post = data_to.node_groups[2] # type: ignore
    datablocks.grass_tint_post.asset_clear()

    datablocks.glint = data_to.node_groups[3] # type: ignore
    datablocks.glint.asset_clear()

    return datablocks
