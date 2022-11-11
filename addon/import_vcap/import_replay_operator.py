# Keep the import replay operator in its own file because it's so big.

from os import name, path

import bpy
import threading
from bpy.props import BoolProperty, EnumProperty, StringProperty
from bpy.types import Context, Operator, Event, Scene
from bpy_extras.io_utils import ImportHelper

from .replay import replay_file
from .vcap.context import VCAPSettings


class ImportReplayOperator(Operator, ImportHelper):
    bl_idname = "vcap.importreplay"
    bl_label = "Import Minecraft Replay"

    # ImportHelper mixin class uses this
    filename_ext = ".txt"

    current_percentage = 0.0
    updated = False
    abort = False
    __thread = None
    __timer = None

    scene: Scene = None

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

    def __error(self, message: str):
        self.report({"ERROR"}, message)
    
    def __feedback(self, message: str):
        self.report({"INFO"}, message)
    
    def __load_replay(self, context: Context):
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

        def progress_update(val: float):
            self.current_percentage = val
            self.updated = True
        
        handle = replay_file.ExecutionHandle(
            onProgress=progress_update,
            onFeedback=self.__feedback,
            onWarning=self.__error,
            should_abort=lambda: self.abort
        )

        replay_file.load_replay(self.filepath, context, context.scene.collection, handle=handle, settings=settings)

    def execute(self, context: Context):
        wm = context.window_manager
        self.__timer = wm.event_timer_add(0.1, window=context.window)
        wm.modal_handler_add(self)

        self.__thread = threading.Thread(target=self.__load_replay, args=[context])
        self.scene = context.scene
        self.scene.show_progress_bar = True
        self.scene.progress_bar = 0

        self.__thread.start()

        return {'RUNNING_MODAL'}

    def modal(self, context: Context, event: Event):
        context.area.tag_redraw()

        # if event.type == 'ESC':
        #     self.abort = True
        #     self.finish(context)
        #     return 

        if self.updated:
            progress = self.current_percentage
            self.scene.progress_bar = int(progress * 100)
            self.updated = False
        
        if not self.__thread.is_alive():
            self.finish(context)
            return {'FINISHED'}
        
        return {'RUNNING_MODAL'}

    def finish(self, context: Context):
        wm = context.window_manager
        wm.event_timer_remove(self.__timer)
        self.scene.show_progress_bar = False


def _menu_func_replay(self, context):
    self.layout.operator(ImportReplayOperator.bl_idname,
                         text="Minecraft Replay File (.replay)")

def _header_draw_func(self, context: Context):
    scene = context.scene
    if (scene.show_progress_bar):
        self.layout.prop(scene, 'progress_bar')

def register():
    bpy.types.Scene.progress_bar = bpy.props.IntProperty(
        subtype="PERCENTAGE",
        min=0,
        max=100
    )

    bpy.types.Scene.show_progress_bar = bpy.props.BoolProperty()

    bpy.utils.register_class(ImportReplayOperator)
    bpy.types.TOPBAR_MT_file_import.append(_menu_func_replay)
    bpy.types.TOPBAR_HT_upper_bar.append(_header_draw_func)

def unregister():
    del bpy.types.Scene.progress_bar
    del bpy.types.Scene.show_progress_bar

    bpy.utils.unregister_class(ImportReplayOperator)
    bpy.types.TOPBAR_MT_file_import.remove(_menu_func_replay)
    bpy.types.TOPBAR_HT_upper_bar.remove(_header_draw_func)