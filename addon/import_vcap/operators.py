from os import name, path

from .vcap.context import VCAPSettings

from .vcap import vcap_importer, import_obj
from .replay import entity, replay_file
from . import camera_export
from bpy.types import Context, Operator, Object
from bpy.props import StringProperty, BoolProperty, EnumProperty
from bpy_extras.io_utils import ImportHelper, ExportHelper
import bpy

# ImportHelper is a helper class, defines filename and
# invoke() function which calls the file selector.

class ImportVcap(Operator, ImportHelper):
    """Import a Voxel Capture file. Used internally in the replay importer."""
    bl_idname = "vcap.import_vcap"
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
            self.filepath,
            context.view_layer.active_layer_collection.collection,
            context,
            name=path.basename(self.filepath),
            settings=VCAPSettings(use_vertex_colors=self.use_vertex_colors,
                                  merge_verts=self.merge_verts))
        return {'FINISHED'}


class ImportEntityOperator(Operator, ImportHelper):
    """Import a single replay entity. Generally only used for testing."""
    bl_idname = "vcap.importentity"
    bl_label = "Import Replay Entity"

    # ImportHelper mixin class uses this
    filename_ext = ".txt"

    filter_glob: StringProperty(
        default="*.xml",
        options={'HIDDEN'},
        maxlen=255,  # Max internal buffer length, longer would be clamped.
    )

    def execute(self, context: Context):
        with open(self.filepath) as file:
            entity.load_entity(file, context, context.scene.collection)
        return {'FINISHED'}

class ExportCameraXMLOperator(Operator, ExportHelper):
    bl_idname = "vcap.exportcameraxml"
    bl_label = "Export Camera XML"

    filename_ext = ".xml"
    
    def execute(self, context: Context):
        obj: Object = context.active_object
        if (obj == None):
            return self.fail("No camera selected.")
        elif (obj.type != 'CAMERA'):
            return self.fail("Selected object must be a camera.")
        
            
        camera_export.write(self.filepath, obj, context)
        return {'FINISHED'}
    
    def fail(self, message: str):
        self.report({'ERROR'}, message)
        return {'CANCELLED'}


# Only needed if you want to add into a dynamic menu
def menu_func_import(self, context):
    self.layout.operator(ImportVcap.bl_idname,
                         text="Voxel Capture (.vcap)")

# Only needed if you want to add into a dynamic menu
def menu_func_import2(self, context):
    self.layout.operator(ImportEntityOperator.bl_idname,
                         text="Test Replay Entity (.xml)")

def menu_func_camera_xml(self, context):
    self.layout.operator(ExportCameraXMLOperator.bl_idname,
                         text="Camera Animation (.xml)")

def register():
    bpy.utils.register_class(ImportVcap)
    bpy.utils.register_class(ImportEntityOperator)
    bpy.utils.register_class(ExportCameraXMLOperator)
    bpy.types.TOPBAR_MT_file_import.append(menu_func_import)
    # bpy.types.TOPBAR_MT_file_import.append(menu_func_import2)
    bpy.types.TOPBAR_MT_file_export.append(menu_func_camera_xml)


def unregister():
    bpy.utils.unregister_class(ImportVcap)
    bpy.utils.unregister_class(ImportEntityOperator)
    bpy.utils.unregister_class(ExportCameraXMLOperator)
    bpy.types.TOPBAR_MT_file_import.remove(menu_func_import)
    # bpy.types.TOPBAR_MT_file_import.remove(menu_func_import2)
    bpy.types.TOPBAR_MT_file_export.remove(menu_func_camera_xml)
