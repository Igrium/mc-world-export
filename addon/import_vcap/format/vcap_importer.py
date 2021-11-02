from typing import IO
import bpy
from zipfile import ZipFile

from bpy.types import Collection


def load(file: str, collection: Collection, context: bpy.context):
    """Import a vcap file.

    Args:
        filename (str): File to import from.
        collection (Collection): Collection to add to.
        context (bpy.context): Blender context.
    """
    archive = ZipFile(file, 'r')
