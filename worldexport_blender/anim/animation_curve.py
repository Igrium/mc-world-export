import struct
import itertools

from typing import BinaryIO, Callable, Protocol
from bpy.types import Action
from ..replay_importer import ReplayImportContext

class AnimationCurve:
    tick_offset: int = 0
    
    pos_x: list[float] = []
    pos_y: list[float] = []
    pos_z: list[float] = []
    
    rot_w: list[float] = []
    rot_x: list[float] = []
    rot_y: list[float] = []
    rot_z: list[float] = []
    
    scale_x: list[float] = []
    scale_y: list[float] = []
    scale_z: list[float] = []
    
    def read(self, f: BinaryIO):
        self.tick_offset = struct.unpack('>i', f.read(4))[0]
        length: int = struct.unpack('>i', f.read(4))[0]
        
        _read_curve(self.pos_x, f, length)
        _read_curve(self.pos_y, f, length)
        _read_curve(self.pos_z, f, length)
        
        _read_curve(self.rot_w, f, length)
        _read_curve(self.rot_x, f, length)
        _read_curve(self.rot_y, f, length)
        _read_curve(self.rot_z, f, length)
        
        _read_curve(self.scale_x, f, length)
        _read_curve(self.scale_y, f, length)
        _read_curve(self.scale_z, f, length)
    
    def apply(self, action: Action, base_datapath: str, context: ReplayImportContext,
              pos_transform: Callable[[float, float, float], tuple[float, float, float]] | None = None):
        """Apply this replay animation curve to an actual animation.

        Args:
            action (Action): Action to add to.
            datapath (str): Data path of bone to keyframe.
            context (ReplayImportContext): The replay import context
            pos_transform (Callable[[float, float, float], tuple[float, float, float]] | None, optional): Apply this transform function to the position. Defaults to None.
        """
        
        if (pos_transform != None):
            transformed_x = [0.0] *len(self.pos_x)
            transformed_y = [0.0] *len(self.pos_y)
            transformed_z = [0.0] *len(self.pos_z)
            
            for i in range(0, len(self.pos_x)):
                x, y, z = pos_transform(self.pos_x[i], self.pos_y[i], self.pos_z[i])
                transformed_x[i] = x
                transformed_y[i] = y
                transformed_z[i] = y
        else:
            transformed_x = self.pos_x
            transformed_y = self.pos_y
            transformed_z = self.pos_z
        
        pos_path = base_datapath + '.location'
        rot_path = base_datapath + '.rotation_quaternion'
        scale_path = base_datapath + '.scale'
        
        _apply_curve(transformed_x, self.tick_offset, context, action, pos_path, 0)
        _apply_curve(transformed_y, self.tick_offset, context, action, pos_path, 1)
        _apply_curve(transformed_z, self.tick_offset, context, action, pos_path, 2)
        
        _apply_curve(self.rot_w, self.tick_offset, context, action, rot_path, 0)
        _apply_curve(self.rot_x, self.tick_offset, context, action, rot_path, 1)
        _apply_curve(self.rot_y, self.tick_offset, context, action, rot_path, 2)
        _apply_curve(self.rot_z, self.tick_offset, context, action, rot_path, 3)
        
        _apply_curve(self.scale_x, self.tick_offset, context, action, scale_path, 0)
        _apply_curve(self.scale_y, self.tick_offset, context, action, scale_path, 1)
        _apply_curve(self.scale_z, self.tick_offset, context, action, scale_path, 2)
        

def _apply_curve(values: list[float], tick_offset: int, context: ReplayImportContext, action: Action, data_path: str, index: int = 0):
    curve = action.fcurves.new(data_path, index=index)
    keyframe_points = curve.keyframe_points
    
    # Create list of keyframes, with the frame as the first element and the value as the second element
    keyframes = [(
        context.tick_to_frame(tick + tick_offset),
        val
    ) for tick, val in enumerate(values)]
    
    
    keyframe_points.add(len(keyframes))
    keyframe_points.foreach_set('co', itertools.chain.from_iterable(keyframes))
    keyframe_points.foreach_set('interpolation', [1] * len(keyframes))

def _read_curve(curve: list[float], f: BinaryIO, length: int):
    data = f.read(length * 4)
    floats = struct.unpack('>f', data)
    curve.extend(floats)
    