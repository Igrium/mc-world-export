from enum import Enum, IntEnum
import struct

from typing import BinaryIO, Callable, TypeAlias
from bpy.types import Action
from ..replay_importer import ReplayImportContext
from ..replay_types import CurveLike
    
class CurveFormat(IntEnum):
    NO_DATA = 0
    POS = 1
    POS_ROT = 2
    POS_ROT_SCALE = 3
    
    def num_channels(self):
        if self == CurveFormat.NO_DATA:
            return 0
        elif self == CurveFormat.POS:
            return 3
        elif self == CurveFormat.POS_ROT:
            return 7
        elif self == CurveFormat.POS_ROT_SCALE:
            return 10
        else:
            raise TypeError("Not a valid curve format index: " + str(self))

    
VectorOperator: TypeAlias = Callable[[float, float, float], tuple[float, float, float]]
QuaternionOperator: TypeAlias = Callable[[float, float, float, float], tuple[float, float, float, float]]

class AnimationCurve(CurveLike):
    format: CurveFormat = CurveFormat.POS_ROT_SCALE
    tick_offset = 0
    length = 0
    
    @property
    def end_tick(self):
        return self.tick_offset + self.length
    
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
    
    def has_position(self):
        return self.format == CurveFormat.POS or self.format == CurveFormat.POS_ROT or self.format == CurveFormat.POS_ROT_SCALE
    
    def has_rotation(self):
        return self.format == CurveFormat.POS_ROT or self.format == CurveFormat.POS_ROT_SCALE
    
    def has_scale(self):
        return self.format == CurveFormat.POS_ROT_SCALE
    
    def read(self, f: BinaryIO):
        
        self.format = CurveFormat(struct.unpack('>b', f.read(1))[0])
        self.tick_offset = struct.unpack('>i', f.read(4))[0]
        length: int = struct.unpack('>i', f.read(4))[0]
        self.length = length
        
        def read_channel(curve: list[float]):
            data = f.read(length * 4)
            floats = struct.unpack(f'>{length}f', data)
            curve.extend(floats)
    
        if self.has_position():
            read_channel(self.pos_x)
            read_channel(self.pos_y)
            read_channel(self.pos_z)
            
        if self.has_rotation():
            read_channel(self.rot_w)
            read_channel(self.rot_x)
            read_channel(self.rot_y)
            read_channel(self.rot_z)
        
        if self.has_scale():
            read_channel(self.scale_x)
            read_channel(self.scale_y)
            read_channel(self.scale_z)
    
    def to_key_arrays(self, base_datapath: str, context: ReplayImportContext,
                      pos_transform: VectorOperator | None = None,
                      rot_transform: QuaternionOperator | None = None,
                      scale_transform: VectorOperator | None = None):
        """Convert to collection of flattened keyframe arrays suitable for `keyframe_points.foreach_set`

        Args:
            base_datapath (Data path prefix): prefix the curve datapath with this.
            context (ReplayImportContext): Import context

        Returns:
            _type_: A dictionary of datpaths and their curve arrays
        """
        
        pos_xp, pos_yp, pos_zp = _apply_vector_operator(self.pos_x, self.pos_y, self.pos_z, pos_transform)
        rot_wp, rot_xp, rot_yp, rot_zp = _apply_quat_operator(self.rot_w, self.rot_x, self.rot_y, self.rot_z, rot_transform)
        scale_xp, scale_yp, scale_zp = _apply_vector_operator(self.scale_x, self.scale_y, self.scale_z, scale_transform)
        
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
        
        if self.has_position():
            arrays[(pos_path, 0)] = to_key_array(pos_xp)
            arrays[(pos_path, 1)] = to_key_array(pos_yp)
            arrays[(pos_path, 2)] = to_key_array(pos_zp)
        
        if self.has_rotation():
            arrays[(rot_path, 0)] = to_key_array(rot_wp)
            arrays[(rot_path, 1)] = to_key_array(rot_xp)
            arrays[(rot_path, 2)] = to_key_array(rot_yp)
            arrays[(rot_path, 3)] = to_key_array(rot_zp)
            
        if self.has_scale():
            arrays[(scale_path, 0)] = to_key_array(scale_xp)
            arrays[(scale_path, 1)] = to_key_array(scale_yp)
            arrays[(scale_path, 2)] = to_key_array(scale_zp)
        
        return arrays
    
def _apply_vector_operator(x: list[float], y: list[float], z: list[float], operator: VectorOperator | None):
    if operator:
        xp = [0.0] * len(x)
        yp = [0.0] * len(y)
        zp = [0.0] * len(z)
        
        for i in range(0, len(x)):
            xp[i], yp[i], zp[i] = operator(x[i], y[i], z[i])
        return (xp, yp, zp)
    else:
        return (x, y, z)
    
def _apply_quat_operator(w: list[float], x: list[float], y: list[float], z: list[float], operator: QuaternionOperator | None):
    if operator:
        dw = [0.0] * len(w)
        dx = [0.0] * len(x)
        dy = [0.0] * len(y)
        dz = [0.0] * len(z)
        
        for i in range(0, len(x)):
            dw[i], dx[i], dy[i], dz[i] = operator(w[i], x[i], y[i], z[i])
        return (dw, dx, dy, dz)
    else:
        return (w, x, y, z)
    