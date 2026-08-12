import bpy

from bpy.props import BoolProperty, StringProperty
from bpy.types import Context, Operator, Panel
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

    use_scene_framerate: BoolProperty(
        name="Use Scene Framerate",
        description="Scale the imported keyframes based on the scene's framerate",
        default=True
    )
    
    process_materials: BoolProperty(
        name="Process Materials",
        description="Run post-processing on the materials to set interpolation mode, etc",
        default=True
    )

    import_world: BoolProperty(
        name="World",
        description="Import the block world",
        default=True
    )
    
    # depends on import_world
    merge_vertices: BoolProperty(
        name="Merge Vertices",
        description="Run an additional 'merge by distance' operation on the imported world",
        default=False
    )

    import_entities: BoolProperty(
        name="Entities",
        description="Import entities",
        default=True
    )

    # depends on import_entities
    local_root_bone: BoolProperty(
        name="Local Root Bone",
        description="Create a root bone in the armature rather than animating the armature's position",
        default=False
    )

    # depends on import_entities
    clean_curves: BoolProperty(
        name="Clean Curves",
        description="(not implemented)",
        default=True
    )

    def execute(self, context): # type: ignore
        settings = ReplayImportSettings(
            use_scene_framerate=self.use_scene_framerate,
            process_materials=self.process_materials,
            import_world=self.import_world,
            merge_vertices=self.merge_vertices,
            import_entities=self.import_entities,
            local_root_bone=self.local_root_bone,
            clean_curves=self.clean_curves,
        )
        replay_importer.import_replay(self.filepath, settings, context) # type: ignore
        return {'FINISHED'}

    def draw(self, context): # type: ignore
        layout = self.layout
        layout.use_property_split = False # type: ignore
        layout.use_property_decorate = False # type: ignore

        layout.prop(self, 'use_scene_framerate') # type: ignore
        layout.prop(self, 'process_materials') # type: ignore


class WORLDEXPORT_PT_import_world(Panel):
    bl_space_type = 'FILE_BROWSER'
    bl_region_type = 'TOOL_PROPS'
    bl_label = "World"
    bl_parent_id = "FILE_PT_operator"
    bl_options = {'DEFAULT_CLOSED'}

    @classmethod
    def poll(cls, context: Context): # type: ignore
        sfile = context.space_data
        operator = sfile.active_operator

        return operator.bl_idname == "WORLDEXPORT_OT_import_replay"

    def draw_header(self, context): # type: ignore
        sfile = context.space_data # type: ignore
        operator = sfile.active_operator

        self.layout.prop(operator, "import_world", text='') # type: ignore

    def draw(self, context): # type: ignore
        layout = self.layout
        layout.use_property_split = False # type: ignore
        layout.use_property_decorate = False # type: ignore

        operator: WORLDEXPORT_OT_import_replay = context.space_data.active_operator # type: ignore
        layout.enabled = operator.import_world # type: ignore

        layout.prop(operator, 'merge_vertices') # type: ignore


class WORLDEXPORT_PT_import_entities(Panel):
    bl_space_type = 'FILE_BROWSER'
    bl_region_type = 'TOOL_PROPS'
    bl_label = "Entities"
    bl_parent_id = "FILE_PT_operator"
    bl_options = {'DEFAULT_CLOSED'}

    @classmethod
    def poll(cls, context: Context): # type: ignore
        sfile = context.space_data
        operator = sfile.active_operator

        return operator.bl_idname == "WORLDEXPORT_OT_import_replay"

    def draw_header(self, context): # type: ignore
        sfile = context.space_data # type: ignore
        operator = sfile.active_operator

        self.layout.prop(operator, "import_entities", text='') # type: ignore

    def draw(self, context): # type: ignore
        layout = self.layout
        layout.use_property_split = False # type: ignore
        layout.use_property_decorate = False # type: ignore

        operator: WORLDEXPORT_OT_import_replay = context.space_data.active_operator # type: ignore
        layout.enabled = operator.import_entities # type: ignore

        # unimplemented
        # layout.prop(operator, 'local_root_bone') # type: ignore
        layout.prop(operator, 'clean_curves') # type: ignore


# Only needed if you want to add into a dynamic menu.
def menu_func_import(self, context):
    self.layout.operator(WORLDEXPORT_OT_import_replay.bl_idname, text="Minecraft Replay")


classes = (
    WORLDEXPORT_OT_import_replay,
    WORLDEXPORT_PT_import_world,
    WORLDEXPORT_PT_import_entities,
)


# Register and add to the "file selector" menu (required to use F3 search "Text Import Operator" for quick access).
def register():
    for cls in classes:
        bpy.utils.register_class(cls)
    bpy.types.TOPBAR_MT_file_import.append(menu_func_import) #type: ignore


def unregister():
    for cls in classes:
        bpy.utils.unregister_class(cls)
    bpy.types.TOPBAR_MT_file_import.remove(menu_func_import) #type: ignore
