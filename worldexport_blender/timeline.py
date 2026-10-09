"""Recorded environment and semantic cues; effects are always opt-in."""
import json
from pathlib import Path
import bpy
from bpy.types import Operator, Panel
from bpy.props import BoolProperty, StringProperty, EnumProperty
from bpy_extras.io_utils import ImportHelper
from . import sound_cues

ENV = 'worldexport_environment'
EVENT = 'worldexport_event'

def key(obj, name, value, frame):
    obj[name] = value
    obj.keyframe_insert('[' + json.dumps(name) + ']', frame=frame)

def constant(obj):
    for curve in sound_cues.curves(obj):
        for point in curve.keyframe_points: point.interpolation = 'CONSTANT'

def load(context):
    root = Path(context.replay_root)
    collection = bpy.data.collections.new('Environment and Events')
    context.bl_context.scene.collection.children.link(collection)
    for file in ('environment.json', 'events.json'):
        path = root / file
        if not path.is_file():
            collection['worldexport_' + file[:-5] + '_report'] = json.dumps({'legacy': True})
            continue
        data = json.loads(path.read_text())
        collection['worldexport_' + file[:-5] + '_report'] = json.dumps({k:v for k,v in data.items() if k not in ('keys','cues')})
        if data.get('schemaVersion') != 1: continue
        if file == 'environment.json':
            obj = bpy.data.objects.new('Recorded Environment', None);collection.objects.link(obj)
            obj[ENV] = json.dumps(data)
            defaults = {'weather_enabled':False,'use_recorded_weather':True,'weather_override':0,
                        'time_enabled':False,'use_recorded_time':True,'time_override':6000.,'day_length':24000.}
            for name, value in defaults.items(): obj[name] = value
            obj.id_properties_ui('weather_override').update(min=0,max=2,description='0 clear, 1 rain, 2 thunder')
            obj.id_properties_ui('day_length').update(min=1,description='Clock period for the sun rig; adjust for custom dimensions')
            for entry in data['keys']:
                frame = context.tick_to_frame(entry['tick'])
                for name, value in entry.items():
                    if name in ('tick','dimension'): continue
                    key(obj, 'recorded_' + name, value, frame)
            constant(obj)
        else:
            for cue in data['cues']:
                obj = bpy.data.objects.new(cue['cueId'] + ' ' + cue['kind'], None);collection.objects.link(obj)
                obj[EVENT] = json.dumps(cue);obj['event_kind'] = cue['kind'];obj['event_enabled'] = True
                obj['event_sound_enabled'] = True;obj['event_effects_enabled'] = True
                obj['replacement_file'] = '';obj['volume'] = 1.0
                obj.id_properties_ui('replacement_file').update(subtype='FILE_PATH')
                start = context.tick_to_frame(cue['tick'])
                key(obj,'event_active',0.,start-1);key(obj,'event_active',1.,start);key(obj,'event_active',0.,start+1)
                constant(obj)
                context.bl_context.scene.timeline_markers.new(obj.name,frame=round(start))

def variable(driver, name, obj, prop):
    var=driver.variables.new();var.name=name;var.type='SINGLE_PROP'
    var.targets[0].id=obj;var.targets[0].data_path='['+json.dumps(prop)+']'

def driven(target, path, expression, obj, props, index=None):
    curve=target.driver_add(path) if index is None else target.driver_add(path,index)
    driver=curve.driver;driver.type='SCRIPTED';driver.expression=expression
    for name, prop in props.items(): variable(driver,name,obj,prop)

def build_rig(scene, obj):
    """Create owned Sun and procedural rain; never touch an existing world, light or weather rig."""
    collection=bpy.data.collections.new('Replay Weather Rig');scene.collection.children.link(collection)
    collection['worldexport_weather_rig']=obj.name
    sun_data=bpy.data.lights.new('Replay Sun','SUN');sun=bpy.data.objects.new('Replay Sun',sun_data);collection.objects.link(sun);sun.parent=obj
    sun.rotation_euler[2]=.35
    driven(sun,'rotation_euler','((clock if recorded else override)/max(period,1))*6.283185307-1.570796327',obj,
           {'clock':'recorded_defaultClockTime','recorded':'use_recorded_time','override':'time_override','period':'day_length'},0)
    driven(sun_data,'energy','enabled*max(0,sin(((clock if recorded else override)/max(period,1))*6.283185307))',obj,
           {'enabled':'time_enabled','clock':'recorded_defaultClockTime','recorded':'use_recorded_time','override':'time_override','period':'day_length'})
    mesh=bpy.data.meshes.new('Replay Rain');rain=bpy.data.objects.new('Replay Rain',mesh);collection.objects.link(rain)
    rain.parent=obj;rain.location=(0,0,0)
    group=bpy.data.node_groups.new('Replay Rain','GeometryNodeTree')
    group.interface.new_socket(name='Geometry',in_out='OUTPUT',socket_type='NodeSocketGeometry')
    nodes,links=group.nodes,group.links
    output=nodes.new('NodeGroupOutput');line=nodes.new('GeometryNodeMeshLine');line.inputs['Count'].default_value=512
    positions=nodes.new('FunctionNodeRandomValue');positions.data_type='FLOAT_VECTOR'
    positions.inputs['Min'].default_value=(-10,-10,0);positions.inputs['Max'].default_value=(10,10,12);positions.inputs['Seed'].default_value=26
    time=nodes.new('GeometryNodeInputSceneTime');speed=nodes.new('ShaderNodeMath');speed.operation='MULTIPLY';speed.inputs[1].default_value=-8
    combine=nodes.new('ShaderNodeCombineXYZ');add=nodes.new('ShaderNodeVectorMath');add.operation='ADD'
    wrap=nodes.new('ShaderNodeVectorMath');wrap.operation='WRAP';wrap.inputs[1].default_value=(10,10,12);wrap.inputs[2].default_value=(-10,-10,0)
    setpos=nodes.new('GeometryNodeSetPosition');strand=nodes.new('GeometryNodeCurvePrimitiveLine');strand.inputs['Start'].default_value=(0,0,0);strand.inputs['End'].default_value=(.03,0,.28)
    circle=nodes.new('GeometryNodeCurvePrimitiveCircle');circle.inputs['Resolution'].default_value=3;circle.inputs['Radius'].default_value=.006
    tube=nodes.new('GeometryNodeCurveToMesh');instances=nodes.new('GeometryNodeInstanceOnPoints')
    links.new(time.outputs['Seconds'],speed.inputs[0]);links.new(speed.outputs[0],combine.inputs['Z'])
    links.new(positions.outputs['Value'],add.inputs[0]);links.new(combine.outputs[0],add.inputs[1]);links.new(add.outputs[0],wrap.inputs[0])
    links.new(line.outputs['Mesh'],setpos.inputs['Geometry']);links.new(wrap.outputs[0],setpos.inputs['Position'])
    links.new(strand.outputs['Curve'],tube.inputs['Curve']);links.new(circle.outputs['Curve'],tube.inputs['Profile Curve'])
    links.new(setpos.outputs['Geometry'],instances.inputs['Points']);links.new(tube.outputs['Mesh'],instances.inputs['Instance']);realize=nodes.new('GeometryNodeRealizeInstances');links.new(instances.outputs['Instances'],realize.inputs['Geometry']);links.new(realize.outputs['Geometry'],output.inputs['Geometry'])
    modifier=rain.modifiers.new('Replay Rain','NODES');modifier.node_group=group
    props={'enabled':'weather_enabled','recorded':'use_recorded_weather','amount':'recorded_rain','allowed':'recorded_weatherAllowed','override':'weather_override'}
    expression='not enabled or ((amount<0.01 or not allowed) if recorded else override==0)'
    driven(rain,'hide_render',expression,obj,props);driven(rain,'hide_viewport',expression,obj,props)
    # Thunder is an editable storm state, not an invented lightning strike schedule.
    return collection

def event_audio(scene,obj):
    if not obj['event_enabled'] or not obj.get('event_sound_enabled',True): return []
    if 'event_sound_enabled' not in obj: obj['event_sound_enabled']=True
    curve=next(c for c in sound_cues.curves(obj) if c.data_path=='["event_active"]')
    start=next(p.co.x for p in curve.keyframe_points if p.co.y>=.5)
    path=Path(bpy.path.abspath(obj['replacement_file']))
    if not path.is_file():raise ValueError('Choose an audio file for this event')
    editor=scene.sequence_editor_create();channel=max((s.channel for s in editor.strips),default=0)+1
    if channel>128:raise ValueError('No free sequencer channels')
    strip=editor.strips.new_sound('Event: '+obj.name,str(path),channel=channel,frame_start=round(start))
    strip.volume=max(0,obj['volume']);sound_cues.enable_driver(strip,obj,'event_sound_enabled','event_enabled');return [strip]

def environment_at_event(obj, weather='KEEP', time='KEEP'):
    if not obj['event_enabled'] or not obj.get('event_effects_enabled',True):return None
    controller=next((o for c in obj.users_collection for o in c.objects if ENV in o),None)
    if controller is None:raise ValueError('This import has no environment controller')
    curve=next(c for c in sound_cues.curves(obj) if c.data_path=='["event_active"]')
    frame=next(p.co.x for p in curve.keyframe_points if p.co.y>=.5)
    updates={}
    if weather=='DISABLE':updates['weather_enabled']=False
    elif weather=='RECORDED':updates.update(weather_enabled=True,use_recorded_weather=True)
    elif weather!='KEEP':updates.update(weather_enabled=True,use_recorded_weather=False,weather_override={'CLEAR':0,'RAIN':1,'THUNDER':2}[weather])
    if time=='DISABLE':updates['time_enabled']=False
    elif time=='RECORDED':updates.update(time_enabled=True,use_recorded_time=True)
    elif time!='KEEP':updates.update(time_enabled=True,use_recorded_time=False,time_override={'DAY':1000.,'NOON':6000.,'NIGHT':18000.}[time])
    for prop,value in updates.items():
        path='['+json.dumps(prop)+']'
        existing=next((c for c in sound_cues.curves(controller) if c.data_path==path),None)
        before=existing.evaluate(frame-1) if existing else controller[prop]
        key(controller,prop,before,frame-1);key(controller,prop,value,frame)
    constant(controller);controller.update_tag();return frame

class WORLDEXPORT_OT_event_environment(Operator):
    bl_idname='worldexport.event_environment';bl_label='Key Weather or Time at Event';bl_options={'REGISTER','UNDO'}
    weather: EnumProperty(name='Weather',items=[(k,label,'') for k,label in (
        ('KEEP','Keep Current'),('RECORDED','Use Recorded'),('CLEAR','Clear'),('RAIN','Rain'),('THUNDER','Thunder Cue'),('DISABLE','Disable Weather'))],default='KEEP')
    time: EnumProperty(name='Time',items=[(k,label,'') for k,label in (
        ('KEEP','Keep Current'),('RECORDED','Use Recorded'),('DAY','Morning'),('NOON','Noon'),('NIGHT','Night'),('DISABLE','Disable Time'))],default='KEEP')
    @classmethod
    def poll(cls,context):return context.object is not None and EVENT in context.object
    def invoke(self,context,event):return context.window_manager.invoke_props_dialog(self)
    def execute(self,context):
        try:frame=environment_at_event(context.object,self.weather,self.time)
        except Exception as error:self.report({'ERROR'},str(error));return {'CANCELLED'}
        self.report({'INFO'},'Disabled event skipped' if frame is None else f'Controls keyed at frame {frame:g}; use the optional rig or your own effects')
        return {'FINISHED'}

class WORLDEXPORT_OT_event_file(Operator,ImportHelper):
    bl_idname='worldexport.event_file';bl_label='Choose Event Sound';bl_options={'REGISTER','UNDO'}
    filter_glob: StringProperty(default='*.wav;*.ogg;*.mp3;*.flac',options={'HIDDEN'})
    all_same_kind: BoolProperty(name='Apply to every event of this kind in this import',default=False)
    @classmethod
    def poll(cls,context):return context.object is not None and EVENT in context.object
    def execute(self,context):
        path=Path(bpy.path.abspath(self.filepath)).resolve()
        if not path.is_file():self.report({'ERROR'},'Choose an existing audio file');return {'CANCELLED'}
        obj=context.object
        objects=[o for collection in obj.users_collection for o in collection.objects if EVENT in o and o['event_kind']==obj['event_kind']] if self.all_same_kind else [obj]
        for candidate in objects:candidate['replacement_file']=str(path)
        self.report({'INFO'},f'Mapped {len(objects)} event cue(s)');return {'FINISHED'}

class WORLDEXPORT_OT_environment_rig(Operator):
    bl_idname='worldexport.environment_rig';bl_label='Build Optional Sun and Rain Rig';bl_options={'REGISTER','UNDO'}
    @classmethod
    def poll(cls,context):return context.object is not None and ENV in context.object
    def execute(self,context):
        try:build_rig(context.scene,context.object)
        except Exception as error:self.report({'ERROR'},str(error));return {'CANCELLED'}
        self.report({'INFO'},'Rig added; enable Time or Weather to use it. Thunder state has no generated lightning.');return {'FINISHED'}

class WORLDEXPORT_OT_event_audio(Operator):
    bl_idname='worldexport.event_audio';bl_label='Place Event Audio';bl_options={'REGISTER','UNDO'}
    @classmethod
    def poll(cls,context):return context.object is not None and EVENT in context.object
    def execute(self,context):
        try:count=len(event_audio(context.scene,context.object))
        except Exception as error:self.report({'ERROR'},str(error));return {'CANCELLED'}
        self.report({'INFO'},f'Placed {count} strip(s); repeat calls add copies');return {'FINISHED'}

class WORLDEXPORT_PT_timeline(Panel):
    bl_label='Environment and Events';bl_space_type='VIEW_3D';bl_region_type='UI';bl_category='Replay Export'
    def draw(self,context):
        layout=self.layout;obj=context.object
        if obj is None:return
        if ENV in obj:
            for prop,label in (('weather_enabled','Enable Weather'),('use_recorded_weather','Recorded Weather'),('weather_override','Override: 0 Clear / 1 Rain / 2 Thunder'),('time_enabled','Enable Time'),('use_recorded_time','Recorded Clock'),('time_override','Override Clock'),('day_length','Sun Clock Period')):
                layout.prop(obj,'['+json.dumps(prop)+']',text=label)
            layout.operator('worldexport.environment_rig');layout.label(text='Starter rain and sun; thunder state is a cue')
        elif EVENT in obj:
            cue=json.loads(obj[EVENT]);layout.label(text=obj['event_kind']);layout.label(text=cue['timing'])
            layout.prop(obj,'["event_enabled"]',text='Enable Event')
            layout.prop(obj,'["event_sound_enabled"]',text='Enable Event Sound')
            layout.prop(obj,'["event_effects_enabled"]',text='Allow Weather / Time Keys')
            layout.prop(obj,'["replacement_file"]',text='Audio File');layout.prop(obj,'["volume"]',text='Volume')
            layout.operator('worldexport.event_file');layout.operator('worldexport.event_audio');layout.operator('worldexport.event_environment')
        else:layout.label(text='Select the environment controller or an event cue')

CLASSES=(WORLDEXPORT_OT_event_environment,WORLDEXPORT_OT_event_file,WORLDEXPORT_OT_environment_rig,WORLDEXPORT_OT_event_audio,WORLDEXPORT_PT_timeline)
def register():
    for cls in CLASSES:bpy.utils.register_class(cls)
def unregister():
    for cls in reversed(CLASSES):bpy.utils.unregister_class(cls)
