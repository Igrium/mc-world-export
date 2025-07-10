import bpy

from bpy.types import Context, Operator, Panel, StringProperty, BoolProperty, EnumProperty
from bpy_extras.io_utils import ImportHelper


def read_some_data(context, filepath, use_some_setting):
    print("running read_some_data...")
    f = open(filepath, "r", encoding="utf-8")
    data = f.read()
    f.close()

    # would normally load the data here
    print(data)

    return {'FINISHED'}


# ImportHelper is a helper class, defines filename and
# invoke() function which calls the file selector.
from bpy_extras.io_utils import ImportHelper
from bpy.props import StringProperty, BoolProperty, EnumProperty
from bpy.types import Operator


class ImportReplay(Operator, ImportHelper):
    """This appears in the tooltip of the operator and in the generated docs"""
    bl_idname = "worldexport.import_replay"
    bl_label = "Import Minecraft Replay"

    # ImportHelper mix-in class uses this.
    filename_ext = ".zip"

    filter_glob: StringProperty(
        default="*.zip",
        options={'HIDDEN'},
        maxlen=255,  # Max internal buffer length, longer would be clamped.
    )

    # List of operator properties, the attributes will be assigned
    # to the class instance from the operator settings before calling.
    use_setting: BoolProperty(
        name="Example Boolean",
        description="Example Tooltip",
        default=True,
    )

    type: EnumProperty(
        name="Example Enum",
        description="Choose between two items",
        items=(
            ('OPT_A', "First Option", "Description one"),
            ('OPT_B', "Second Option", "Description two"),
        ),
        default='OPT_A',
    )

    def execute(self, context):
        return read_some_data(context, self.filepath, self.use_setting)


# Only needed if you want to add into a dynamic menu.
def menu_func_import(self, context):
    self.layout.operator(ImportReplay.bl_idname, text="Minecraft Replay")


# Register and add to the "file selector" menu (required to use F3 search "Text Import Operator" for quick access).
def register():
    bpy.utils.register_class(ImportReplay)
    bpy.types.TOPBAR_MT_file_import.append(menu_func_import)


def unregister():
    bpy.utils.unregister_class(ImportReplay)
    bpy.types.TOPBAR_MT_file_import.remove(menu_func_import)