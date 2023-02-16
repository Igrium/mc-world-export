import math
import xml.etree.ElementTree as ET

from bpy.types import Camera, Context, Object
from mathutils import Vector

from . import data


def write(file, obj: Object, context: Context):
    """Write a camera animation to an XML file

    Args:
        file (str or IO): File name or handle to write
        obj (Object): Camera to use.
        context (Context): Blender context.
    """
    tree = ET.ElementTree(write_data(obj, context))
    tree.write(file)

def write_data(obj: Object, context: Context):
    """Write the XML data of a camera animation

    Args:
        obj (Object): Camera to use.
        context (Context): Blender context.

    Raises:
        Exception: If the object is not a camera.
    """
    if obj.type != 'CAMERA':
        raise Exception("Selected object must be a camera!")
    camera: Camera = obj.data

    root = ET.Element("animation")
    root.set('fps', str(context.scene.render.fps / context.scene.render.fps_base))
    root.set('fov', str(math.degrees(camera.angle)))

    offset: Vector = Vector(data.vcap_offset_mc(context.scene))
    offset *= -1
    if offset.length_squared > 0:
        root.set('offset', '[' + str(offset[0]) + ', ' + str(offset[1]) + ', ' + str(offset[2]) + ']')


    root.append(channel('location', 3))
    quaternion = obj.rotation_mode == 'QUATERNION'

    if quaternion:
        root.append(channel('rotation_quat', 4))
    else:
        root.append(channel('rotation_euler', 3))

    # TODO: Check for keyframes
    animate_fov = True

    if animate_fov:
        root.append(channel('fov', 1))
    
    builder = []
    for i in range(0, context.scene.frame_end + 1):
        frame = []
        context.scene.frame_set(i)
        location = [obj.location[0], obj.location[2], -obj.location[1]]
        frame.extend([str(x) for x in location])

        if quaternion:
            brot = obj.rotation_quaternion
            rotation = [brot[0], brot[1], brot[3], -brot[2]]
            frame.extend([str(x) for x in rotation])
        else:
            brot = obj.rotation_euler
            rotation = [brot[0], brot[2], -brot[1]]
            frame.extend([str(x) for x in rotation])
        
        if animate_fov:
            frame.append(str(math.degrees(camera.angle_y)))
        
        builder.append(' '.join(frame))
    
    anim_data = ET.Element("anim_data")
    anim_data.text = '\n'.join(builder)
    root.append(anim_data)

    return root



def channel(name: str, size: int):
    element = ET.Element('channel')
    element.set('name', name)
    element.set('size', str(size))
    return element