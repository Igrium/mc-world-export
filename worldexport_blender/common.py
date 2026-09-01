"""Small Blender helpers shared across the importer."""

import bpy

from bpy.types import Object


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

def convert_rotation(w: float, x: float, y: float, z: float):
    return (w, *convert_coords(x, y, z))

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
