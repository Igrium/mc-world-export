from io import BytesIO, StringIO
import math
from typing import IO
from bmesh import new
from bpy.types import Context
from mathutils import Matrix, Quaternion, Vector

from ..vcap.import_obj import load as load_obj
import xml.etree.ElementTree as ET
import bpy

def load_entity(file: IO[str], context: Context):
    
    tree = ET.parse(file)
    entity = tree.getroot()
    
    model = entity.find('model')
    if model is None:
        raise Exception("Entity XML is missing model tag.")
    
    mesh = model.find('mesh')

    if mesh is not None:
        obj = BytesIO(bytes(mesh.text, 'utf-8'))
        meshes, mats = load_obj(context, obj)
        
        for mesh in meshes:
            new_object = bpy.data.objects.new(mesh.name, mesh)
            context.scene.collection.objects.link(new_object)
            new_object.rotation_euler[0] = math.radians(90)
    else:
        print("Warning: no mesh found in entity XML.")

    parse_armature(model, context)


def parse_armature(model: ET.Element, context: Context, name="entity"):
    armature = bpy.data.armatures.new(name)
    obj = bpy.data.objects.new(name, armature)

    context.scene.collection.objects.link(obj)
    context.view_layer.objects.active = obj

    bpy.ops.object.mode_set(mode='EDIT')

    edit_bones = armature.edit_bones
    id = 0
    for element in model:
        if element.tag != 'bone': continue
        attrib = element.attrib

        if 'name' in attrib.keys():
            name = attrib['name']
        else:
            name = f'bone{id}'

        if 'len' in attrib.keys():
            length = float(attrib['len'])
        else:
            length = '.16'

        bone = edit_bones.new(name)
        bone.head = [0, 0, 0]
        bone.tail = [0, length, 0]

        if 'pos' in attrib:
            pos = Vector(map(float, attrib['pos'].split(',')))
        else:
            pos = Vector()
        
        if 'rot' in attrib:
            rot = Quaternion(map(float, attrib['rot'].split(',')))
        else:
            rot = Quaternion()

        transformation: Matrix = Matrix.Translation(pos) @ rot.to_matrix().to_4x4()

        bone.transform(transformation)

        id += id

    bpy.ops.object.mode_set(mode='OBJECT')
    obj.rotation_euler[0] = math.radians(90)
    return armature
    ...