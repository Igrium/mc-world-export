import bpy

from dataclasses import dataclass, field
from abc import abstractmethod
from bpy.types import Context, Collection, ShaderNodeTree
from typing import Protocol, TypeVar, Generic, Iterable, Mapping

class PrefabDatablocks:
    mul_vertex_color: ShaderNodeTree

    def clear_unused(self):
        if self.mul_vertex_color and self.mul_vertex_color.users == 0:
            bpy.data.node_groups.remove(self.mul_vertex_color)

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
    
    prefabs: PrefabDatablocks
    
    settings: ReplayImportSettings = field(default_factory=lambda: ReplayImportSettings())
    
    def tick_to_frame(self, tick: int) -> float:
        """Return the global scene frame that a replay tick falls on.

        Args:
            tick (int): Replay tick index.

        Returns:
            float: Scene frame. Might be a non-integer.
        """
        # return tick
        
        scene = self.bl_context.scene
        if self.settings.use_scene_framerate and scene != None:
            return tick * scene.render.fps / float(scene.render.fps_base * 20)
        else:
            return tick

class CurveLike(Protocol):
    tick_offset: int
    length: int


class AnimationProvider(Protocol):
    @abstractmethod
    def get_curves(self) -> Mapping[str, Iterable[CurveLike]]:
        ...