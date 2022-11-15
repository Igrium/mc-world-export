# Keep the import replay operator in its own file because it's so big.

from os import name, path

import bpy
from bpy.props import BoolProperty, EnumProperty, StringProperty
from bpy.types import Context, Operator, Panel
from bpy_extras.io_utils import ImportHelper

from .replay import replay_file
from .vcap.context import VCAPSettings


class ImportReplayOperator(Operator, ImportHelper):
    bl_idname = "vcap_import.replay"
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
        description="Import world blocks (significantly increases import time)",
        default=True
    )
        
    import_entities: BoolProperty(
        name="Import Entities",
        description="Import Minecraft entities and their animations",
        default=True
    )

    separate_parts: BoolProperty(
        name="Separate Entity Parts",
        description="Import every ModelPart in an entity as a seperate object. Only works on multipart entities.",
        default=False
    )
    
    use_vertex_colors: BoolProperty(
        name="Use Block Colors",
        description="Import block colors from the file (grass tint, etc). If unchecked, world may look very grey",
        default=True,
    )
    
    merge_verts: BoolProperty(
        name="Merge Vertices",
        description="Run a 'merge by distance' operation on the imported world. May exhibit unpredictable behavior",
        default=False
    )

    hide_entities: BoolProperty(
        name="Auto-hide Entities",
        description="Hide entities before they've been spawned and after they've been killed",
        default=True
    )

    automatic_offset: BoolProperty(
        name="Automatic Offset",
        description="Read the world offset from the file, or generate it if it doesn't exist. If unchecked, scene-wide vcap offset is used",
        default=True
    )

    def __error(self, message: str):
        self.report({"ERROR"}, message)
        print("ERROR: "+message)
    
    def __warn(self, message: str):
        self.report({"WARNING"}, message)
        print("WARNING: "+message)
    
    def __feedback(self, message: str):
        self.report({"INFO"}, message)
        print("INFO: "+message)

    def execute(self, context: Context):
        settings = replay_file.ReplaySettings(
            world=self.import_world,
            entities=self.import_entities,
            separate_parts=self.separate_parts,
            hide_entities=self.hide_entities,
            automatic_offset=self.automatic_offset,

            vcap_settings=VCAPSettings(
                use_vertex_colors=self.use_vertex_colors,
                merge_verts=self.merge_verts
            )
        )
        
        handle = replay_file.ExecutionHandle(
            onProgress=lambda val : context.window_manager.progress_update(val),
            onFeedback=self.__feedback,
            onWarning=self.__warn,
            onError=self.__error
        )

        context.window_manager.progress_begin(min=0, max=1)
        replay_file.load_replay(self.filepath, context, context.scene.collection, handle=handle, settings=settings)
        context.window_manager.progress_end()
        return {'FINISHED'}
    
    def draw(self, context):
        pass

class REPLAY_PT_import_replay(Panel):
    bl_space_type = 'FILE_BROWSER'
    bl_region_type = 'TOOL_PROPS'
    bl_label = "Replay"
    bl_parent_id = "FILE_PT_operator"

    @classmethod
    def poll(cls, context: Context):
        sfile = context.space_data
        operator = sfile.active_operator

        return operator.bl_idname == "VCAP_IMPORT_OT_replay"

    def draw(self, context):
        layout = self.layout
        layout.use_property_split = False
        layout.use_property_decorate = False

        operator: ImportReplayOperator = context.space_data.active_operator

        layout.prop(operator, 'automatic_offset')

class REPLAY_PT_import_world(Panel):
    bl_space_type = 'FILE_BROWSER'
    bl_region_type = 'TOOL_PROPS'
    bl_label = "Import World"
    bl_parent_id = "FILE_PT_operator"
    bl_options = {'DEFAULT_CLOSED'}

    @classmethod
    def poll(cls, context: Context):
        sfile = context.space_data
        operator = sfile.active_operator

        return operator.bl_idname == "VCAP_IMPORT_OT_replay"
    
    def draw_header(self, context):
        sfile = context.space_data
        operator = sfile.active_operator

        self.layout.prop(operator, "import_world", text='')
    
    def draw(self, context):
        layout = self.layout
        layout.use_property_split = False
        layout.use_property_decorate = False

        operator: ImportReplayOperator = context.space_data.active_operator
        layout.enabled = operator.import_world

        layout.prop(operator, 'use_vertex_colors')
        layout.prop(operator, 'merge_verts')

class REPLAY_PT_import_entities(Panel):
    bl_space_type = 'FILE_BROWSER'
    bl_region_type = 'TOOL_PROPS'
    bl_label = "Import Entities"
    bl_parent_id = "FILE_PT_operator"
    bl_options = {'DEFAULT_CLOSED'}

    @classmethod
    def poll(cls, context: Context):
        sfile = context.space_data
        operator = sfile.active_operator

        return operator.bl_idname == "VCAP_IMPORT_OT_replay"
    
    def draw_header(self, context):
        sfile = context.space_data
        operator = sfile.active_operator

        self.layout.prop(operator, "import_entities", text='')
    
    def draw(self, context):
        layout = self.layout
        layout.use_property_split = False
        layout.use_property_decorate = False

        operator: ImportReplayOperator = context.space_data.active_operator
        layout.enabled = operator.import_entities

        layout.prop(operator, 'hide_entities')
        layout.prop(operator, 'separate_parts')

def _menu_func_replay(self, context):
    self.layout.operator(ImportReplayOperator.bl_idname,
                         text="Minecraft Replay File (.replay)")

classes = (
    ImportReplayOperator,
    REPLAY_PT_import_replay,
    REPLAY_PT_import_world,
    REPLAY_PT_import_entities
)

def register():
    for cls in classes:
        bpy.utils.register_class(cls)

    bpy.types.TOPBAR_MT_file_import.append(_menu_func_replay)

def unregister():
    for cls in classes:
        bpy.utils.unregister_class(cls)

    bpy.types.TOPBAR_MT_file_import.remove(_menu_func_replay)