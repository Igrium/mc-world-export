"""Apply custom Minecraft PBR packs using identities saved by Replay Exporter.

No texture guessing, UV edits, geometry edits or global MCprep configuration.
Later packs override earlier packs independently for each texture resource.
"""
import hashlib
import json
import re
from pathlib import Path
import tempfile
import zipfile

import bpy
from bpy.props import (BoolProperty, CollectionProperty, EnumProperty, FloatProperty,
                       IntProperty, StringProperty)
from bpy.types import Operator, OperatorFileListElement, Panel, PropertyGroup, UIList
from bpy_extras.io_utils import ImportHelper

META = "worldexport_texture_metadata"
SOURCE = "worldexport_pbr_original"


class PackStack:
    """Resolve only requested resources; never unpack entire resource packs."""
    def __init__(self, paths):
        self.sources = []
        try:
            for raw in paths:
                path = Path(bpy.path.abspath(str(raw))).resolve()
                if path.is_dir():
                    if not (path / 'pack.mcmeta').is_file():
                        raise ValueError(f'Not a resource-pack folder: {path.name}')
                    self.sources.append((path, None))
                elif path.is_file() and zipfile.is_zipfile(path):
                    archive = zipfile.ZipFile(path)
                    if 'pack.mcmeta' not in archive.namelist():
                        archive.close()
                        raise ValueError(f'ZIP has no root pack.mcmeta: {path.name}')
                    self.sources.append((path, archive))
                else:
                    raise ValueError(f'Resource pack not found: {path.name}')
        except Exception:
            self.close()
            raise

    def close(self):
        for _, archive in self.sources:
            if archive:
                archive.close()

    def read(self, resource):
        parts = resource.split('/')
        if '..' in parts or resource.startswith('/') or '\\' in resource:
            raise ValueError('Unsafe texture resource path')
        for path, archive in reversed(self.sources):
            if archive:
                try:
                    return archive.read(resource)
                except KeyError:
                    continue
            else:
                candidate = (path / resource).resolve()
                if not candidate.is_relative_to(path):
                    raise ValueError('Texture path escapes the resource pack')
                if candidate.is_file():
                    return candidate.read_bytes()
        return None

    def passes(self, resource):
        base = 'assets/' + resource
        stem = base[:-4] if base.endswith('.png') else base
        return {name: data for name, suffix in (('base', '.png'), ('normal', '_n.png'),
                                               ('specular', '_s.png'))
                if (data := self.read(stem + suffix)) is not None}


def load_image(data, resource, kind):
    digest = hashlib.sha256(data).hexdigest()
    key = f'{digest}:{kind}'
    for image in bpy.data.images:
        if image.get('worldexport_pbr_image') == key:
            return image
    with tempfile.TemporaryDirectory(prefix='replay-pbr-') as directory:
        path = Path(directory) / 'texture.png'
        path.write_bytes(data)
        image = bpy.data.images.load(str(path), check_existing=False)
        if min(image.size) <= 0:
            bpy.data.images.remove(image)
            raise ValueError(f'Unreadable PNG texture: {resource} ({kind})')
        image.name = resource + ':' + kind
        image.colorspace_settings.name = 'sRGB' if kind == 'base' else 'Non-Color'
        if kind != 'base':
            image.alpha_mode = 'CHANNEL_PACKED'
        image.pack()
    image['worldexport_pbr_image'] = key
    return image


def metadata(material):
    try:
        return json.loads(material.get(META, '{}'))
    except (TypeError, json.JSONDecodeError):
        return {}


def original(material):
    return bpy.data.materials.get(material.get(SOURCE, '')) or material


class AtlasIndex:
    def __init__(self, sprites):
        self.bins = {}
        for sprite in sprites:
            u0,v0,u1,v1 = sprite['uv']
            for x in range(max(0,int(u0*64)), min(63,int(u1*64))+1):
                for y in range(max(0,int(v0*64)), min(63,int(v1*64))+1):
                    self.bins.setdefault((x,y),[]).append(sprite)

    def candidates(self,u,v):
        return self.bins.get((min(63,int(u*64)),min(63,int(v*64))),[])


def find_sprite(sprites, coords):
    """Require the whole polygon to fit one rectangle, including atlas edge shrink."""
    low_u = min(uv[0] for uv in coords)
    high_u = max(uv[0] for uv in coords)
    low_v = min(uv[1] for uv in coords)
    high_v = max(uv[1] for uv in coords)
    center_u, center_v = (low_u + high_u) / 2, (low_v + high_v) / 2
    matches = []
    for sprite in sprites.candidates(center_u,center_v):
        u0, v0, u1, v1 = sprite['uv']
        if (u0 <= center_u < u1 and v0 <= center_v < v1 and
                low_u >= u0 - 1e-6 and high_u <= u1 + 1e-6 and
                low_v >= v0 - 1e-6 and high_v <= v1 + 1e-6):
            matches.append(sprite)
    return matches[0] if len(matches) == 1 else None


def resource_for_material(material, props):
    resource = props.get('textureResource')
    # Older exports retain direct entity texture identities in their material names.
    if not resource and '/textures/' in material.name and material.name.endswith('.png'):
        resource = material.name
    return resource


def add_pbr(material, resource, uv_rect, passes, settings, props):
    """Copy existing tint/opacity/overlay/animation nodes and add PBR samplers."""
    result = material.copy()
    result.name = f'{material.name} | PBR {resource}'
    result[SOURCE] = material.name
    if META in result:
        del result[META]
    tree = result.node_tree
    nodes, links = tree.nodes, tree.links
    principled = next((n for n in nodes if n.type == 'BSDF_PRINCIPLED'), None)
    if not principled:
        bpy.data.materials.remove(result)
        return None
    serial = 0

    def node(kind, label):
        nonlocal serial
        n = nodes.new(kind)
        n.label = label
        n.location = (-1400 + (serial % 4) * 230, -450 - (serial // 4) * 130)
        serial += 1
        return n

    def connect(value, socket):
        if isinstance(value, (int, float)):
            socket.default_value = value
        else:
            links.new(value, socket)

    def math(operation, a, b=0):
        n = node('ShaderNodeMath', operation)
        n.operation = operation
        connect(a, n.inputs[0]); connect(b, n.inputs[1])
        return n.outputs[0]

    uv = node('ShaderNodeTexCoord', 'Original replay UVs').outputs['UV']

    def local_uv(input_uv):
        if not uv_rect:
            return input_uv
        u0, v0, u1, v1 = uv_rect
        sub = node('ShaderNodeVectorMath', 'Atlas tile origin')
        sub.operation = 'SUBTRACT'
        links.new(input_uv, sub.inputs[0]); sub.inputs[1].default_value = (u0, v0, 0)
        scale = node('ShaderNodeVectorMath', 'Atlas tile to full-resolution texture')
        scale.operation = 'MULTIPLY'
        links.new(sub.outputs['Vector'], scale.inputs[0])
        scale.inputs[1].default_value = (1 / (u1 - u0), 1 / (v1 - v0), 1)
        return scale.outputs['Vector']

    mapped = local_uv(uv)
    samplers = {}
    for kind in ('normal', 'specular'):
        if kind not in passes:
            continue
        n = node('ShaderNodeTexImage', kind.title() + ' (packed data)')
        n.image = load_image(passes[kind], resource, kind)
        n.extension = 'EXTEND'; n.interpolation = 'Linear'
        links.new(mapped, n.inputs['Vector'])
        samplers[kind] = n

    if settings.replace_base and 'base' in passes:
        # Preserve existing color/alpha wiring, vertex tints and grass overlay nodes.
        # The first image is the primary diffuse sampler in both bundled OBJ importers.
        diffuse = next((n for n in nodes if n.type == 'TEX_IMAGE' and n not in samplers.values()), None)
        if diffuse:
            previous_uv = diffuse.inputs['Vector'].links[0].from_socket if diffuse.inputs['Vector'].is_linked else uv
            diffuse.image = load_image(passes['base'], resource, 'base')
            diffuse.interpolation = 'Linear'; diffuse.extension = 'EXTEND'
            links.new(local_uv(previous_uv), diffuse.inputs['Vector'])

    base_socket = principled.inputs['Base Color']
    color = base_socket.links[0].from_socket if base_socket.is_linked else None
    if color is None:
        constant = node('ShaderNodeRGB', 'Original base color')
        constant.outputs[0].default_value = base_socket.default_value
        color = constant.outputs[0]
    normal = samplers.get('normal')
    if normal:
        separate = node('ShaderNodeSeparateColor', 'Packed normal channels')
        links.new(normal.outputs['Color'], separate.inputs['Color'])
        red, green, blue = (separate.outputs[name] for name in ('Red', 'Green', 'Blue'))
        combine = node('ShaderNodeCombineColor', 'Blender tangent normal')
        links.new(red, combine.inputs['Red'])
        links.new(math('SUBTRACT', 1, green), combine.inputs['Green'])
        if settings.format == 'LABPBR':
            x, y = math('SUBTRACT', math('MULTIPLY', red, 2), 1), math('SUBTRACT', math('MULTIPLY', green, 2), 1)
            z = math('SQRT', math('MAXIMUM', math('SUBTRACT', 1,
                     math('ADD', math('MULTIPLY', x, x), math('MULTIPLY', y, y))), 0))
            links.new(math('MULTIPLY', math('ADD', z, 1), 0.5), combine.inputs['Blue'])
        else:
            links.new(blue, combine.inputs['Blue'])
        nm = node('ShaderNodeNormalMap', 'Custom surface detail')
        nm.inputs['Strength'].default_value = settings.normal_strength
        links.new(combine.outputs['Color'], nm.inputs['Color'])
        links.new(nm.outputs['Normal'], principled.inputs['Normal'])
        if settings.format == 'LABPBR' and settings.use_ao and color:
            ao = node('ShaderNodeMixRGB', 'Optional texture AO')
            ao.blend_type = 'MULTIPLY'; ao.inputs[0].default_value = 1
            links.new(color, ao.inputs[1]); links.new(blue, ao.inputs[2])
            color = ao.outputs[0]
            links.new(color, base_socket)

    specular = samplers.get('specular')
    if specular:
        separate = node('ShaderNodeSeparateColor', 'Packed specular channels')
        links.new(specular.outputs['Color'], separate.inputs['Color'])
        red, green, blue = (separate.outputs[name] for name in ('Red', 'Green', 'Blue'))
        roughness = math('SUBTRACT', 1, red)
        if settings.format == 'LABPBR':
            roughness = math('MULTIPLY', roughness, roughness)
            metal = math('GREATER_THAN', green, 229.5 / 255)
            root_f0 = math('SQRT', math('MINIMUM', green, 229 / 255))
            ior = math('DIVIDE', math('ADD', 1, root_f0), math('SUBTRACT', 1, root_f0))
            links.new(ior, principled.inputs['IOR'])
            principled.inputs['Specular IOR Level'].default_value = 0.5
            # Decode predefined conductor colors as normal-incidence F0 for Principled.
            nk = {230: ((2.9114,2.9497,2.5845),(3.0893,2.9318,2.7670)),
                  231: ((.18299,.42108,1.3734),(3.4242,2.3459,1.7704)),
                  232: ((1.3456,.96521,.61722),(7.4746,6.3995,5.3031)),
                  233: ((3.1071,3.1812,2.3230),(3.3314,3.3291,3.1350)),
                  234: ((.27105,.67693,1.3164),(3.6092,2.6248,2.2921)),
                  235: ((1.9100,1.8300,1.4400),(3.5100,3.4000,3.1800)),
                  236: ((2.3757,2.0847,1.8453),(4.2655,3.7153,3.1365)),
                  237: ((.15943,.14512,.13547),(3.9291,3.1900,2.3808))}
            if color:
                for code, (n, k) in nk.items():
                    choose = math('LESS_THAN', math('ABSOLUTE', math('SUBTRACT',
                                  math('MULTIPLY', green, 255), code)), 0.5)
                    mix = node('ShaderNodeMixRGB', f'LabPBR metal {code}')
                    connect(choose, mix.inputs[0]); links.new(color, mix.inputs[1])
                    mix.inputs[2].default_value = tuple(((a-1)**2+b*b)/((a+1)**2+b*b) for a,b in zip(n,k))+(1,)
                    color = mix.outputs[0]
                links.new(color, base_socket)
            sss = math('MINIMUM', math('MAXIMUM', math('DIVIDE', math('SUBTRACT',
                       math('MULTIPLY', blue, 255), 65), 190), 0), 1)
            links.new(sss, principled.inputs['Subsurface Weight'])
            alpha = specular.outputs['Alpha']
            emission = math('MULTIPLY', math('LESS_THAN', alpha, 254.5/255),
                       math('MULTIPLY', alpha, 255/254))
        else:
            metal, emission = green, blue
        links.new(roughness, principled.inputs['Roughness'])
        links.new(metal, principled.inputs['Metallic'])
        if color and not props.get('glint'):
            links.new(color, principled.inputs['Emission Color'])
            links.new(math('MULTIPLY', emission, settings.emission_strength), principled.inputs['Emission Strength'])
    result['worldexport_pbr_resource'] = resource
    return result


def apply_packs(objects, paths, settings):
    stack = PackStack(paths)
    cache, pass_cache, seen, atlas_cache = {}, {}, set(), {}
    stats = {'faces': 0, 'materials': 0, 'missing_maps': 0, 'missing_identity': 0,
             'animated_skipped': 0, 'unsupported_uv': 0}
    metadata_cache = {}
    rollback = []
    try:
        for obj in objects:
            if obj.type != 'MESH' or obj.data.as_pointer() in seen:
                continue
            mesh = obj.data
            seen.add(mesh.as_pointer())
            if not mesh.uv_layers.active:
                continue
            slots = list(mesh.materials)
            rollback.append((mesh, slots, [face.material_index for face in mesh.polygons]))
            slot_cache = {}
            for face in mesh.polygons:
                if face.material_index >= len(slots) or slots[face.material_index] is None:
                    continue
                base = original(slots[face.material_index])
                pointer = base.as_pointer()
                if pointer not in metadata_cache:
                    metadata_cache[pointer] = metadata(base)
                props = metadata_cache[pointer]
                rect = None
                if sprites := props.get('pbrAtlas'):
                    coords = [mesh.uv_layers.active.data[i].uv for i in face.loop_indices]
                    pointer = base.as_pointer()
                    if pointer not in atlas_cache:
                        atlas_cache[pointer] = AtlasIndex(sprites)
                    sprite = find_sprite(atlas_cache[pointer], coords)
                    if not sprite:
                        stats['unsupported_uv'] += 1; continue
                    if sprite.get('animated'):
                        stats['animated_skipped'] += 1; continue
                    resource, rect = sprite['resource'], sprite['uv']
                else:
                    resource = resource_for_material(base, props)
                    if not resource:
                        stats['missing_identity'] += 1; continue
                    if props.get('spritesheet'):
                        stats['animated_skipped'] += 1; continue
                if resource not in pass_cache:
                    pass_cache[resource] = stack.passes(resource)
                passes = pass_cache[resource]
                if not ('normal' in passes or 'specular' in passes):
                    stats['missing_maps'] += 1; continue
                key = (base.as_pointer(), resource, tuple(rect or ()))
                if key not in cache:
                    cache[key] = add_pbr(base, resource, rect, passes, settings, props)
                material = cache[key]
                if not material:
                    continue
                if key not in slot_cache:
                    # Reapplying replaces an existing matching slot instead of growing slots forever.
                    index = next((i for i,m in enumerate(mesh.materials) if m and
                        m.get(SOURCE) == base.name and m.get('worldexport_pbr_resource') == resource), None)
                    if index is None:
                        index = len(mesh.materials); mesh.materials.append(material)
                    else:
                        mesh.materials[index] = material
                    slot_cache[key] = index
                face.material_index = slot_cache[key]
                stats['faces'] += 1
        stats['materials'] = sum(m is not None for m in cache.values())
        return stats
    except Exception:
        # Restore assignments on failed application, including replacement of existing PBR slots.
        for mesh, slots, face_indices in rollback:
            for face, index in zip(mesh.polygons, face_indices):
                face.material_index = index
            while len(mesh.materials) > len(slots):
                mesh.materials.pop(index=len(mesh.materials)-1)
            for index, mat in enumerate(slots):
                mesh.materials[index] = mat
        raise
    finally:
        stack.close()


class WORLDEXPORT_PBR_pack(PropertyGroup):
    path: StringProperty(subtype='FILE_PATH')


class WORLDEXPORT_UL_pbr_packs(UIList):
    def draw_item(self, context, layout, data, item, icon, active_data, active_propname, index):
        layout.label(text=Path(item.path).name, icon='PACKAGE')


class WORLDEXPORT_OT_pbr_add(Operator, ImportHelper):
    bl_idname = 'worldexport.pbr_add'
    bl_label = 'Add Resource Pack ZIPs'
    filter_glob: StringProperty(default='*.zip', options={'HIDDEN'})
    directory: StringProperty(subtype='DIR_PATH')
    files: CollectionProperty(type=OperatorFileListElement)

    def execute(self, context):
        paths = [str(Path(self.directory) / f.name) for f in self.files] or [self.filepath]
        # Split pack bundles use numbered parts; preserve their documented override order.
        def part_order(path):
            match = re.search(r'part-(\d+)',Path(path).name)
            return int(match.group(1)) if match else 0
        paths.sort(key=part_order)
        try:
            stack = PackStack(paths); stack.close()
        except (ValueError, OSError, zipfile.BadZipFile) as exc:
            self.report({'ERROR'}, str(exc)); return {'CANCELLED'}
        for path in paths:
            item = context.scene.worldexport_pbr_packs.add(); item.path = path
        context.scene.worldexport_pbr_index = len(context.scene.worldexport_pbr_packs)-1
        return {'FINISHED'}


class WORLDEXPORT_OT_pbr_folder(Operator, ImportHelper):
    bl_idname = 'worldexport.pbr_folder'
    bl_label = 'Add Extracted Pack (select pack.mcmeta)'
    filter_glob: StringProperty(default='pack.mcmeta', options={'HIDDEN'})

    def execute(self, context):
        path = str(Path(self.filepath).parent)
        try:
            stack = PackStack([path]); stack.close()
        except (ValueError, OSError) as exc:
            self.report({'ERROR'}, str(exc)); return {'CANCELLED'}
        item = context.scene.worldexport_pbr_packs.add(); item.path = path
        return {'FINISHED'}


class WORLDEXPORT_OT_pbr_order(Operator):
    bl_idname = 'worldexport.pbr_order'
    bl_label = 'Change Pack Order'
    action: EnumProperty(items=[('UP','Up',''),('DOWN','Down',''),('REMOVE','Remove','')])

    def execute(self, context):
        scene = context.scene; packs = scene.worldexport_pbr_packs
        index = scene.worldexport_pbr_index
        if 0 <= index < len(packs):
            if self.action == 'REMOVE':
                packs.remove(index); scene.worldexport_pbr_index = max(0, index-1)
            else:
                target = index + (-1 if self.action == 'UP' else 1)
                if 0 <= target < len(packs):
                    packs.move(index,target); scene.worldexport_pbr_index = target
        return {'FINISHED'}


class WORLDEXPORT_OT_pbr_apply(Operator):
    bl_idname = 'worldexport.pbr_apply'
    bl_label = 'Apply PBR Packs'
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        scene = context.scene
        if not scene.worldexport_pbr_packs:
            self.report({'ERROR'}, 'Add a resource pack first'); return {'CANCELLED'}
        from types import SimpleNamespace
        settings = SimpleNamespace(format=scene.worldexport_pbr_format,
            replace_base=scene.worldexport_pbr_replace_base,
            normal_strength=scene.worldexport_pbr_normal_strength,
            emission_strength=scene.worldexport_pbr_emission_strength,
            use_ao=scene.worldexport_pbr_use_ao)
        try:
            stats = apply_packs(list(scene.objects), [p.path for p in scene.worldexport_pbr_packs], settings)
        except (ValueError, OSError, RuntimeError, zipfile.BadZipFile) as exc:
            self.report({'ERROR'}, str(exc)); return {'CANCELLED'}
        scene['worldexport_pbr_report'] = json.dumps(stats)
        message = f"Applied {stats['materials']} PBR materials to {stats['faces']} faces"
        if stats['missing_identity']:
            message += '; terrain without identities needs a fresh export with the PBR-enabled JAR'
        if stats['animated_skipped']:
            message += f"; skipped {stats['animated_skipped']} animated-texture faces"
        self.report({'INFO'}, message)
        return {'FINISHED'}


class WORLDEXPORT_OT_pbr_restore(Operator):
    bl_idname = 'worldexport.pbr_restore'
    bl_label = 'Restore Original Materials'
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        seen = set()
        for obj in context.scene.objects:
            if obj.type != 'MESH' or obj.data.as_pointer() in seen:
                continue
            seen.add(obj.data.as_pointer())
            mesh = obj.data
            for i in reversed(range(len(mesh.materials))):
                mat = mesh.materials[i]
                if not mat or not mat.get(SOURCE):
                    continue
                base = bpy.data.materials.get(mat[SOURCE])
                if not base:
                    continue
                target = next((index for index,m in enumerate(mesh.materials) if m == base),None)
                if target is None:
                    target=len(mesh.materials); mesh.materials.append(base)
                for face in mesh.polygons:
                    if face.material_index == i:
                        face.material_index=target
                mesh.materials.pop(index=i)
        return {'FINISHED'}


class WORLDEXPORT_PT_pbr(Panel):
    bl_label = 'Custom PBR Packs'
    bl_space_type = 'VIEW_3D'
    bl_region_type = 'UI'
    bl_category = 'Replay Export'

    def draw(self, context):
        layout, scene = self.layout, context.scene
        layout.label(text='Bottom pack overrides those above it')
        row = layout.row()
        row.template_list('WORLDEXPORT_UL_pbr_packs','',scene,'worldexport_pbr_packs',scene,'worldexport_pbr_index',rows=4)
        buttons = row.column(align=True)
        for action, icon in [('UP','TRIA_UP'),('DOWN','TRIA_DOWN'),('REMOVE','X')]:
            buttons.operator('worldexport.pbr_order',text='',icon=icon).action = action
        row = layout.row(align=True)
        row.operator('worldexport.pbr_add',text='Add ZIPs')
        row.operator('worldexport.pbr_folder',text='Add Folder')
        layout.prop(scene,'worldexport_pbr_format')
        layout.prop(scene,'worldexport_pbr_replace_base')
        layout.prop(scene,'worldexport_pbr_normal_strength')
        layout.prop(scene,'worldexport_pbr_emission_strength')
        layout.prop(scene,'worldexport_pbr_use_ao')
        layout.operator('worldexport.pbr_apply')
        layout.operator('worldexport.pbr_restore')
        layout.label(text='Animated PBR / displacement not applied')


CLASSES = (WORLDEXPORT_PBR_pack, WORLDEXPORT_UL_pbr_packs, WORLDEXPORT_OT_pbr_add,
           WORLDEXPORT_OT_pbr_folder, WORLDEXPORT_OT_pbr_order, WORLDEXPORT_OT_pbr_apply,
           WORLDEXPORT_OT_pbr_restore, WORLDEXPORT_PT_pbr)
PROPS = ('packs','index','format','replace_base','normal_strength','emission_strength','use_ao')


def register():
    for cls in CLASSES:
        bpy.utils.register_class(cls)
    scene = bpy.types.Scene
    scene.worldexport_pbr_packs = CollectionProperty(type=WORLDEXPORT_PBR_pack)
    scene.worldexport_pbr_index = IntProperty(default=0)
    scene.worldexport_pbr_format = EnumProperty(name='PBR Format',items=[
        ('LABPBR','LabPBR 1.3','Packed normals, F0/metals and alpha emission'),
        ('SEUS','SEUS','RGB normals, smoothness/metalness/emission')],default='LABPBR')
    scene.worldexport_pbr_replace_base = BoolProperty(name='Replace Base Colors',default=True)
    scene.worldexport_pbr_normal_strength = FloatProperty(name='Normal Strength',default=1,min=0,max=5)
    scene.worldexport_pbr_emission_strength = FloatProperty(name='Emission Strength',default=10,min=0,max=1000)
    scene.worldexport_pbr_use_ao = BoolProperty(name='Multiply Texture AO',default=False)


def unregister():
    for prop in PROPS:
        delattr(bpy.types.Scene,'worldexport_pbr_'+prop)
    for cls in reversed(CLASSES):
        bpy.utils.unregister_class(cls)
