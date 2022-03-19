# .VCAP Specification

The general purpose of Vcap (voxel capture) files is to represent voxel worlds in which there can be different types of voxels, each with a different mesh, in a format that is void of any external dependencies. It was designed with the purpose of exporting Minecraft worlds, and real-time changes to said worlds, into pieces of 3D software like Blender, although it is applicable to other applications as well.

## High Level Format

Vcap files are essentially renamed zip files. Changing the file extension from `.vcap` to `.zip` allows you to easily inspect the contents inside. The rest of this specification will assume we are looking inside the zip package as if it were a folder.

## Metadata

The first file to look at within the archive is `meta.json`. This JSON file contains the following metadata about the Vcap:

- `version` - *string*: The Vcap version. This specification is for version `0.1.0`.
- `encoder` - *string*: The program used to write this file. Used for debugging.

Example:

```json
{
  "version": "0.1.0",
  "encoder": "Minecraft World Exporter"
}
```

## The World

The world, arguably the most dense element of the format, is stored using an uncompressed [NBT](https://wiki.vg/NBT) format within `world.dat`. It uses the following scheme:

- root - TAG_COMPOUND
  
  - `frames` - TAG_LIST: A list of "frames," or updates to the world. 
    
    - (a frame): TAG_COMPOUND
      
      - `type`: TAG_BYTE: The type of frame this is. `0` for intracoded and `1` for predicted.
      
      - `time`: TAG_DOUBLE: The time, in seconds, since the beginning of the capture that this frame activates.
        
        The rest of the this varies based on frame type, as described below.

### Intracoded Frames

These frames are fairly heavy and represent the world data in it's entirety. Should be used sparingly.

- [All data from universal frame documentation]
- `sections`: TAG_LIST A set of three-dimensional, 16x16x16 "chunks" containing voxel data.
  - (a section): TAG_COMPOUND
    - `x`: TAG_INT The x position of this section in section coordinates.
    - `y`: TAG_INT The y position of this section in section coordinates.
    - `z`: TAG_INT The z position of this section in section coordinates.
    - `palette`: TAG_LIST: A list of the different voxel types in this chunk, where each entry is a simple string tag indicating a model ID.
    - `blocks`: TAG_INT_ARRAY: The actual block data within the chunk. Each integer represents a different block, making the array 4096 bytes in length. Blocks are sorted by height (bottom to top) then length then width—the index of the block at X,Y,Z is `(Y * 16 + Z) * 16 + X`. Read as signed numbers, the values correlate to the index in the palette which the intended model ID resides.
    - `colorPalette`: TAG_BYTE_ARRAY An array of the different color values contained within this chunk (biome colors, etc.). The array is broken into sets of three bytes, each representing a different color entry, thus making the size of this array 3 * the number of colors in the section. The three bytes in each set represent the red, green, and blue values of the color, in that order. It's worth noting that, although the NBT format specifies that all values are signed, these bytes are an exception to this rule, giving each channel the unsigned range of `0-255`. The values returned by most NBT libraries will require conversion.
    - `colors`: TAG_BYTE_ARRAY The actual color data of the chunk. Like the block data, these bytes each reference an index of the `colorPalette` array, following the same arrangement pattern as the block data. Due to the fact that the color palette is broken into sets of three, only indices that are multiples are permitted.

This format is modeled loosely off of Minecraft [schematic](https://minecraft.fandom.com/wiki/Schematic_file_format) files, modified to fit the requirements for Vcap.

### Predicted Frames

Predicted frames are much lighter than Intracoded frames and are designed to represent changes to a world relative to the previous frame. However, while less data is stored overall, making these frames less expensive, it is stored less efficiently, meaning these should not be used to store entire worlds.

- [All data from universal frame documentation]
  - `blocks` - TAG_LIST: A list of all updated blocks in this frame.
    - (a block) - TAG_COMPOUND:
      - `pos` - TAG_LIST: A three-int list containing the global coordinates of this block.
      - `state` - TAG_INT: The index within the `pallete` tag with this block's mesh ID.
      - (optional) `color` - TAG_LIST: A three-byte list denoting the red, green, and blue values of this block's color. It's worth noting that, although the NBT format specifies that all values are signed, these bytes are an exception to this rule, giving each channel the unsigned range of `0-255`. The values returned by most NBT libraries will require conversion.
  - `palette` - TAG_LIST:
    - A list of string tags with the mesh IDs within the frame.

## Meshes

One of the strengths of Vcap is that is entirely self-contained. Whereas other formats require an external library of textures and meshes in order to render them, Vcap files contain all the assets needed out of the box, occlusion data and all.

Within the `mesh` folder of the archive is a series of `.obj` files containing the mesh data. Each model ID gets it's own file, with the simple naming scheme of `[model_id].obj`. It is expected that these models have their occlusion optimizations pre-applied, meaning that each each variant of a block will have a separate model ID. In other words, while a dirt block may use the same ID regardless of where it's placed normally, a free-floating dirt block and a block of dirt in the ground will use different model IDs here,
and therefore have separate palette entries.

See the [OBJ file](https://en.wikipedia.org/wiki/Wavefront_.obj_file) specification for details about the content within the mesh files themselves.

## Face Layers

Some implementations of mesh-based voxel rendering (Minecraft in particular) assume that some faces are rendered after others, and therefore no z-fighting can take place. Because of this, some blocks (such as Minecraft grass blocks) will not render properly in a traditional rendering engine. From the need to fix this issue, face layers were born.

The OBJ specification allows for different "face groups" within a file. If a face group name begins with the keyword `flayer` followed by a number (ex: `flayer0`), the faces in that group are guaranteed to render in the proper order compared to other face groups. (Note: this may be implemented at a shader level rather than a geometry level.) If a mesh has no groups assigned, the bottom face layer is assumed to be used.

## Textures and Materials

When parsing obj files, a `usemtl` line is often encountered. Unlike traditional obj files however, this does not reference an external `mtl` file.
Instead, it refers to a material definition within the archive.

Within the `mat` folder is a series of Json files, each named `[material_id].json`, which describe the materials found in the world. Within each file is a single Json object with a set of *fields*. Each field controls an aspect of the material, and is represented by an object key followed by one of the following Json types:

* `number`: A value that acts as a grayscale scalar across the entire image.
* `array`: A three-number array that, like `number`, acts across the entire image. The difference is that this can represent RGB and XYZ values.
* `string`: A string that references a packed texture. Textures are loaded using a color space determined by the field in which they are used.

All textures can be found as PNG files within the `tex` folder, using the name that the material refers to them with (`[tex_id].png`). It's common for multiple materials to reference the same texture, and texture data should not be duplicated when this happens.

The fields themselves follow standard PBR conventions:

* `color`: The base albedo map.
* `roughness`: The material's roughness / reflectivity, where black is fully reflective and white is fully matte. (default: `.5`)
* `metallic`: A value between 0 and 1 determining the material's metalness. [Read about PBR metalness here.](https://www.chaosgroup.com/blog/understanding-metalness) (default: `0`)
* `normal`: The material's normal map. (default: neutral)

There are also additional two boolean values that provide material metadata:

* `transparent`: Whether this material should be rendered with transparent shading (alpha hashed). (default: `false`)
* `useVertexColors`: If enabled, the shader will multiply the texture with the block's corresponding color from the world data. Typically implemented using vertex colors. (default: `false`) 

All fields are optional, and the exclusion of one will lead to its default value being used.

### Example material:

```json
{
  "color": "world",
  "roughness": 0.7,
  "metallic": 0,
  "transparent": true,
  "useVertexColors": true
}
```