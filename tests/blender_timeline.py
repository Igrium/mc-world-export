"""Verify opt-in controls, recorded packets, key retiming and owned weather geometry in Blender 4.5."""
import json,os,sys,tempfile,wave
from pathlib import Path
import bpy
root=Path(__file__).resolve().parents[1];sys.path.insert(0,os.environ.get('WORLDEXPORT_ADDON_ROOT',str(root)))
import worldexport_blender
from worldexport_blender import timeline,sound_cues
replay,output=sys.argv[sys.argv.index('--')+1:];output=Path(output);output.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True);worldexport_blender.register();scene=bpy.context.scene;scene.render.fps=30
world=bpy.data.worlds.new('Existing World');scene.world=world;world.use_nodes=True
background=world.node_tree.nodes.get('Background');background.inputs['Strength'].default_value=.42
light=bpy.data.lights.new('Existing Light','AREA');light.energy=77
old=bpy.data.objects.new('Existing Light',light);scene.collection.objects.link(old)
assert bpy.ops.worldexport.import_replay(filepath=replay)=={'FINISHED'}
assert scene.world==world and abs(background.inputs['Strength'].default_value-.42)<1e-5 and light.energy==77
assert len(bpy.data.sounds)==0 and (scene.sequence_editor is None or len(scene.sequence_editor.strips)==0)
controller=next(o for o in scene.objects if timeline.ENV in o)
keys=json.loads(controller[timeline.ENV])['keys'];assert len(keys)>10
assert any(k['raining'] for k in keys) and any(not k['raining'] for k in keys)
assert max(k['defaultClockTime'] for k in keys)-min(k['defaultClockTime'] for k in keys)>10000
assert not controller['weather_enabled'] and not controller['time_enabled']
for curve in sound_cues.curves(controller):assert all(p.interpolation=='CONSTANT' for p in curve.keyframe_points)
events=[o for o in scene.objects if timeline.EVENT in o]
kinds={o['event_kind'] for o in events};assert {'example:quest_completed','example:level_changed','example:story_beat'}<=kinds,kinds
for obj in events:
    cue=json.loads(obj[timeline.EVENT])
    if cue['source']=='worldexport_studio':
        assert cue['timing']=='client_receipt_tick_with_server_timestamp'
        assert 'serverUnixMillis' in cue['data'] and 'serverGameTimeAtDispatch' in cue['data']
        active=next(c for c in sound_cues.curves(obj) if c.data_path=='["event_active"]')
        assert abs(next(p.co.x for p in active.keyframe_points if p.co.y>=.5)-cue['tick']*1.5)<1e-4
rig=timeline.build_rig(scene,controller)
assert scene.world==world and light.energy==77
sun=next(o for o in rig.objects if o.type=='LIGHT');rain=next(o for o in rig.objects if o.type=='MESH')
scene.frame_set(0);bpy.context.view_layer.update()
assert rain.hide_render and sun.data.energy==0
controller['weather_enabled']=True;controller['use_recorded_weather']=False;controller['weather_override']=1
controller['time_enabled']=True;controller['use_recorded_time']=False;controller['time_override']=6000
controller.update_tag();scene.frame_set(2);bpy.context.view_layer.update();assert not rain.hide_render and sun.data.energy>.99
controller['weather_override']=0;controller.update_tag();scene.frame_set(3);bpy.context.view_layer.update();assert rain.hide_render
controller['weather_override']=2;controller.update_tag();scene.frame_set(4);bpy.context.view_layer.update();assert not rain.hide_render
# Controls can themselves be keyframed and suppress a chosen interval.
timeline.key(controller,'weather_enabled',True,5);timeline.key(controller,'weather_enabled',False,6);timeline.constant(controller)
scene.frame_set(5);assert not rain.hide_render
scene.frame_set(6);assert rain.hide_render
controller['time_enabled']=False;controller.update_tag();scene.frame_set(7);assert sun.data.energy==0
# Geometry nodes produce geometry when weather is enabled.
scene.frame_set(5);evaluated=rain.evaluated_get(bpy.context.evaluated_depsgraph_get());mesh=evaluated.to_mesh();assert len(mesh.vertices)>0;evaluated.to_mesh_clear()
with tempfile.TemporaryDirectory() as directory:
    file=Path(directory)/'event.wav'
    with wave.open(str(file),'wb') as audio:
        audio.setnchannels(1);audio.setsampwidth(2);audio.setframerate(8000);audio.writeframes(b'\0\0'*1600)
    event=next(o for o in events if o['event_kind']=='example:quest_completed');event['replacement_file']=str(file)
    curve=next(c for c in sound_cues.curves(event) if c.data_path=='["event_active"]')
    for point in curve.keyframe_points:point.co.x+=9
    curve.update();start=next(p.co.x for p in curve.keyframe_points if p.co.y>=.5)
    event['event_enabled']=False;assert timeline.event_audio(scene,event)==[]
    assert timeline.environment_at_event(event,'RAIN','NOON') is None
    event['event_enabled']=True;event['event_sound_enabled']=False
    assert timeline.event_audio(scene,event)==[]
    assert timeline.environment_at_event(event,'RAIN','NOON')==start
    event['event_sound_enabled']=True;event['event_effects_enabled']=False
    assert timeline.environment_at_event(event,'CLEAR','NIGHT') is None
    event['event_effects_enabled']=True;strips=timeline.event_audio(scene,event);assert strips[0].frame_final_start==round(start)
    timeline.key(event,'event_enabled',True,10);timeline.key(event,'event_enabled',False,11);timeline.constant(event);event.update_tag()
    scene.frame_set(10);assert not strips[0].mute
    scene.frame_set(11);assert strips[0].mute
    scene.frame_set(10);frame=timeline.environment_at_event(event,'RAIN','NOON');assert frame==start
    scene.frame_set(int(start)-1);assert not controller['weather_enabled']
    scene.frame_set(int(start));assert controller['weather_enabled'] and controller['weather_override']==1 and controller['time_override']==6000
    sound=next(o for o in scene.objects if sound_cues.META in o);sound['sound_enabled']=False
    assert sound_cues.place_audio(scene,sound)==[]
    for strip in list(scene.sequence_editor.strips):scene.sequence_editor.strips.remove(strip)
    event['replacement_file']=''
for audio in list(bpy.data.sounds):bpy.data.sounds.remove(audio)
scene.frame_set(5)
bpy.ops.wm.save_as_mainfile(filepath=str(output/'timeline.blend'))
bpy.ops.wm.open_mainfile(filepath=str(output/'timeline.blend'))
assert bpy.context.scene.world.name=='Existing World'
assert len([o for o in bpy.context.scene.objects if timeline.EVENT in o])==len(events)
result={'environmentSamples':len(keys),'eventKinds':sorted(kinds),'importedCueTimingVerified':True,
        'clientReceiptTimingVerified':True,'existingWorldAndLightsPreserved':True,'audioAutoLoaded':False,
        'retimedEventAudioPlacementVerified':True,'disabledSoundsAndEventsSkipped':True,
        'eventWeatherAndTimeMappingVerified':True,'placedAudioEnableKeysVerified':True,'weatherOverrideAndEnableKeysVerified':True,'optionalSunAndRainRigVerified':True,'saveReloadVerified':True,
        'syntheticFixture':True,'lightningGenerated':False}
(output/'result.json').write_text(json.dumps(result,indent=2)+'\n');print('TIMELINE_OK '+json.dumps(result),flush=True)
