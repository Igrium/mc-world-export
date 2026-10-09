"""Round-trip captured sound cues, source references, retiming and optional audio mapping."""
import json, math, os, sys, tempfile, wave
from pathlib import Path
import bpy
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,os.environ.get('WORLDEXPORT_ADDON_ROOT',str(root)))
import worldexport_blender
from worldexport_blender import sound_cues
replay,legacy,output=sys.argv[sys.argv.index('--')+1:]
output=Path(output).resolve();output.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True);worldexport_blender.register()
assert bpy.ops.worldexport.import_replay(filepath=legacy)=={'FINISHED'}
assert any(json.loads(c.get('worldexport_sound_report','{}')).get('legacy') for c in bpy.data.collections)
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.context.scene.render.fps=30
assert bpy.ops.worldexport.import_replay(filepath=replay)=={'FINISHED'}
scene=bpy.context.scene
objects=[o for o in scene.objects if sound_cues.META in o]
assert objects,'No captured sound cues were imported'
cues=[json.loads(o[sound_cues.META]) for o in objects]
assert any(c['category']=='blocks' and c['soundId']=='minecraft:block.note_block.harp' for c in cues)
assert any(c['category']=='ambient' for c in cues)
assert any(c['category']=='neutral' and c['source']['association']=='explicit_entity_bound_sound' for c in cues)
assert all(c.get('resolvedFile','').endswith('.ogg') for c in cues if c['resolved'])
assert all('sounds/' in c['resolvedFile'] for c in cues if c['resolved'])
for obj,cue in zip(objects,cues):
    start,end=sound_cues.cue_timing(obj)
    assert abs(start-cue['startTick']*1.5)<1e-5
    assert all(math.isclose(a,b,abs_tol=1e-5) for a,b in zip(obj.location,sound_cues.common.convert_coords(*cue['position']))) or cue.get('positionKeys')
    for curve in sound_cues.curves(obj):
        if curve.data_path=='["cue_active"]': assert all(p.interpolation=='CONSTANT' for p in curve.keyframe_points)
loop=next(o for o in objects if json.loads(o[sound_cues.META])['looping'])
data=json.loads(loop[sound_cues.META]);assert data['endReason']=='explicit_stop'
assert data['source']['instanceId'] in {o.get('worldexport_instance_id') for o in scene.objects}
assert abs(loop['pitch']-.75)<1e-6
assert all(not o['replacement_file'] for o in objects)
assert len(bpy.data.sounds)==0,'Import unexpectedly loaded audio'
# Edit the cue keys, then opt in to mapping a temporary stock/custom file.
for curve in sound_cues.curves(loop):
    if curve.data_path=='["cue_active"]':
        for point in curve.keyframe_points: point.co.x+=7
        curve.update()
start,end=sound_cues.cue_timing(loop)
# Resolve a real local vanilla sample without extracting or copying its audio.
scene.worldexport_sound_asset_index=os.environ['WORLDEXPORT_STOCK_INDEX']
bpy.context.view_layer.objects.active=loop;loop.select_set(True)
assert bpy.ops.worldexport.sound_stock()=={'FINISHED'}
stock_strips=sound_cues.place_audio(scene,loop)
assert stock_strips and stock_strips[0].frame_final_duration>0
for strip in stock_strips:scene.sequence_editor.strips.remove(strip)
loop['replacement_file']=''
with tempfile.TemporaryDirectory() as directory:
    file=Path(directory)/'replacement.wav'
    with wave.open(str(file),'wb') as sound:
        sound.setnchannels(1);sound.setsampwidth(2);sound.setframerate(8000);sound.writeframes(b'\0\0'*1600)
    bpy.context.view_layer.objects.active=loop;loop.select_set(True)
    assert bpy.ops.worldexport.sound_file(filepath=str(file),all_same_event=True)=={'FINISHED'}
    matched=sound_cues.same_event_objects(loop)
    assert len(matched)>=2 and all(o['replacement_file']==str(file) for o in matched)
    strips=sound_cues.place_audio(scene,loop)
    assert len(strips)>=2 and strips[0].frame_final_start==round(start)
    assert strips[-1].frame_final_end==round(end)
    assert all(abs(s.volume-loop['volume'])<1e-6 for s in strips)
    assert len({s.channel for s in strips})==1
    before=len(scene.sequence_editor.strips)
    loop['replacement_file']=str(Path(directory)/'missing.wav')
    try: sound_cues.place_audio(scene,loop)
    except ValueError: pass
    else: raise AssertionError('Missing file should fail safely')
    assert len(scene.sequence_editor.strips)==before
    # Keep the saved scene metadata-only, with no temporary sample paths or strips.
    for strip in list(scene.sequence_editor.strips): scene.sequence_editor.strips.remove(strip)
    for obj in matched: obj['replacement_file']=''
for sound in list(bpy.data.sounds):bpy.data.sounds.remove(sound)
bpy.ops.wm.save_as_mainfile(filepath=str(output/'sound-cues.blend'))
bpy.ops.wm.open_mainfile(filepath=str(output/'sound-cues.blend'))
assert len([o for o in bpy.context.scene.objects if sound_cues.META in o])==len(cues)
assert len(bpy.data.sounds)==0
result={'cueCount':len(cues),'categories':sorted({c['category'] for c in cues}),
    'explicitEntityAssociationVerified':True,'resolvedFilesVerified':True,'stockSamplePlacementVerified':True,'fps30TimingVerified':True,
    'loopStopVerified':True,'retimedReplacementPlacementVerified':True,'replacementSameEventVerified':True,
    'missingFilePreservesExistingStrips':True,'savedSceneReloadVerified':True,'audioAutoLoaded':False,
    'pitchAndSpatialMixApplied':False}
(output/'result.json').write_text(json.dumps(result,indent=2)+'\n')
print('SOUND_CUES_OK '+json.dumps(result),flush=True)
