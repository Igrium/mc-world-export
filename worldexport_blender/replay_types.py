from dataclasses import dataclass, field
from bpy.types import Context, Collection

from .prefabs import PrefabDatablocks

@dataclass
class ReplayImportSettings:
    use_scene_framerate: bool = True
    """
    If set, scale the imported keyframes based on the scene's framerate.
    If unset, assumes 1 frame = 1 tick.
    """

    process_materials: bool = True
    """Run post-processing on the materials to set interpolation mode, etc.
    """

    import_world: bool = True
    """ Import the block world
    """

    merge_vertices: bool = False
    """Run a 'merge by distance' operation of the imported world
    """

    import_entities: bool = True
    """Import entities
    """

    local_root_bone: bool = False
    """Create a root bone in the armature rather than animating the armature's position.
    """

    clean_curves: bool = True
    """Run a 'clean curves' operator on the imported entities.
    """

    interp_spritesheets: bool = True
    """Allow spritesheets to be interpolated. Can cause artifacts on lower sample counts.
    """


@dataclass
class ReplayImportContext:

    replay_root: str

    bl_context: Context
    world_collection: Collection
    entity_collection: Collection

    prefabs: PrefabDatablocks

    settings: ReplayImportSettings = field(default_factory=ReplayImportSettings)

    def fps(self) -> float:
        scene = self.bl_context.scene
        if scene is not None:
            return scene.render.fps / float(scene.render.fps_base)
        else:
            return 24

    def tick_to_frame(self, tick: int) -> float:
        """Return the global scene frame that a replay tick falls on.

        Args:
            tick (int): Replay tick index.

        Returns:
            float: Scene frame. Might be a non-integer.
        """
        scene = self.bl_context.scene
        if self.settings.use_scene_framerate and scene is not None:
            return tick * scene.render.fps / float(scene.render.fps_base * 20)
        else:
            return tick
