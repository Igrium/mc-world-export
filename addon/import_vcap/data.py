import bpy
from bpy.types import Scene

def register():
    bpy.types.Scene.vcap_offset = bpy.props.IntVectorProperty(
        name="Vcap Offset", default=[0,0,0],
        description="The relationship between Minecraft coordinates and Blender coordinates (disragarding space conversions)")

def unregister():
    del bpy.types.Scene.vcap_offset

def vcap_offset(scene: Scene) -> list[int]:
    """Get the current vcap offset of a scene.

    Args:
        scene (Scene): Scene to use.

    Returns:
        list[float]: The current offset, in Blender's coordinate space.
    """
    return scene.vcap_offset

def vcap_offset_mc(scene: Scene):
    """Get the current vcap offset of a scene in MC coordinates.

    Args:
        scene (Scene): Scene to use.

    Returns:
        list[float]: The current offset, in Minecraft's coordinate space.
    """
    offset = vcap_offset(scene)
    return [offset[0], offset[2], -offset[1]]