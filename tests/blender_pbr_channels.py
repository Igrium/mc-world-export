"""Render numeric LabPBR/SEUS channel checks in Blender, not just node inspection."""
import json
import os
from pathlib import Path
import struct
import sys
import tempfile
from types import SimpleNamespace
import zlib

import bpy

repository = Path(__file__).resolve().parents[1]
sys.path.insert(0,os.environ.get('WORLDEXPORT_ADDON_ROOT',str(repository)))
from worldexport_blender import pbr_packs

def png(pixel):
    def chunk(tag,data):
        return struct.pack('>I',len(data))+tag+data+struct.pack('>I',zlib.crc32(tag+data)&0xffffffff)
    return (b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',2,2,8,6,0,0,0))+
            chunk(b'IDAT',zlib.compress((b'\0'+bytes(pixel)*2)*2))+chunk(b'IEND',b''))

bpy.ops.wm.read_factory_settings(use_empty=True)
scene=bpy.context.scene
scene.render.engine='CYCLES'; scene.cycles.samples=64
scene.render.resolution_x=8; scene.render.resolution_y=8; scene.render.resolution_percentage=100
scene.world=bpy.data.worlds.new('Black'); scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value=0
scene.render.image_settings.file_format='OPEN_EXR'; scene.render.image_settings.color_depth='32'
scene.render.image_settings.color_mode='RGBA'
bpy.ops.mesh.primitive_plane_add(size=2)
plane=bpy.context.object
camera_data=bpy.data.cameras.new('Channel camera');camera=bpy.data.objects.new('Channel camera',camera_data)
scene.collection.objects.link(camera);camera.location=(0,0,2);camera_data.type='ORTHO';camera_data.ortho_scale=1
scene.camera=camera
aov=scene.view_layers[0].aovs.add(); aov.name='PBRValue'; aov.type='VALUE'
scene.use_nodes=True
cn=scene.node_tree.nodes; cl=scene.node_tree.links
cn.clear(); render_layer=cn.new('CompositorNodeRLayers'); composite=cn.new('CompositorNodeComposite')
cl.new(render_layer.outputs['PBRValue'],composite.inputs['Image'])
base=bpy.data.materials.new('Channel fixture');base.use_nodes=True
base.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value=(.5,.5,.5,1)

def sample(spec, socket, format='LABPBR', normal=False):
    settings=SimpleNamespace(format=format,replace_base=False,normal_strength=1,emission_strength=10,use_ao=False)
    passes={'specular':png(spec)}
    if normal: passes['normal']=png((128,128,64,255))
    material=pbr_packs.add_pbr(base,'minecraft/textures/block/test.png',None,passes,settings,{})
    nodes,links=material.node_tree.nodes,material.node_tree.links
    bsdf=nodes['Principled BSDF']
    if socket=='encoded_normal':
        encoded=next(n for n in nodes if n.label=='Blender tangent normal').outputs['Color']
        separate=nodes.new('ShaderNodeSeparateColor');links.new(encoded,separate.inputs['Color'])
        source=separate.outputs['Blue']
    else:
        source=bsdf.inputs[socket].links[0].from_socket
    aov_node=nodes.new('ShaderNodeOutputAOV');aov_node.aov_name='PBRValue';links.new(source,aov_node.inputs['Value'])
    emission=nodes.new('ShaderNodeEmission');emission.inputs['Strength'].default_value=1
    links.new(source,emission.inputs['Color'])
    links.new(emission.outputs[0],nodes['Material Output'].inputs['Surface'])
    plane.data.materials.clear();plane.data.materials.append(material)
    with tempfile.TemporaryDirectory() as directory:
        scene.render.filepath=str(Path(directory)/'channel.exr')
        bpy.ops.render.render(write_still=True)
        image=bpy.data.images.load(scene.render.filepath,check_existing=False)
        image.colorspace_settings.name='Non-Color'
        values=list(image.pixels[4*(4*8+4):4*(4*8+4)+3])
        bpy.data.images.remove(image)
    return values

cases={
    'roughness':(sample((191,230,0,255),'Roughness')[0],(1-191/255)**2),
    'metal_230':(sample((191,230,0,255),'Metallic')[0],1),
    'dielectric_229':(sample((191,229,0,255),'Metallic')[0],0),
    'alpha_255_emission_off':(sample((191,10,0,255),'Emission Strength')[0],0),
    'alpha_254_emission_full':(sample((191,10,0,254),'Emission Strength')[0],10),
    'seus_metal':(sample((191,128,64,255),'Metallic',format='SEUS')[0],128/255),
    'seus_emission':(sample((191,128,64,255),'Emission Strength',format='SEUS')[0],64/255*10),
    'normal_z_reconstructed_not_ao':(sample((191,10,0,255),'encoded_normal',normal=True)[2],1),
}
print('PBR_CHANNEL_VALUES',json.dumps(cases),flush=True)
for name,(actual,expected) in cases.items():
    assert abs(actual-expected)<.002,(name,actual,expected)
print('PBR_CHANNELS_OK',json.dumps(cases),flush=True)
