import bpy
from bpy.types import Context
from ..mesh import mesh_utils

def main(context: Context):
    selected = context.active_object
    if not selected or selected.type != 'MESH':
        raise TypeError("Mesh must be selected.")

    result = mesh_utils.split_vertex_groups(selected, lambda name: name == 'splitme' or name == 'splitmetoo')
    for obj in result.values():
        context.collection.objects.link(obj) # type: ignore

class WORLDEXPORT_OT_split_test(bpy.types.Operator):
    """Tooltip"""
    bl_idname = "worldexport.split_test"
    bl_label = "Split mesh on vertex group"

    @classmethod
    def poll(cls, context):
        return context.active_object is not None

    def execute(self, context): # type: ignore
        main(context)
        return {'FINISHED'}


def menu_func(self, context):
    self.layout.operator(WORLDEXPORT_OT_split_test.bl_idname, text=WORLDEXPORT_OT_split_test.bl_label)


# Register and add to the "object" menu (required to also use F3 search "Simple Object Operator" for quick access).
def register():
    bpy.utils.register_class(WORLDEXPORT_OT_split_test)
    bpy.types.VIEW3D_MT_object.append(menu_func)


def unregister():
    bpy.utils.unregister_class(WORLDEXPORT_OT_split_test)
    bpy.types.VIEW3D_MT_object.remove(menu_func)
