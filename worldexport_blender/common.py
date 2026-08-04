import struct
import bpy

from typing import BinaryIO, Literal
from bpy.types import Context, Object
from bpy_extras.io_utils import axis_conversion

from .mesh import import_obj

def load_obj(filepath: str, use_split_objects=True, use_split_groups=False,
               import_vertex_groups=False, validate_meshes=True):
    existing = set(bpy.data.objects)
    bpy.ops.wm.obj_import(filepath=filepath, use_split_objects=use_split_objects, use_split_groups=use_split_groups,
                          import_vertex_groups=import_vertex_groups, validate_meshes=validate_meshes)
    new = set(bpy.data.objects)
    return new - existing

def load_obj_python(context: Context, filepath: str, use_split_objects=False, use_split_groups=False,
               import_vertex_groups=False, validate_meshes=True):
    # bpy.ops.wm.obj_import(filepath=filepath, use_split_objects=use_split_objects, use_split_groups=use_split_groups,
    #                       import_vertex_groups=import_vertex_groups, validate_meshes=validate_meshes)
    return import_obj.load(context, filepath, use_split_objects=use_split_objects, use_split_groups=use_split_groups,
                    use_groups_as_vgroups=import_vertex_groups, global_matrix=axis_conversion('-Z', 'Y').to_4x4())



def create_action(id_data, name: str, id_type: str = 'OBJECT'):
    """Create an action, assign it (and a slot) to the given ID, and return its fcurve collection.

    Blender 4.4+ actions are "slotted": fcurves live in a channelbag belonging to a slot inside a
    strip, and the legacy `Action.fcurves` shortcut was removed in 5.0.
    """
    anim_data = id_data.animation_data or id_data.animation_data_create()
    action = bpy.data.actions.new(name=name)
    anim_data.action = action

    slot = action.slots.new(id_type=id_type, name=id_data.name)
    anim_data.action_slot = slot

    strip = action.layers.new("Layer").strips.new(type='KEYFRAME')
    return action, strip.channelbag(slot, ensure=True).fcurves


def convert_coords(x: float, y: float, z: float):
    return (x, -z, y)

def add_vis_keyframe(obj: Object, visible: bool, frame: float):
    obj.hide_viewport = not visible
    obj.hide_render = not visible
    obj.keyframe_insert('hide_viewport', frame=frame)
    obj.keyframe_insert('hide_render', frame=frame)

    anim_data = obj.animation_data
    if anim_data and anim_data.action and anim_data.action_slot:
        for layer in anim_data.action.layers:
            for strip in layer.strips:
                channelbag = strip.channelbag(anim_data.action_slot)
                if not channelbag:
                    continue
                for fcurve in channelbag.fcurves:
                    if fcurve.data_path in ('hide_viewport', 'hide_render'):
                        for kp in fcurve.keyframe_points:
                            if kp.co.x == frame:
                                kp.interpolation = 'CONSTANT'

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
