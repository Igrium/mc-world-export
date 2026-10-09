"""Timed sound references and optional replacement files; imports never load audio."""
import json
from pathlib import Path
import bpy
from bpy.props import BoolProperty, StringProperty
from bpy.types import Operator, Panel
from bpy_extras.io_utils import ImportHelper
from . import common

META = 'worldexport_sound_cue'


def curves(obj):
    animation = obj.animation_data
    if not animation or not animation.action or not animation.action_slot:
        return []
    return [curve for layer in animation.action.layers for strip in layer.strips
            if (bag := strip.channelbag(animation.action_slot)) for curve in bag.fcurves]


def cue_timing(obj):
    active = next(c for c in curves(obj) if c.data_path == '["cue_active"]')
    start = next(p.co.x for p in active.keyframe_points if p.co.y >= .5)
    ends = [p.co.x for p in active.keyframe_points if p.co.x >= start and p.co.y < .5]
    return start, max(ends) if ends and obj['end_bounded'] else None


def load(context):
    path = Path(context.replay_root) / 'sounds.json'
    if not path.is_file():
        context.entity_collection['worldexport_sound_report'] = json.dumps({'legacy': True})
        return
    data = json.loads(path.read_text())
    if data.get('schemaVersion') != 1:
        context.entity_collection['worldexport_sound_report'] = json.dumps({'unknown_schema': data.get('schemaVersion')})
        return
    collection = bpy.data.collections.new('Sound Cues')
    context.bl_context.scene.collection.children.link(collection)
    collection['worldexport_sound_report'] = json.dumps({k:v for k,v in data.items() if k != 'cues'} | {'cueCount': len(data['cues'])})
    for cue in data['cues']:
        obj = bpy.data.objects.new(cue['cueId'] + ' ' + cue['soundId'], None)
        collection.objects.link(obj)
        obj.empty_display_type = 'SPHERE'; obj.empty_display_size = .15
        obj[META] = json.dumps(cue)
        obj['sound_id'] = cue['soundId']
        obj['resolved_file'] = cue.get('resolvedFile', '')
        obj['source_entity_id'] = cue.get('source', {}).get('instanceId', '')
        obj['replacement_file'] = ''; obj['sound_enabled'] = True
        obj['volume'] = cue.get('volume', 1.0); obj['pitch'] = cue.get('pitch', 1.0)
        obj['loop_delay_frames'] = context.tick_to_frame(cue.get('loopDelayTicks', 0))
        obj['end_bounded'] = 'endTick' in cue
        start = context.tick_to_frame(cue['startTick'])
        end = context.tick_to_frame(cue['endTick']) if 'endTick' in cue else start + 1
        obj['cue_active'] = 0.0; obj.keyframe_insert('["cue_active"]', frame=start-1)
        obj['cue_active'] = 1.0; obj.keyframe_insert('["cue_active"]', frame=start)
        obj['cue_active'] = 0.0; obj.keyframe_insert('["cue_active"]', frame=max(end, start+.001))
        for curve in curves(obj):
            for point in curve.keyframe_points: point.interpolation = 'CONSTANT'
        keys = cue.get('positionKeys', [{'tick': cue['startTick'], 'position': cue['position']}])
        for key in keys:
            obj.location = common.convert_coords(*key['position'])
            obj.keyframe_insert('location', frame=context.tick_to_frame(key['tick']))
        marker = context.bl_context.scene.timeline_markers.new(collection.name + ': ' + obj.name, frame=round(start))
        marker.select = False


def same_event_objects(obj):
    return [candidate for collection in obj.users_collection
            if 'worldexport_sound_report' in collection
            for candidate in collection.objects if META in candidate and candidate['sound_id'] == obj['sound_id']]


def stock_file(cue, index_path):
    index_path = Path(index_path)
    index = json.loads(index_path.read_text())
    namespace, resource = cue['resolvedFile'].split(':', 1)
    entry = index['objects'].get(namespace + '/' + resource)
    if entry is None: raise ValueError('Sample is absent from this stock index; choose your resource-pack file')
    digest = entry['hash']
    if len(digest) != 40 or any(c not in '0123456789abcdef' for c in digest):
        raise ValueError('Invalid stock sound hash')
    file = index_path.parent.parent / 'objects' / digest[:2] / digest
    if not file.is_file(): raise ValueError('Stock sound asset is not available locally')
    return str(file.resolve())


class WORLDEXPORT_OT_sound_stock(Operator):
    bl_idname = 'worldexport.sound_stock'
    bl_label = 'Use Recorded Stock Sample'
    bl_options = {'REGISTER', 'UNDO'}

    @classmethod
    def poll(cls, context): return context.object is not None and META in context.object

    def execute(self, context):
        cue = json.loads(context.object[META])
        try: context.object['replacement_file'] = stock_file(cue, bpy.path.abspath(context.scene.worldexport_sound_asset_index))
        except Exception as error:
            self.report({'ERROR'}, str(error)); return {'CANCELLED'}
        if cue.get('resourcePack') != 'vanilla':
            self.report({'WARNING'}, 'Stock fallback selected; the recorded resource pack may supply a different file')
        return {'FINISHED'}


class WORLDEXPORT_OT_sound_file(Operator, ImportHelper):
    bl_idname = 'worldexport.sound_file'
    bl_label = 'Choose Sound Replacement'
    bl_options = {'REGISTER', 'UNDO'}
    filter_glob: StringProperty(default='*.wav;*.ogg;*.mp3;*.flac', options={'HIDDEN'})
    all_same_event: BoolProperty(name='Apply to every cue with this sound ID', default=True)

    @classmethod
    def poll(cls, context): return context.object is not None and META in context.object

    def execute(self, context):
        path = str(Path(bpy.path.abspath(self.filepath)).resolve())
        if not Path(path).is_file():
            self.report({'ERROR'}, 'Choose an existing audio file'); return {'CANCELLED'}
        objects = same_event_objects(context.object) if self.all_same_event else [context.object]
        for obj in objects: obj['replacement_file'] = path
        self.report({'INFO'}, f'Mapped {len(objects)} cue(s); audio placement is optional')
        return {'FINISHED'}


def enable_driver(strip, obj, prop, master=None):
    driver=strip.driver_add('mute').driver;driver.type='SCRIPTED';driver.expression='not enabled'
    var=driver.variables.new();var.name='enabled';var.type='SINGLE_PROP'
    var.targets[0].id=obj;var.targets[0].data_path='['+json.dumps(prop)+']'
    if master:
        driver.expression='not enabled or not master'
        var=driver.variables.new();var.name='master';var.type='SINGLE_PROP'
        var.targets[0].id=obj;var.targets[0].data_path='['+json.dumps(master)+']'


def place_audio(scene, obj):
    if 'sound_enabled' not in obj: obj['sound_enabled'] = True
    if not obj['sound_enabled']: return []
    path = Path(bpy.path.abspath(obj['replacement_file']))
    if not path.is_file(): raise ValueError('Choose a stock or custom audio file first')
    start, end = cue_timing(obj)
    if end is not None and end <= start: raise ValueError('Sound cue ends before it starts')
    editor = scene.sequence_editor_create()
    # Create only new strips; preserve existing edits and allow Blender undo.
    cue = json.loads(obj[META]); cursor = round(start); last = round(end) if end is not None else None
    created = []
    channel = max((s.channel for s in editor.strips), default=0) + 1
    if channel > 128: raise ValueError('No free sequencer channels')
    try:
        while last is None or cursor < last:
            strip = editor.strips.new_sound('Minecraft: '+obj.name, str(path), channel=channel, frame_start=cursor)
            created.append(strip)
            strip.volume = max(0, obj['volume'])
            enable_driver(strip,obj,'sound_enabled')
            duration = strip.frame_final_duration
            if duration < 1: raise ValueError('Audio file has no playable duration')
            if last is not None: strip.frame_final_end = min(strip.frame_final_end, last)
            cursor += duration + max(0, round(obj['loop_delay_frames']))
            if not cue.get('looping') or last is None: break
        return created
    except Exception:
        for strip in created: editor.strips.remove(strip)
        raise


class WORLDEXPORT_OT_sound_place(Operator):
    bl_idname = 'worldexport.sound_place'
    bl_label = 'Place Chosen Audio in Sequencer'
    bl_options = {'REGISTER', 'UNDO'}

    @classmethod
    def poll(cls, context): return context.object is not None and META in context.object

    def execute(self, context):
        try: strips = place_audio(context.scene, context.object)
        except Exception as error:
            self.report({'ERROR'}, str(error)); return {'CANCELLED'}
        self.report({'INFO'}, f'Placed {len(strips)} strip(s); recorded pitch and 3D attenuation remain cue metadata')
        return {'FINISHED'}


class WORLDEXPORT_PT_sound_cues(Panel):
    bl_label = 'Sound Cues'
    bl_space_type = 'VIEW_3D'; bl_region_type = 'UI'; bl_category = 'Replay Export'

    def draw(self, context):
        layout = self.layout
        obj = context.object
        if obj is None or META not in obj:
            layout.label(text='Select an imported sound cue')
            layout.label(text='Import stores references without audio')
            return
        cue = json.loads(obj[META])
        layout.label(text=obj['sound_id'])
        layout.label(text=cue['category'] + ' / ' + cue['source']['association'])
        layout.label(text='File: '+obj['resolved_file'])
        if obj['source_entity_id']: layout.label(text='Entity: '+obj['source_entity_id'])
        layout.prop(obj, '["sound_enabled"]', text='Enable Sound')
        layout.prop(obj, '["volume"]', text='Volume')
        layout.prop(obj, '["pitch"]', text='Recorded Pitch (metadata)')
        layout.prop(obj, '["replacement_file"]', text='Replacement File')
        layout.prop(context.scene, 'worldexport_sound_asset_index', text='Stock Asset Index')
        layout.operator('worldexport.sound_stock')
        layout.operator('worldexport.sound_file')
        layout.operator('worldexport.sound_place')
        layout.label(text='Placement adds strips; repeat calls add copies')
        layout.label(text='Pitch / 3D attenuation: apply in audio mix')


CLASSES = (WORLDEXPORT_OT_sound_stock, WORLDEXPORT_OT_sound_file, WORLDEXPORT_OT_sound_place, WORLDEXPORT_PT_sound_cues)
def register():
    candidates = []
    for directory in (Path.home()/'AppData/Roaming/PrismLauncher/assets/indexes', Path.home()/'.local/share/PrismLauncher/assets/indexes'):
        if directory.is_dir(): candidates.extend(directory.glob('*.json'))
    guess = str(max(candidates, key=lambda p:p.stat().st_mtime)) if candidates else ''
    bpy.types.Scene.worldexport_sound_asset_index = StringProperty(name='Stock Asset Index', subtype='FILE_PATH', default=guess,
        description='Choose the Minecraft asset index matching this recording to resolve stock samples')
    for cls in CLASSES: bpy.utils.register_class(cls)
def unregister():
    del bpy.types.Scene.worldexport_sound_asset_index
    for cls in reversed(CLASSES): bpy.utils.unregister_class(cls)
