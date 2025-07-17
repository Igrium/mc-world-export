from dataclasses import dataclass
from bpy.types import Context, Collection

@dataclass
class ReplayImportSettings:
    use_scene_framerate: bool = True
    """
    If set, scale the imported keyframes based on the scene's framerate.
    If unset, assumes 1 frame = 1 tick.
    """
    
    local_root_bone: bool = False
    """Create a root bone in the armature rather than animating the armature's position.
    """
    
    clean_curves: bool = True
    """Run a 'clean curves' operator on the imported entities.
    """

@dataclass
class ReplayImportContext:
    
    context: Context
    world_collection: Collection
    entity_collection: Collection
    
    
    settings: ReplayImportSettings = ReplayImportSettings()
    
    def tick_to_frame(self, tick: int) -> float:
        """Return the global scene frame that a replay tick falls on.

        Args:
            tick (int): Replay tick index.

        Returns:
            float: Scene frame. Might be a non-integer.
        """
        
        scene = self.context.scene
        if self.settings.use_scene_framerate and scene != None:
            return tick * (scene.render.fps / scene.render.fps_base)
        else:
            return tick
