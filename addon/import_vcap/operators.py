from os import name, path

from .vcap.context import VCAPSettings

from .vcap import vcap_importer, import_obj
from .replay import entity, replay_file
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

        with open(file, 'rb') as f:
            meshes = import_obj.load(context, f, name=path.basename(file))

        view_layer = context.view_layer
        collection = view_layer.active_layer_collection.collection

        for mesh in meshes:
            obj = bpy.data.objects.new(mesh.name, mesh)
            collection.objects.link(obj)

        # obj.load(context, open(file, 'rb'), path.basename(file))

        return {'FINISHED'}


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

class ImportReplayOperator(Operator, ImportHelper):
    bl_idname = "vcap.importreplay"
    bl_label = "Import Minecraft Replay"

    # ImportHelper mixin class uses this
    filename_ext = ".txt"

    filter_glob: StringProperty(
        default="*.replay",
        options={'HIDDEN'},
        maxlen=255,  # Max internal buffer length, longer would be clamped.
    )
    
    import_world: BoolProperty(
        name="Import World",
        description="Import world blocks (significantly increases import time.)",
        default=True
    )
        
    import_entities: BoolProperty(
        name="Import Entities",
        description="Import Minecraft entities and their animations.",
        default=True
    )

    separate_parts: BoolProperty(
        name="Separate Entity Parts",
        description="Import every ModelPart in an entity as a seperate object. Only works on multipart entities.",
        default=False
    )
    
    use_vertex_colors: BoolProperty(
        name="Use Block Colors",
        description="Import block colors from the file (grass tint, etc). If unchecked, world may look very grey.",
        default=True,
    )
    
    merge_verts: BoolProperty(
        name="Merge Vertices",
        description="Run a 'merge by distance' operation on the imported world. May exhibit unpredictable behavior.",
        default=False
    )

    hide_entities: BoolProperty(
        name="Auto-hide Entities",
        description="Hide entities before they've been spawned and after they've been killed.",
        default=True
    )

    def execute(self, context: Context):
        settings = replay_file.ReplaySettings(
            world=self.import_world,
            entities=self.import_entities,
            separate_parts=self.separate_parts,
            hide_entities=self.hide_entities,

            vcap_settings=VCAPSettings(
                use_vertex_colors=self.use_vertex_colors,
                merge_verts=self.merge_verts
            )
        )
        replay_file.load_replay(self.filepath, context, context.scene.collection, self, settings=settings)
        return {'FINISHED'}

# Only needed if you want to add into a dynamic menu
def menu_func_import(self, context):
    self.layout.operator(ImportVcap.bl_idname,
                         text="Voxel Capture (.vcap)")

# Only needed if you want to add into a dynamic menu
def menu_func_import2(self, context):
    self.layout.operator(ImportEntityOperator.bl_idname,
                         text="Test Replay Entity (.xml)")

def menu_func_replay(self, context):
    self.layout.operator(ImportReplayOperator.bl_idname,
                         text="Minecraft Replay File (.replay)")


def register():
    bpy.utils.register_class(ImportVcap)
    bpy.utils.register_class(ImportEntityOperator)
    bpy.utils.register_class(ImportReplayOperator)
    bpy.types.TOPBAR_MT_file_import.append(menu_func_import)
    # bpy.utils.register_class(ImportTestOperator)
    bpy.types.TOPBAR_MT_file_import.append(menu_func_import2)
    bpy.types.TOPBAR_MT_file_import.append(menu_func_replay)


def unregister():
    bpy.utils.unregister_class(ImportVcap)
    bpy.utils.unregister_class(ImportEntityOperator)
    bpy.utils.unregister_class(ImportReplayOperator)
    bpy.types.TOPBAR_MT_file_import.remove(menu_func_import)
    # bpy.utils.unregister_class(ImportTestOperator)
    bpy.types.TOPBAR_MT_file_import.remove(menu_func_import2)
    bpy.types.TOPBAR_MT_file_import.remove(menu_func_replay)
