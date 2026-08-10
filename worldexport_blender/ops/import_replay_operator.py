import bpy

from bpy.props import StringProperty
from bpy.types import Operator
# ImportHelper is a helper class, defines filename and
# invoke() function which calls the file selector.
from bpy_extras.io_utils import ImportHelper

from .. import replay_importer
from ..replay_types import ReplayImportSettings


class WORLDEXPORT_OT_import_replay(Operator, ImportHelper): # type: ignore
    """This appears in the tooltip of the operator and in the generated docs"""
    bl_idname = "worldexport.import_replay"
    bl_label = "Import Minecraft Replay"

    # ImportHelper mix-in class uses this.
    filename_ext = ".replay"

    filter_glob: StringProperty(
        default="*.replay",
        options={'HIDDEN'},
        maxlen=255,  # Max internal buffer length, longer would be clamped.
    )

    def execute(self, context): # type: ignore
        replay_importer.import_replay(self.filepath, ReplayImportSettings(), context) # type: ignore
        return {'FINISHED'}


# Only needed if you want to add into a dynamic menu.
def menu_func_import(self, context):
    self.layout.operator(WORLDEXPORT_OT_import_replay.bl_idname, text="Minecraft Replay")


# Register and add to the "file selector" menu (required to use F3 search "Text Import Operator" for quick access).
def register():
    bpy.utils.register_class(WORLDEXPORT_OT_import_replay)
    bpy.types.TOPBAR_MT_file_import.append(menu_func_import)


def unregister():
    bpy.utils.unregister_class(WORLDEXPORT_OT_import_replay)
    bpy.types.TOPBAR_MT_file_import.remove(menu_func_import)
