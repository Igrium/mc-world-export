import struct
import itertools

from typing import BinaryIO, Callable
from bpy.types import Action
from ..replay_importer import ReplayImportContext


class AnimationCurve:
    tick_offset: int = 0
    
    pos_x: list[float]
    pos_y: list[float]
    pos_z: list[float]
    
    rot_w: list[float]
    rot_x: list[float]
    rot_y: list[float]
    rot_z: list[float]
    
    scale_x: list[float]
    scale_y: list[float]
    scale_z: list[float]
    
    def __init__(self) -> None:
        self.pos_x = []
        self.pos_y = []
        self.pos_z = []
        
        self.rot_w = []
        self.rot_x = []
        self.rot_y = []
        self.rot_z = []
        
        self.scale_x = []
        self.scale_y = []
        self.scale_z = []
    
    def read(self, f: BinaryIO):
        self.tick_offset = struct.unpack('>i', f.read(4))[0]
        length: int = struct.unpack('>i', f.read(4))[0]
        
        def read_curve(curve: list[float]):
            data = f.read(length * 4)
            floats = struct.unpack(f'>{length}f', data)
            curve.extend(floats)
    
        
        read_curve(self.pos_x)
        read_curve(self.pos_y)
        read_curve(self.pos_z,)
        
        read_curve(self.rot_w,)
        read_curve(self.rot_x,)
        read_curve(self.rot_y,)
        read_curve(self.rot_z,)
        
        read_curve(self.scale_x,)
        read_curve(self.scale_y,)
        read_curve(self.scale_z,)
    
    def to_key_arrays(self, base_datapath: str, context: ReplayImportContext,
                      pos_transform: Callable[[float, float, float], tuple[float, float, float]] | None = None):
        """Convert to collection of flattened keyframe arrays suitable for `keyframe_points.foreach_set`

        Args:
            base_datapath (Data path prefix): prefix the curve datapath with this.
            context (ReplayImportContext): Import context
            pos_transform (Callable[[float, float, float], tuple[float, float, float]] | None, optional): A function to apply to all location values. Defaults to None.

        Returns:
            _type_: A dictionary of datpaths and their curve arrays
        """
        
        if (pos_transform != None):
            transformed_x = [0.0] * len(self.pos_x)
            transformed_y = [0.0] * len(self.pos_y)
            transformed_z = [0.0] * len(self.pos_z)
            
            for i in range(0, len(self.pos_x)):
                x, y, z = pos_transform(self.pos_x[i], self.pos_y[i], self.pos_z[i])
                transformed_x[i] = x
                transformed_y[i] = y
                transformed_z[i] = z
        else:
            transformed_x = self.pos_x
            transformed_y = self.pos_y
            transformed_z = self.pos_z
        
        if base_datapath and not base_datapath.endswith('.'):
            base_datapath += '.'
            
        pos_path = base_datapath + 'location'
        rot_path = base_datapath + 'rotation_quaternion'
        scale_path = base_datapath + 'scale'
        
        def to_key_array(values: list[float]):
            # Create list of keyframes, with the frame as the first element and the value as the second element
            keyframes = [(
                context.tick_to_frame(tick + self.tick_offset),
                val
            ) for tick, val in enumerate(values)]
            
            return [item for tuple in keyframes for item in tuple]


        arrays: dict[tuple[str, int], list[float]] = {}
        
        arrays[(pos_path, 0)] = to_key_array(transformed_x)
        arrays[(pos_path, 1)] = to_key_array(transformed_y)
        arrays[(pos_path, 2)] = to_key_array(transformed_z)
        
        arrays[(rot_path, 0)] = to_key_array(self.rot_w)
        arrays[(rot_path, 1)] = to_key_array(self.rot_x)
        arrays[(rot_path, 2)] = to_key_array(self.rot_y)
        arrays[(rot_path, 3)] = to_key_array(self.rot_z)
        
        arrays[(scale_path, 0)] = to_key_array(self.scale_x)
        arrays[(scale_path, 1)] = to_key_array(self.scale_y)
        arrays[(scale_path, 2)] = to_key_array(self.scale_z)
        
        return arrays
