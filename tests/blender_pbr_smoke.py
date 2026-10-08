"""Blender smoke test: exact atlas mapping, pack priority, apply/restore and rendering.

Run with Blender --background --factory-startup --python this.py --
  exported.replay output_directory resource_pack.zip [more_packs.zip ...]
"""
import hashlib
import json
import os
import math
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import zipfile

import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index('--')+1:]
replay, output, *paths = args
output = Path(output); output.mkdir(parents=True, exist_ok=True)
# In the repository this script lives in tests/.
repository = Path(__file__).resolve().parents[1]
sys.path.insert(0, os.environ.get('WORLDEXPORT_ADDON_ROOT',str(repository)))
import worldexport_blender
from worldexport_blender import pbr_packs

bpy.ops.wm.read_factory_settings(use_empty=True)
worldexport_blender.register()
scene = bpy.context.scene
scene.render.fps = 20
assert bpy.ops.worldexport.import_replay(filepath=replay) == {'FINISHED'}
scene.frame_start=0; scene.frame_end=19; scene.frame_set(19)
bpy.context.view_layer.update()

def fingerprint():
    result = {}
    for obj in scene.objects:
        if obj.type == 'MESH':
            mesh = obj.data
            geometry = [[list(v.co) for v in mesh.vertices],
                        [list(p.vertices) for p in mesh.polygons],
                        [[list(uv.uv) for uv in layer.data] for layer in mesh.uv_layers],
                        [[(g.group,g.weight) for g in v.groups] for v in mesh.vertices]]
            result[obj.name] = hashlib.sha256(json.dumps(geometry).encode()).hexdigest()
        if obj.animation_data and obj.animation_data.action:
            result[obj.name+':action'] = [obj.animation_data.action.name,
                                         list(obj.animation_data.action.frame_range)]
    return result

before = fingerprint()
slot_counts = {obj.name:len(obj.data.materials) for obj in scene.objects if obj.type=='MESH'}
original_faces = {obj.name:[obj.data.materials[p.material_index].name for p in obj.data.polygons]
                  for obj in scene.objects if obj.type=='MESH'}
world = bpy.data.materials.get('world')
metadata = pbr_packs.metadata(world)
assert len(metadata['pbrAtlas']) > 100
iron = next(s for s in metadata['pbrAtlas'] if s['resource'].endswith('/iron_block.png'))
u0,v0,u1,v1 = iron['uv']
index = pbr_packs.AtlasIndex(metadata['pbrAtlas'])
assert pbr_packs.find_sprite(index,[(u0+1e-5,v0+1e-5),(u1-1e-5,v1-1e-5)]) == iron
assert pbr_packs.find_sprite(index,[(u0,v0),(u1+0.1,v1)]) is None

stack = pbr_packs.PackStack(paths)
iron_passes = stack.passes(iron['resource'])
assert 'normal' in iron_passes and 'specular' in iron_passes
with tempfile.TemporaryDirectory() as directory:
    overlay = Path(directory)/'override.zip'
    key='assets/minecraft/textures/block/stone_s.png'
    with zipfile.ZipFile(overlay,'w') as archive:
        archive.writestr('pack.mcmeta','{"pack":{"description":"Test","pack_format":84}}')
        archive.writestr(key,iron_passes['specular'])
    priority = pbr_packs.PackStack(paths+[str(overlay)])
    assert priority.read(key) == iron_passes['specular']
    assert priority.read('assets/'+iron['resource']) == iron_passes['base']
    priority.close()
stack.close()

for path in paths:
    scene.worldexport_pbr_packs.add().path=path
scene.worldexport_pbr_format='LABPBR'
scene.worldexport_pbr_replace_base=True
assert bpy.ops.worldexport.pbr_apply() == {'FINISHED'}
stats=json.loads(scene['worldexport_pbr_report'])
assert stats['faces']>100 and stats['materials']>3
assert before == fingerprint(), 'Geometry, UVs, weights or animation changed'
assert bpy.ops.worldexport.pbr_restore() == {'FINISHED'}
for obj in scene.objects:
    if obj.type=='MESH':
        assert original_faces[obj.name] == [obj.data.materials[p.material_index].name for p in obj.data.polygons]
        assert slot_counts[obj.name] == len(obj.data.materials)
assert before == fingerprint()
assert bpy.ops.worldexport.pbr_apply() == {'FINISHED'}
assert before == fingerprint()
# A corrupt later pack must roll back assignments, including already-updated slots.
def assignments():
    return {obj.name:([m.name if m else None for m in obj.data.materials],
                      [p.material_index for p in obj.data.polygons])
            for obj in scene.objects if obj.type=='MESH'}
assigned=assignments()
corrupt_resource=next(m.get('worldexport_pbr_resource') for obj in scene.objects if obj.type=='MESH'
                      for m in obj.data.materials if m and m.get(pbr_packs.SOURCE))
with tempfile.TemporaryDirectory() as directory:
    corrupt=Path(directory)/'corrupt.zip'
    with zipfile.ZipFile(corrupt,'w') as archive:
        archive.writestr('pack.mcmeta','{"pack":{"description":"Test","pack_format":84}}')
        archive.writestr('assets/'+corrupt_resource[:-4]+'_s.png',b'Invalid PNG')
    settings=SimpleNamespace(format='LABPBR',replace_base=True,normal_strength=1,
                             emission_strength=10,use_ao=False)
    try:
        pbr_packs.apply_packs(list(scene.objects),paths+[str(corrupt)],settings)
        raise AssertionError('Corrupt image was accepted')
    except (RuntimeError,ValueError):
        pass
assert assigned==assignments(), 'Failed application changed material assignments'
assert before==fingerprint()
stats['failed_apply_rollback_verified']=True
stats['geometry_uv_weights_actions_unchanged']=True
stats['pack_priority_verified']=True
stats['restore_verified']=True
stats['blender']=bpy.app.version_string
stats['atlas_sprites']=len(metadata['pbrAtlas'])
stats['pack_names']=[Path(p).name for p in paths]

# Save the imported, animated scene with its PBR settings.
bpy.ops.wm.save_as_mainfile(filepath=str(output/'pbr-replay.blend'))

# Render a small material comparison scene using the user's real pack images.
# Its explicit atlas UVs also exercise full-resolution sampling on tiny atlas tiles.
samples=bpy.data.scenes.new('PBR Material Samples')
bpy.context.window.scene=samples
samples.render.engine='CYCLES'; samples.cycles.samples=32
samples.render.resolution_x=960; samples.render.resolution_y=540
samples.render.resolution_percentage=100
samples.world=bpy.data.worlds.new('Studio lighting'); samples.world.use_nodes=True
samples.world.node_tree.nodes['Background'].inputs['Color'].default_value=(.4,.45,.55,1)
samples.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.35

for i,name in enumerate(('stone','iron_block','copper_block','glowstone')):
    resource='minecraft/textures/block/'+name+'.png'
    sprite=next(s for s in metadata['pbrAtlas'] if s['resource']==resource)
    mat=next((m for m in bpy.data.materials if m.get('worldexport_pbr_resource')==resource and m.get(pbr_packs.SOURCE)=='world'),None)
    if not mat:
        stack=pbr_packs.PackStack(paths)
        settings=SimpleNamespace(format='LABPBR',replace_base=True,normal_strength=1,emission_strength=10,use_ao=False)
        mat=pbr_packs.add_pbr(world,resource,sprite['uv'],stack.passes(resource),settings,metadata)
        stack.close()
    bpy.ops.mesh.primitive_cube_add(size=1.5,location=(i*2.15-3.225,0,.8))
    cube=bpy.context.object; cube.name='PBR sample '+name
    cube.data.materials.append(mat)
    u0,v0,u1,v1=sprite['uv']
    for poly in cube.data.polygons:
        for loop,uv in zip(poly.loop_indices,((0,0),(1,0),(1,1),(0,1))):
            cube.data.uv_layers.active.data[loop].uv=(u0+uv[0]*(u1-u0),v0+uv[1]*(v1-v0))
    color=cube.data.color_attributes.new(name='Color',type='FLOAT_COLOR',domain='CORNER')
    for datum in color.data: datum.color=(1,1,1,1)
    bevel=cube.modifiers.new('Tiny edge bevel','BEVEL'); bevel.width=.018; bevel.segments=2

bpy.ops.mesh.primitive_plane_add(size=200)
floor=bpy.context.object
floor_mat=bpy.data.materials.new('Studio floor'); floor_mat.diffuse_color=(.12,.15,.18,1)
floor.data.materials.append(floor_mat)
for location,energy,size in (((-2,-3,6),1800,5),((4,2,5),1400,4)):
    light=bpy.data.lights.new('Softbox','AREA'); light.energy=energy; light.shape='DISK'; light.size=size
    obj=bpy.data.objects.new('Softbox',light); samples.collection.objects.link(obj)
    obj.location=location; obj.rotation_euler=(Vector((0,0,0))-obj.location).to_track_quat('-Z','Y').to_euler()
camera_data=bpy.data.cameras.new('Samples camera')
camera=bpy.data.objects.new('Samples camera',camera_data); samples.collection.objects.link(camera)
camera.location=(7,-13,7)
camera.rotation_euler=(Vector((0,0,.7))-camera.location).to_track_quat('-Z','Y').to_euler()
camera_data.lens=48; samples.camera=camera
samples.render.image_settings.file_format='PNG'
samples.render.filepath=str(output/'pbr-materials.png')
bpy.ops.render.render(write_still=True)
bpy.context.window.scene=scene
bpy.ops.wm.save_as_mainfile(filepath=str(output/'pbr-replay.blend'))
(output/'result.json').write_text(json.dumps(stats,indent=2)+'\n')
print('PBR_SMOKE_OK',json.dumps(stats),flush=True)
