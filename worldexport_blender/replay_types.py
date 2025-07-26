from dataclasses import dataclass, field
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
    
    process_materials: bool = True
    """Run post-processing on the materials to set interpolation mode, etc.
    """

@dataclass
class ReplayImportContext:
    
    replay_root: str
    
    bl_context: Context
    world_collection: Collection
    entity_collection: Collection
    
    settings: ReplayImportSettings = field(default_factory=lambda: ReplayImportSettings())
    
    def tick_to_frame(self, tick: int) -> float:
        """Return the global scene frame that a replay tick falls on.

        Args:
            tick (int): Replay tick index.

        Returns:
            float: Scene frame. Might be a non-integer.
        """
        return tick
        
        scene = self.bl_context.scene
        if self.settings.use_scene_framerate and scene != None:
            return tick * (float(scene.render.fps) / scene.render.fps_base)
        else:
            return tick