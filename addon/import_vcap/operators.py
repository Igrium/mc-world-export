import bpy

class ImportVCAPOperator(bpy.types.Operator):
    bl_idname = "vcap.import_vcap"
    bl_label = "Import VCAP"

    def execute(self, context):
        return {'FINISHED'}

def menu_func(self, context):
    self.layout.operator(ImportVCAPOperator.bl_idname)

def register():
    bpy.types.VIEW3D_MT_add.append(menu_func)

