import struct
from .. import common
from ..anim.animation_curve import AnimationCurve
from typing import BinaryIO

class CapturedEntity:
    """An in-memory representation of a replay entity before it's applied to Blender objects
    """
    
    parents: dict[str, str] = {}
    """A map of model part names and their parent (optional)
    """
    
    curves: list[tuple[str, AnimationCurve]] = []
        
    def read_anim_file(self, f: BinaryIO):
        size: int = struct.unpack('>i', f.read(4))[0]
        for i in range(0, size): 
            name = common.read_utf(f)
            curve = AnimationCurve()
            curve.read(f)
            self.curves.append((name, curve))
            
            
