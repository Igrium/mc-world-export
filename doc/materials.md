# The Unified Material System

There are multiple components throughout the Vcap and Replay exporters that require the serialization of PBR materials. The Unified Material System is a lightweight, material definition format that aims to be simple, reliable, and engine-agnostic.

*Note: the Unified Material System does not manage the packing of textures, nor does it define how textures must be packed. All textures in the material are defined relative to a root "textures" directory, specified by the parent format.*

## File Structure and Fields

Each material is serialized as a Json file, with each element controlling a different attribute of the material. The majority of these attributes come in the form of a *field*, which is simply a universal struct for representing color or texture data. Each field is represented as an entry in the material's root object, where the key is the name of the field and the value is the field data. The field type is determined by the type of the Json element representing it. The permitted types are as follows:

- `number (float)`: A single value that acts as a grayscale scalar across the entire image.
- `array`: A three-number array that acts as either an RGB color or an XYZ vector across the entire image.
- `string`: References the filename of a texture relative to the texture root (see above). Textures are loaded using a color space determined by the attribute in which they are used.

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

Material overrides are defined in the `"overrides"` Json object of the file. This object is a set of key-value string pairs, where the key is the name of the attribute containing the field you wish to override, and the value is the name of the override itself. The following override names are hard-coded to return engine-level information about the object:

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
