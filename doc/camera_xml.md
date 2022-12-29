# Camera Animation XML

Within various parts of the replay export system, camera animations must be stored for later use. While a standard format such as glTF *could* be used, most are too complicated to use internally and many lack support for FOV keyframes.

This XML-based format is used when exporting animations from Blender for re-import into Minecraft, *and* when storing said animations within the `mcpr` file itself.

This specification defines the structure of a single XML element (and its children) describing an animation. This self-contained element may exist in a standalone file *or* as a child within a larger XML file.

*All 3D coordinates in the file use Minecraft's coordinate space (Y up)*

## The Animation Element

The basis of a replay camera animation is the `<animation>` XML element. Along with its children, this element contains the necessary information to play back an animation.

The element itself has a number of attributes indicating global metadata, all optional unless otherwise indicated. When an attribute is omitted, the default behavior is unspecified. However, it should be assumed to use the most generic value possible (for instance, `[0,0,0]` for the offset).

The available attributes are as follows:

- `fps` **(required)**: The frame rate of the animation, in frames per second.
  
  *Example:* `fps="29.97"`

- `id`: The unique numerical ID of the animation. Only used internally within the mod; if an animation with a set ID is imported by the user, it will be overwritten.
  
  *Example:* `id="2"`

- `name`: A user-friendly name for the animation.
  
  *Example:* `name="My awesome animation"`

- `offset`: A json-like array of three float values indicating an additional offset to apply to the animation.
  
  *Example:* `offset="[0.0, 0.0, 0.0]"`

- `preview_color`: A hexadecimal color to use for the path preview in the editor. This should be the raw hex value; no `#` or `0x`.
  
  *Example:* `preview_color="2b00ff"`

- `fov`: A default FOV (in degrees) to use if no FOV channel is provided.
  
  *Example:* `fov="70"`

**Example:**

```xml
<animation fps="29.97" id="2" name="My awesome animation" offset="[0.0, 0.0, 0.0]" preview_color="2b00ff" fov="70">
    ...
</animation>
```

## Animation Channels

Every animation has a set of *channels*, declaring what data is provided in each frame, and in what order. Each channel has a *name* and a *size*. The name indicates the type of data the channel contains, and the size indicates how many scalar values it uses. For instance, the `location` channel uses 3 values, one for each axis.

The available channels are as follows:

- `location` (size: 3): The XYZ position of the camera. Add to the animation's `offset` to get the global position in the world.

- `rotation_euler` (size: 3): The pitch/yaw/roll Euler rotation of the camera in radians (`yaw > pitch > roll`). Incompatible with `rotation_quat`.

- `rotation_quat` (size: 4) The WXYZ Quaternion rotation of the camera. Incompatible with `rotation_euler`.

- `fov` (size: 1) The FOV of the camera in degrees.

Each channel is defined as a child of the `<animation>` element, with its name and size as attributes.

**Example**:

```xml
<animation ...>
    <channel name="location" size="3" />
    <channel name="rotation_euler" size="3" />
    <channel name="fov" size="1" />
    ...
</animation>
```

## Animation Data

The primary animation data is provided within the `<anim_data>` element. This element contains no attributes, and its body is comprised of specially-formatted text:

Each line in this block of text indicates a new frame; the number of lines in the block is equal to the number of frames in the animation. Within each line is a series of numerical scalars, each separated by a single space. This is the transform data for this frame. 

These values are arranged in the order in which the animation channels were defined, and each channel gets allocated an amount of values based on its declared size. So, if a file were to use the channel declaration shown in the example above, each line would be structured as follows:

```
[loc x] [loc y] [loc z] [rot x] [rot y] [rot z] [fov]
```

Every frame must have the same number of values, as determined by the channel declaration. If a frame has an incorrect number of values, the parse fails and an error is thrown.

**Example:**

```xml
<animation ...>
    ...
    <anim_data>64.23 0.38 13.29 1.58 1.89 6.56E-5 51.28
64.23 0.38 13.29 1.58 1.89 6.56E-5 51.28
64.23 0.38 13.29 1.58 1.89 6.56E-5 51.28
64.23 0.38 13.30 1.58 6089 1.89 6.56E-5 51.28
...
    </anim_data>
</animation>
```

# Example

*This example is abridged to four frames for simplicity's sake. The average file will be much longer.*

```xml
<animation fps="29.97" id="2" name="My awesome animation" offset="[0.0, 0.0, 0.0]" preview_color="2b00ff" fov="70">
    <channel name="location" size="3" />
    <channel name="rotation_euler" size="3" />
    <channel name="fov" size="1" />
    <anim_data>64.23 0.38 13.29 1.58 1.89 6.56E-5 51.28
64.23 0.38 13.29 1.58 1.89 6.56E-5 51.28
64.23 0.38 13.29 1.58 1.89 6.56E-5 51.28
64.23 0.38 13.30 1.58 6089 1.89 6.56E-5 51.28
    </anim_data>
</animation>
```
