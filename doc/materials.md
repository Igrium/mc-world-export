# The Unified Material System

There are multiple components throughout the Vcap and Replay exporters that require the serialization of PBR materials. The Unified Material System is a lightweight, material definition format that aims to be simple, reliable, and engine-agnostic.

*Note: the Unified Material System does not manage the packing of textures, nor does it define how textures must be packed. All textures in the material are defined relative to a root "textures" directory, specified by the parent format.*

## File Structure and Fields

Each material is serialized as a JSON file, with each element controlling a different attribute of the material. The majority of these attributes come in the form of a *field*, which is simply a universal struct for representing color or texture data. Each field is represented as an entry in the material's root object, where the key is the name of the field and the value is the field data. The field type is determined by the type of the Json element representing it. The permitted types are as follows:

- `number` (float): A single value that acts as a grayscale scalar across the entire image.
- `array`: A three-number array that acts as either an RGB color or an XYZ vector across the entire image.
- `string`: References the filename of a texture relative to the texture root (see "Textures" section). Textures are loaded using a color space determined by the attribute in which they are used.

## Available Attributes

As of this specification, the attributes available to control the material are as follows. Note that not all of them accept fully-fledged field structs. Some are simply options to pass to the shader builder.

- `color` (field): The base color, or albedo, of the material.

- `color2` (field, default: `[1.0, 1.0, 1.0]`): An additional color to mix with the base color. Used primarily for tinting.

- `color2_blend_mode` (string, default: `"multiply"`): The blending mode to use while mixing `color` and `color2`. 
  
  - Must be one of `mix`, `darken`, `multiply`, `burn`, `lighten`, `screen`, `dodge`, `add`, `overlay`, `soft_light`, `linear_light`, `difference`, `subtract`, `divide`, `hue`, `saturation`, `color`, or `value`.

- `roughness` (field, default: `0.5`): The PBR roughness of the material.

- `metallic` (field, default: `0`): The PBR metalness of the material.

- `normal` (field, default: `[0.5, 0.5, 1.0]`): The material's OpenGL-formatted normal map.

- `emission` (field, default: `0`): The PBR emission of the material.

- `emission_strength` (float, default: `1.0`): A uniform "multiplier" to apply to the emission channel.

- `blend_mode` (string, default: `"opaque"`): The alpha blending mode to use on this material. Implementation depends on render engine.
  
  - Must be one of `opaque`, `clip`, `hashed` or `blend`.

- More attributes may be added in the future.

## Material Overrides

It's possible for materials to expose a set of fields to be overwritten on a per-entity basis. This is known as adding an "override".

Material overrides are defined in the `"overrides"` JSON object of the file. This object is a set of key-value string pairs, where the key is the name of the attribute containing the field you wish to override, and the value is the name of the override itself. The following override names are hard-coded to return engine-level information about the object:

- `$VERTEX_COLOR`: Returns the vertex color or the Vcap block color of any given pixel. Primarily used for world tinting.

Other overrides are user-definable and are implemented in the entity files.

## Tags

Sometimes, a material effect is impossible to represent with the Unified Material System. To account for these situations, the `"tags"` element is a Json array of strings allowing for the inclusion of arbitrary metadata in the material. The material parser may check these tags and modify any shader parameter accordingly. However, for any given tag, implementation is *not* guaranteed. Unimplemented tags will be ignored, and the base material will be fallen back upon.

## Example Material:

```json
{
  "color": "minecraft/textures/models/armor/diamond_layer_1",
  "color2_blend_mode": "SOFT_LIGHT",
  "roughness": 1.0,
  "tags": ["enchanted"],
  "overrides": {
    "color2": "tint"
  },
  "transparent": true
}
```

# Textures

By default, textures are stored as PNG files relative to a "texture root". This texture root is defined by the context in which the material is being loaded. For instance, in Vcap and Replay files, the texture root is a folder named `tex` directly under the root directory. With this example, a texture with a given ID would be found at `tex/[id].png`.

If a texture ID has a slash (`/`) in it, it will parsed as a sub-directory under the texture root. So a texture with ID `subfolder/texture` would be found at `[tex_root]/subfolder/texture.png`. To reduce platform inconsistency, texture IDs should *not* contain backslashes (`\`).

## Animated Textures

Some textures, however are presented as JSON files rather than PNG files. If a JSON file is found with a texture's ID (`[tex_root]/[id].json`), it will be parsed as an **animated texture**. An animated texture consists of two files: a **metadata** file (the JSON file described) and a **spritesheet**.

### Animation Metadata

As of this version, the metadata JSON consists of the following entries:

- `framerate` (float): The frames per second (FPS) of this animated texture.

- `frame_count` (int): The total number of frames in this animated texture.

**Example:**

```json
{
  "framerate": 10.0,
  "frame_count": 20
}
```

### Animation Spritesheet

Adjacent to the metadata file is a **spritesheet**, identified as `[tex_root]/[id]_spritesheet.png`. This spritesheet contains an atlas of all the frames in the animation, stacked vertically. Each frame must be the same size, and the resolution of the spritesheet is `[tex_width]` x `[tex_height] * [frame_count]`

*See [`example_spritesheet.png`](example_spritesheet.png) for an example.*

> **Implementation Note:**
> 
> Although spritesheets are rendered differently, they may be loaded using the same mechanism as regular textures. As such, referencing a spritesheet directly in a material could result in the spritesheet itself being rendered in its entirety, without any animation.
