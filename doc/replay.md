# .REPLAY Specification

The specification for `.replay` files is very similar to that of `.vcap`. Unlike Vcap, however, the `replay` specification is written *specifically* for exporting Minecraft scenes into 3D software.

*Note: all coordinate spaces use the Y axis as the up axis, similar to Minecraft.*

## High Level Format

Replay files are essentially renamed zip files. Changing the file extension from `.replay` to `.zip` allows you to easily inspect the contents inside. The rest of this specification will assume we are looking inside the zip package as if it were a folder.

## The World

Replay files store world data using Vcap technology. In the root of archive is a file called `world.vcap`, which is a [Vcap file](vcap.md) containing this replay's world data. Time `0` in this file corresponds to the first frame of the replay.

# Entities

The meat of the Replay file comes in its entity animation. This presents an interesting challenge for the exporter given that, unlike blocks, Minecraft has no universal model management system for entities. Instead, every entity registers a renderer class that writes directly to viewport vertex buffers. Therefore, export code has to be written individually for each entity type.

Furthermore, the majority of entity renderers rely on a system of individual "model parts" rather than an armature with joints. This means that entity rigs have no concept of a "bind pose", and instead store individual bone positions (relative to the entity root) on a per-frame basis. In order to retrofit this into a typical armature-based system, a typical implementation will capture the entity's pose on the first frame of the animation and use that as its bind pose. This, however, is finicky and can lead to many issues. As such, Replay files support two types of rig representation: `armature` (default) and `parts`. More details to follow.

## Entity Files

Each entity in a replay gets its own string ID, usually the namespaced name of the entity (with the `:` replaced with a `_`), followed by a dot and a numerical ID. (ex. `minecraft_zombie.69`)

Within the `entities` folder within the archive is a series of XML files named with entity IDs (`minecraft_zombie.69.xml`, etc.). These files contain the mesh *and* animation data of the entities themselves.

The element of the file is an `<entity>` element. This element contains basic information about the entity, including:

* `name` Another copy of the entity ID.

* `class` (optional) The namespaced name of this entity type.

## The Model

The first child of the `<entity>` element is `<model>`. The format of this element varies based on the rig type, which is defined in the `rig-type` attribute. Currently, only `armature` and `multipart` are supported.

### Armature Rigs

If `rig-type` is set to `armature`, `<model>` can support the following elements:

- `<bone>`: Represents a single bone in the armature.
  
  - `len`: The length of the bone from beginning to end, in meters.
  
  - `name`: The name of the bone.
  
  - `pos`: The XYZ position of the bone root relative to the entity root. Written as three stringified floats separated by commas (no spaces).
  
  - `rot`: The rotation of the bone relative to the entity root, represented as a WXYZ quaternion. Written as four stringified floats separated by commas (no spaces).

- `<mesh>`: Contains the actual mesh data for the model. Only *one* of these may exist per-model.
  
  - The `<mesh>` element contains no child elements. Instead, it houses the raw ascii data of an `obj` file, with a small exception that OBJ face groups now designate the name of the bone that each face belongs to.

***Example:***

```xml
<model rig-type="armature">
    <bone len="0.16" name="head" pos="0.0,1.5,-1.83E-16" rot="6.12E-17,1.0,0.0,0.0"/>
    <bone len="0.16" name="torso" pos="-0.3125,1.375,-1.685E-16" rot="0.656,0.754,0.00534,-0.01382"/>
    <bone len="0.16" name="left_arm" pos="0.3125,1.375,-1.68E-16" rot="0.629,0.776,-0.00315,0.0159"/>
    <bone len="0.16" name="right_arm" pos="-0.118,0.75,-0.00625" rot="-0.200,0.979,0.0,0.0"/>
    <bone len="0.16" name="left_leg" pos="0.118,0.75,-0.00625" rot="0.200,0.979,0.0,0.0"/>
    <bone len="0.16" name="right_leg" pos="0.0,1.5,-1.83E-16" rot="6.12E-17,0.999,-7.51E-19,-0.0122"/>
    <bone len="0.16" name="hat" pos="0.0,1.5,-1.83E-16" rot="6.12E-17,0.999,-7.51E-19,-0.0122"/>
    <mesh>
        [obj data...]
    </mesh>
</model>
```

### Multipart Rigs

*[tbd]*

## Animation

The other major part of entity files are animations. Except for a minor detail regarding coordinate spaces, animations look exactly the same for both `armature` and `multipart` rigs.

Each `entity` has *one* `<anim>` element with the following attribute:

- `fps`: The frame rate of the animation, represented as a stringified float. In most cases, this will be `"20"`, matching Minecraft's internal clock speed.

Like `<mesh>`, `<anim>` contains raw text which efficiently makes up the animation. Every line in this block of text is an individual frame, and each frame is made up of a set of *transform entries*, separated by semicolons (`;`).

*Note: the semicolon is technically a part of the transform entry, so even the last one in a frame must have one*

Each transform entry contains a set of stringified floats separated by spaces. The data itself is as follows:

1. WXYZ (quaternion) rotation.

2. XYZ position.

3. XYZ scale (in local space of rotated element).

```
[w rotation] [x rotation] [y rotation] [z rotation] [x translation] [y translation] [z translation] [x scale] [y scale] [z scale];
```

For `armature` rigs, these values are defined relative to the bind pose, and in `multipart` rigs, they are defined relative to the entity root. *The exception to this is the transform entry for the entity root, which is defined in world space.*

Any of these values may be omitted, but due to the fact that *what* a number represents is determined by its position the list, if a value is omitted, none of the data after it may be defined. For instance, if translation is omitted, the entry cannot contain a scale value.

***Transform entry example:***

```
0.219 0.0 0.975 0.0 32.5 -9.0 324.5 1.0 1.0 1.0;
```

The order of the entries themselves within a frame is determined by the order in which the bones were defined in the file, starting with the entity root. (Remember: unlike JSON, the order in which elements are defined in XML matters.)

*All whitespace at the beginning of an entry is discarded.*

***Animation example:***

```xml
<anim fps="20">
    0.219 0.0 0.975 0.0 32.5 -9.0 324.5; 1.0 0.0 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 0.0193 -0.00167 0.0134 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 -0.0193 -0.00167 -0.0134 0.0 0.0 0.0 1.0 1.0 1.0; 0.979 -0.2 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 0.979 0.2 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 1.23E-32 0.0122 9.62E-35 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 1.23E-32 0.0122 9.62E-35 0.0 0.0 0.0 1.0 1.0 1.0;
    0.219 0.0 0.975 0.0 32.5 -9.0 324.5; 1.0 0.0 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 0.0193 -0.00167 0.0134 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 -0.0193 -0.00167 -0.0134 0.0 0.0 0.0 1.0 1.0 1.0; 0.979 -0.2 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 0.979 0.2 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 1.23E-32 0.0122 9.62E-35 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 1.23E-32 0.0122 9.62E-35 0.0 0.0 0.0 1.0 1.0 1.0;
    ...
</anim>
```

### Example Entity

```xml
<entity name="minecraft_zombie.69" class="minecraft:zombie">
    <model rig-type="armature">
        <bone len="0.16" name="head" pos="0.0,1.5,-1.83E-16" rot="6.12E-17,1.0,0.0,0.0"/>
        <bone len="0.16" name="torso" pos="-0.3125,1.375,-1.685E-16" rot="0.656,0.754,0.00534,-0.01382"/>
        <bone len="0.16" name="left_arm" pos="0.3125,1.375,-1.68E-16" rot="0.629,0.776,-0.00315,0.0159"/>
        <bone len="0.16" name="right_arm" pos="-0.118,0.75,-0.00625" rot="-0.200,0.979,0.0,0.0"/>
        <bone len="0.16" name="left_leg" pos="0.118,0.75,-0.00625" rot="0.200,0.979,0.0,0.0"/>
        <bone len="0.16" name="right_leg" pos="0.0,1.5,-1.83E-16" rot="6.12E-17,0.999,-7.51E-19,-0.0122"/>
        <bone len="0.16" name="hat" pos="0.0,1.5,-1.83E-16" rot="6.12E-17,0.999,-7.51E-19,-0.0122"/>
        <mesh>
            [obj data...]
        </mesh>
    </model>
    <anim fps="20.0">
        0.219 0.0 0.975 0.0 32.5 -9.0 324.5; 1.0 0.0 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 0.0193 -0.00167 0.0134 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 -0.0193 -0.00167 -0.0134 0.0 0.0 0.0 1.0 1.0 1.0; 0.979 -0.2 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 0.979 0.2 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 1.23E-32 0.0122 9.62E-35 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 1.23E-32 0.0122 9.62E-35 0.0 0.0 0.0 1.0 1.0 1.0;
        0.219 0.0 0.975 0.0 32.5 -9.0 324.5; 1.0 0.0 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 0.0193 -0.00167 0.0134 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 -0.0193 -0.00167 -0.0134 0.0 0.0 0.0 1.0 1.0 1.0; 0.979 -0.2 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 0.979 0.2 0.0 0.0 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 1.23E-32 0.0122 9.62E-35 0.0 0.0 0.0 1.0 1.0 1.0; 1.0 1.23E-32 0.0122 9.62E-35 0.0 0.0 0.0 1.0 1.0 1.0;
        ...
    </anim>
</entity>
```

*Note: this example has been prettied-up to make it digestible and it is NOT a valid entity XML tree. See `[insert file here]` for a proper example.*
