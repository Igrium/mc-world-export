import struct
import bpy

from typing import BinaryIO, Literal
from bpy.types import Context, Object

def import_obj(filepath: str):
    existing = set(bpy.data.objects)
    bpy.ops.wm.obj_import(filepath=filepath)
    return set(bpy.data.objects) - existing

def convert_coords(x: float, y: float, z: float):
    return (x, -z, y)

def tick_to_frame(tick: int, context: Context) -> float:
    scene = context.scene
    if (scene == None):
        return tick
    
    return (tick * scene.render.fps) / scene.render.fps_base

def add_vis_keyframe(obj: Object, visible: bool, frame: float):
    obj.hide_viewport = not visible
    obj.hide_render = not visible
    obj.keyframe_insert('hide_viewport', frame=frame)
    obj.keyframe_insert('hide_render', frame=frame)

def read_int(f: BinaryIO) -> int:
    data: bytes = f.read(4)
    if len(data) != 4:
        raise EOFError("Unexpected end of file while reading int")
    return struct.unpack('>i', data)[0]

def read_float(f: BinaryIO) -> float:
    data: bytes = f.read(4)
    if len(data) != 4:
        raise EOFError("Unexpected end of file while reading float")
    return struct.unpack('>f', data)[0]


def read_utf(f: BinaryIO) -> str:
    length_bytes: bytes = f.read(2)
    if len(length_bytes) != 2:
        raise EOFError("Unexpected end of file while reading UTF length")
    length: int = struct.unpack('>H', length_bytes)[0]
    utf8_bytes: bytes = f.read(length)
    if len(utf8_bytes) != length:
        raise EOFError("Unexpected end of file while reading UTF data")
    return utf8_bytes.decode('utf-8')
