"""Optional capture identities and honest coverage diagnostics for imported scenes."""
import json
from pathlib import Path
import bpy
from bpy.types import Panel

META='worldexport_appearance'


def load(context):
    path=Path(context.replay_root)/'appearance.json'
    if not path.is_file():
        context.entity_collection['worldexport_capture_report']=json.dumps({'legacy':True})
        return
    data=json.loads(path.read_text())
    if data.get('schemaVersion') != 1:
        context.entity_collection['worldexport_capture_report']=json.dumps({'unknown_schema':data.get('schemaVersion')})
        return
    context.appearance=data.get('entities',{})
    context.entity_collection['worldexport_capture_report']=json.dumps(data.get('captureReport',{}))


def attach(entity, context):
    info=context.appearance.get(entity.name)
    if not info:return
    for obj in [entity.armature,entity.mesh,*entity.part_meshes.values()]:
        if obj:
            obj[META]=json.dumps(info)
            obj['worldexport_instance_id']=info.get('instanceId','')
            obj['worldexport_entity_type']=info.get('entityType',info.get('blockEntityType',''))


class WORLDEXPORT_PT_capture(Panel):
    bl_label='Capture Coverage'
    bl_space_type='VIEW_3D'
    bl_region_type='UI'
    bl_category='Replay Export'

    def draw(self,context):
        layout=self.layout
        pending=[context.scene.collection];visible=set()
        while pending:
            collection=pending.pop()
            if collection in visible:continue
            visible.add(collection);pending.extend(collection.children)
        collections=[c for c in visible if 'worldexport_capture_report' in c]
        if not collections:
            layout.label(text='Import a replay to see capture coverage')
        for collection in collections:
            data=json.loads(collection['worldexport_capture_report'])
            box=layout.box();box.label(text=collection.name)
            if data.get('legacy'):
                box.label(text='Older export: no capture report',icon='INFO')
                continue
            if 'unknown_schema' in data:
                box.label(text='Unrecognized capture-report version',icon='ERROR')
                continue
            box.label(text=f"Instances: {data.get('instances',0)}")
            box.label(text=f"With geometry: {data.get('withGeometry',0)}")
            box.label(text=f"Without geometry: {data.get('withoutGeometry',0)}")
            box.label(text=f"Warnings: {data.get('withWarnings',0)}")
            if not data.get('blockIdentitiesCaptured'):
                box.label(text='Per-block identities not captured yet',icon='INFO')
        obj=context.object
        if obj and META in obj:
            data=json.loads(obj[META]);box=layout.box()
            box.label(text=obj.get('worldexport_entity_type',''))
            box.label(text='Geometry captured' if data.get('geometryCaptured') else 'No geometry captured')
            for warning in data.get('warnings',[]):box.label(text=warning.replace('_',' '),icon='INFO')


def register():bpy.utils.register_class(WORLDEXPORT_PT_capture)
def unregister():bpy.utils.unregister_class(WORLDEXPORT_PT_capture)
