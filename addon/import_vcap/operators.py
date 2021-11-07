from os import path

from .format.context import VCAPSettings

from .format import vcap_importer, import_obj
from bpy.types import Context, Operator
from bpy.props import StringProperty, BoolProperty, EnumProperty
from bpy_extras.io_utils import ImportHelper
import bpy


def read_some_data(context, filepath, use_some_setting):
    print("running read_some_data...")
    f = open(filepath, 'r', encoding='utf-8')
    data = f.read()
    f.close()

    # would normally load the data here
    print(data)

    return {'FINISHED'}


# ImportHelper is a helper class, defines filename and
# invoke() function which calls the file selector.


class ImportTestOperator(Operator, ImportHelper):
    bl_idname = "vcap.importtest"
    bl_label = "Import Test"

    # ImportHelper mixin class uses this
    filename_ext = ".txt"

    filter_glob: StringProperty(
        default="*.obj",
        options={'HIDDEN'},
        maxlen=255,  # Max internal buffer length, longer would be clamped.
    )

    def execute(self, context: Context):
        file = self.filepath
        f = open(file, 'rb')
        meshes = import_obj.load(context, f, name=path.basename(file))
        f.close()

        view_layer = context.view_layer
        collection = view_layer.active_layer_collection.collection

        for mesh in meshes:
            obj = bpy.data.objects.new(mesh.name, mesh)
            collection.objects.link(obj)

        # obj.load(context, open(file, 'rb'), path.basename(file))

        return {'FINISHED'}


class ImportVcap(Operator, ImportHelper):
    """This appears in the tooltip of the operator and in the generated docs"""
    bl_idname = "vcap.import_vcap"  # important since its how bpy.ops.import_test.some_data is constructed
    bl_label = "Import VCAP"

    # ImportHelper mixin class uses this
    filename_ext = ".txt"

    filter_glob: StringProperty(
        default="*.vcap",
        options={'HIDDEN'},
        maxlen=255,  # Max internal buffer length, longer would be clamped.
    )

    # List of operator properties, the attributes will be assigned
    # to the class instance from the operator settings before calling.
    use_vertex_colors: BoolProperty(
        name="Use Block Colors",
        description="Whether to load the block colors from the file.",
        default=True,
    )

    merge_verts: BoolProperty(
        name="Merge Vertices",
        description="Whether to merge by distance after the import is complete.",
        default=True,
    )

    def execute(self, context: Context):
        vcap_importer.load(
            self.filepath, context.view_layer.active_layer_collection.collection, context,
            VCAPSettings(use_vertex_colors=self.use_vertex_colors, merge_verts=self.merge_verts))
        return {'FINISHED'}


# Only needed if you want to add into a dynamic menu
def menu_func_import(self, context):
    self.layout.operator(ImportVcap.bl_idname,
                         text="Voxel Capture (.vcap)")

# Only needed if you want to add into a dynamic menu
def menu_func_import2(self, context):
    self.layout.operator(ImportTestOperator.bl_idname,
                         text="Test OBJ (.obj)")



def register():
    bpy.utils.register_class(ImportVcap)
    bpy.types.TOPBAR_MT_file_import.append(menu_func_import)
    # bpy.utils.register_class(ImportTestOperator)
    # bpy.types.TOPBAR_MT_file_import.append(menu_func_import2)


def unregister():
    bpy.utils.unregister_class(ImportVcap)
    bpy.types.TOPBAR_MT_file_import.remove(menu_func_import)
    # bpy.utils.unregister_class(ImportTestOperator)
    # bpy.types.TOPBAR_MT_file_import.remove(menu_func_import2)
