"""Verify actual display/head assets, metadata, model switches and pack appearance in Blender."""
import hashlib, io, json, os, struct, sys, tempfile, zipfile, zlib
from pathlib import Path
from types import SimpleNamespace
import bpy
from mathutils import Matrix, Vector, Quaternion

repository=Path(__file__).resolve().parents[1]
sys.path.insert(0,os.environ.get('WORLDEXPORT_ADDON_ROOT',str(repository)))
import worldexport_blender
from worldexport_blender import pbr_packs, appearance
from worldexport_blender.entity.captured_entity import read_anim_file

# Known non-white RGB values must survive the bundled import and part split.
from worldexport_blender import obj_loader
from worldexport_blender.mesh import mesh_utils
with tempfile.TemporaryDirectory() as directory:
    fixture=Path(directory)/'colors.obj'
    fixture.write_text('v 0 0 0 1 0 0\nv 1 0 0 1 0 0\nv 1 1 0 1 0 0\nv 0 1 0 1 0 0\nv 2 0 0 0 1 0\nv 3 0 0 0 1 0\nv 3 1 0 0 1 0\nv 2 1 0 0 1 0\ng red\nf 1 2 3 4\ng green\nf 5 6 7 8\n'.replace('\\n','\n'))
    source=next(iter(obj_loader.load_obj_bundled(bpy.context,str(fixture),import_vertex_groups=True)))
    split=mesh_utils.split_vertex_groups(source,lambda name:name=='red')
    for obj,expected in ((source,(0,1,0,1)),(split['red'],(1,0,0,1))):
        attribute=obj.data.color_attributes['Color']
        assert len(attribute.data)==4
        assert all(max(abs(a-b) for a,b in zip(c.color,expected))<1e-6 for c in attribute.data)
        mesh=obj.data;bpy.data.objects.remove(obj,do_unlink=True);bpy.data.meshes.remove(mesh)

replay, output, legacy=sys.argv[sys.argv.index('--')+1:]
output=Path(output).resolve(); output.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
worldexport_blender.register()
# Old replay archives still import, with an explicit unknown/legacy coverage report.
assert bpy.ops.worldexport.import_replay(filepath=legacy)=={'FINISHED'}
assert any(json.loads(c.get('worldexport_capture_report','{}')).get('legacy') for c in bpy.data.collections)
bpy.ops.wm.read_factory_settings(use_empty=True)
scene=bpy.context.scene; scene.render.fps=20
assert bpy.ops.worldexport.import_replay(filepath=replay)=={'FINISHED'}
with zipfile.ZipFile(replay) as archive:
    manifest=json.loads(archive.read('appearance.json'))
    data=manifest['entities']
    pet_name=next(n for n,d in data.items() if d.get('entityType')=='minecraft:item_display')
    pet_data=data[pet_name]
    last_frame=pet_data['lastFrame']
    assert pet_data['geometryCaptured']
    assert len(pet_data['itemSelectors'])>=2, pet_data
    selectors=[d['itemId'] for d in pet_data['itemSelectors'].values()]
    assert selectors==['minecraft:stone','minecraft:gold_block'], selectors
    curves={}; read_anim_file(io.BytesIO(archive.read(pet_name+'.anim')),curves)
    assert len(curves['display/right'])>0
    scale=curves['display/left'][0].scale_x
    assert max(scale)-min(scale)>.3,scale
    head_name=next(n for n,d in data.items() if d.get('headAttachment'))
    assert data[head_name]['itemParts']
    assert pet_data['itemParts']
    for unsupported in ('minecraft:text_display','minecraft:block_display'):
        item=next(d for d in data.values() if d.get('entityType')==unsupported)
        assert not item['geometryCaptured'] and 'renderer_has_no_geometry_adapter' in item['warnings']
# Item colors must reach Blender's material graph, not only the stored OBJ vertices.
item_materials=[m for m in bpy.data.materials if json.loads(m.get('worldexport_texture_metadata','{}')).get('item')]
assert item_materials
for material in item_materials:
    assert json.loads(material['worldexport_texture_metadata']).get('vertexTint')
    assert any(n.type=='GROUP' and n.node_tree and any(c.type=='VERTEX_COLOR' for c in n.node_tree.nodes) for n in material.node_tree.nodes)
roots={o['worldexport_instance_id']:o for o in scene.objects if 'worldexport_instance_id' in o and o.type in {'ARMATURE','EMPTY'}}
assert len(roots)==len(data), (len(roots),len(data))
pet=roots[pet_data['instanceId']]
hat=roots[data[head_name]['instanceId']]
assert 'display/left' in pet.pose.bones and 'display/right' in pet.pose.bones
head_parts=[name for name in hat.pose.bones.keys() if name.startswith('head/item.')]
assert head_parts and hat.pose.bones[head_parts[0]].parent.name==data[head_name]['headAttachment']
scene.frame_set(0); bpy.context.view_layer.update()
scale0=list(pet.pose.bones['display/left'].scale)
scene.frame_set(last_frame); bpy.context.view_layer.update()
scale18=list(pet.pose.bones['display/left'].scale)
assert max(abs(a-b) for a,b in zip(scale0,scale18))>.3,(scale0,scale18)
# Compare evaluated Blender bounds to the full source-space transform chain.
# This checks coordinate conversion, both display rotations, nonuniform scale and model visibility.
with zipfile.ZipFile(replay) as archive:
    lines=archive.read(pet_name+'.obj').decode().splitlines()
    parents=json.loads(archive.read('entities.json'))[pet_name]
vertices=[];groups={};current=[]
for line in lines:
    if line.startswith('v '):vertices.append(Vector(tuple(float(x) for x in line.split()[1:4])))
    elif line.startswith('g '):current=line.split()[1:]
    elif line.startswith('f '):
        for group in current:groups.setdefault(group,set()).update(int(x.split('/')[0])-1 for x in line.split()[1:])
def pose_at(name,frame):
    curve=next(c for c in curves[name] if c.tick_offset<=frame<c.end_tick)
    index=frame-curve.tick_offset
    pos=Vector((curve.pos_x[index],curve.pos_y[index],curve.pos_z[index])) if curve.has_position() else Vector()
    rot=Quaternion((curve.rot_w[index],curve.rot_x[index],curve.rot_y[index],curve.rot_z[index])) if curve.has_rotation() else Quaternion()
    scale=(curve.scale_x[index],curve.scale_y[index],curve.scale_z[index],1) if curve.has_scale() else (1,1,1,1)
    return Matrix.Translation(pos)@rot.to_matrix().to_4x4()@Matrix.Diagonal(scale)
def extents(points):return tuple(f(point[i] for point in points) for i in range(3) for f in (min,max))
for frame in (0,last_frame):
    scene.frame_set(frame);bpy.context.view_layer.update();expected=[]
    for name,indices in groups.items():
        if not any(c.tick_offset<=frame<c.end_tick for c in curves[name]):continue
        chain=[];part=name
        while part!='transform':chain.append(part);part=parents.get(part,'transform')
        transform=pose_at('transform',frame)
        for part in reversed(chain):transform=transform@pose_at(part,frame)
        for index in indices:
            point=transform@vertices[index];expected.append(Vector((point.x,-point.z,point.y)))
    actual=[]
    for obj in scene.objects:
        if obj.type!='MESH' or obj.get('worldexport_instance_id')!=pet_data['instanceId'] or obj.hide_render:continue
        evaluated=obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
        actual.extend(obj.matrix_world@v.co for v in evaluated.data.vertices)
    assert actual and expected
    assert max(abs(a-b) for a,b in zip(extents(actual),extents(expected)))<1e-4,(frame,extents(actual),extents(expected))
# Detect malformed/unrecognized sidecars without claiming successful coverage.
assert manifest['captureReport']['withoutGeometry']==2
assert any(json.loads(c.get('worldexport_capture_report','{}')).get('instances')==len(data) for c in bpy.data.collections)

def fingerprint():
    items={}
    for obj in scene.objects:
        if obj.type=='MESH':
            items[obj.name]=[[list(v.co) for v in obj.data.vertices],[list(p.vertices) for p in obj.data.polygons],
                [[list(uv.uv) for uv in layer.data] for layer in obj.data.uv_layers],
                [[(g.group,g.weight) for g in v.groups] for v in obj.data.vertices]]
        if obj.animation_data and obj.animation_data.action:
            action=obj.animation_data.action
            keys=[]
            for layer in action.layers:
                for strip in layer.strips:
                    for bag in strip.channelbags:
                        keys.extend((c.data_path,c.array_index,[(tuple(k.co),k.interpolation) for k in c.keyframe_points]) for c in bag.fcurves)
            items[obj.name+':animation']=keys
    return hashlib.sha256(json.dumps(items).encode()).hexdigest()

before=fingerprint()
def png(pixel):
    def chunk(tag,data): return struct.pack('>I',len(data))+tag+data+struct.pack('>I',zlib.crc32(tag+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',2,2,8,6,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes(pixel)*2)*2))+chunk(b'IEND',b'')
settings=SimpleNamespace(format='LABPBR',replace_base=True,normal_strength=1,emission_strength=10,use_ao=False)
with tempfile.TemporaryDirectory() as directory:
    colors=Path(directory)/'color-only.zip'
    with zipfile.ZipFile(colors,'w') as z:
        z.writestr('pack.mcmeta','{"pack":{"description":"Test","min_format":[88,0],"max_format":[97,1]}}')
        z.writestr('assets/minecraft/textures/block/grass_block_side.png',png((70,40,10,255)))
        z.writestr('assets/minecraft/textures/block/grass_block_side_overlay.png',png((0,180,20,128)))
        z.writestr('assets/minecraft/textures/block/grass_block_top.png',png((0,120,20,255)))
    report=pbr_packs.apply_packs(list(scene.objects),[str(colors)],settings)
    assert report['faces']>0 and report['materials']>=2,report
    sides=[m for m in bpy.data.materials if m.get('worldexport_pbr_resource','').endswith('/grass_block_side.png')]
    assert sides
    samplers=[n for n in sides[0].node_tree.nodes if n.type=='TEX_IMAGE']
    assert len(samplers)==2 and all(n.image.get('worldexport_pbr_image') for n in samplers)
    assert samplers[0].image != samplers[1].image
    assert fingerprint()==before
    assert bpy.ops.worldexport.pbr_restore()=={'FINISHED'}
    assert fingerprint()==before
    # PBR-only mode must not apply a pack that contains only base colors.
    settings.replace_base=False
    assert pbr_packs.apply_packs(list(scene.objects),[str(colors)],settings)['faces']==0
    settings.replace_base=True
    stats=pbr_packs.apply_packs(list(scene.objects),[str(colors)],settings)
    assert stats['faces']>0 and stats['materials']>=2,stats
    assert fingerprint()==before

# Render the two vanilla item-display variants and a paper head attachment on the exported armor stand.
samples=bpy.data.scenes.new('Captured Vanilla Models')
bpy.context.window.scene=samples
samples.render.engine='CYCLES';samples.cycles.samples=32
samples.render.resolution_x=960;samples.render.resolution_y=540;samples.render.resolution_percentage=100
samples.world=bpy.data.worlds.new('Studio');samples.world.use_nodes=True
samples.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.4
centers=[]
for index,(root,frame) in enumerate(((pet,0),(pet,last_frame),(hat,0))):
    bpy.context.window.scene=scene;scene.frame_set(frame);bpy.context.view_layer.update()
    meshes=[o for o in scene.objects if o.type=='MESH' and o.get('worldexport_instance_id')==root['worldexport_instance_id'] and not o.hide_render]
    points=[];copies=[]
    for obj in meshes:
        evaluated=obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
        mesh=bpy.data.meshes.new_from_object(evaluated)
        clone=bpy.data.objects.new(obj.name+' sample',mesh);samples.collection.objects.link(clone)
        clone.matrix_world=obj.matrix_world.copy()
        points.extend(clone.matrix_world@v.co for v in mesh.vertices);copies.append(clone)
    assert points
    lower=Vector(tuple(min(p[i] for p in points) for i in range(3)));upper=Vector(tuple(max(p[i] for p in points) for i in range(3)))
    center=(lower+upper)/2;target=Vector((index*2.5-2.5,0,(upper.z-lower.z)/2+.05))
    for clone in copies:clone.matrix_world.translation+=target-center
bpy.context.window.scene=samples
bpy.ops.mesh.primitive_plane_add(size=200)
floor=bpy.context.object;material=bpy.data.materials.new('Floor');material.diffuse_color=(.13,.16,.2,1);floor.data.materials.append(material)
for location,energy in (((-3,-4,6),1300),((4,2,5),900)):
    light=bpy.data.lights.new('Softbox','AREA');light.energy=energy;light.size=4
    obj=bpy.data.objects.new('Softbox',light);samples.collection.objects.link(obj);obj.location=location
    obj.rotation_euler=(Vector((0,0,.6))-obj.location).to_track_quat('-Z','Y').to_euler()
camera_data=bpy.data.cameras.new('Preview');camera=bpy.data.objects.new('Preview',camera_data);samples.collection.objects.link(camera)
camera.location=(6,-12,6);camera.rotation_euler=(Vector((0,0,.8))-camera.location).to_track_quat('-Z','Y').to_euler();camera_data.lens=50;samples.camera=camera
samples.render.filepath=str(output/'appearance-preview.png');samples.render.image_settings.file_format='PNG'
bpy.ops.render.render(write_still=True)
assert (output/'appearance-preview.png').is_file()
bpy.context.window.scene=scene;scene.frame_set(0)
bpy.ops.wm.save_as_mainfile(filepath=str(output/'appearance-replay.blend'))
result={'blender':bpy.app.version_string,'capture_report':manifest['captureReport'],'item_selectors':selectors,
    'head_attachment':data[head_name]['headAttachment'],'display_scale0':scale0,'display_scale_last':scale18,
    'legacy_import_verified':True,'color_only_and_overlay_verified':True,'pbr_only_mode_verified':True,
    'geometry_uv_weights_all_animation_keys_unchanged':True,'evaluated_pet_bounds_match_source_transforms':True,'item_vertex_colors_verified':True,'synthetic_color_report':stats}
(output/'result.json').write_text(json.dumps(result,indent=2)+'\n')
print('APPEARANCE_SMOKE_OK '+json.dumps(result),flush=True)
