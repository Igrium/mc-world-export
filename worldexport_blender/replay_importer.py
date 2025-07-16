from dataclasses import dataclass

@dataclass
class ReplayImportSettings:
    use_scene_framerate: bool = True
    """
    If set, scale the imported keyframes based on the scene's framerate.
    If unset, assumes 1 frame = 1 tick.
    """
    
    clean_curves: bool = True
    """Run a 'clean curves' operator on the imported entities.
    """