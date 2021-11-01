# .VCAP Specification
The general purpose of vcap (voxel capture) files is to represent voxel worlds in which there can be different types of
voxels, each with a different mesh, in a format that is void of any external dependencies. It was designed with the purpose
of exporting Minecraft worlds, and realtime changes to said worlds, into pieces of 3D software like Blender, although it is
applicable to other applications as well.

## High Level Format
VCAP files are essentially renamed zip files. Changing the file extension from `.vcap` to `.zip`.
The rest of this specification will assume we are looking inside the zip package as if it were a folder.

## The World
The world, arguably the most dense element of the format, is stored in an uncompressed [NBT](https://wiki.vg/NBT)
format within `world.dat`. It follows the following scheme

- root - TAG_COMPOUND
    - frames - TAG_LIST: A list of "frames," or updates to the world. Currently, only one frame
    is supported. This tag is just for future-proofing.
        - (a frame): TAG_COMPOUND
            - type: TAG_BYTE: The type of frame this is. Currently, only Intracoded frames are supported.
            - time: TAG_DOUBLE: The time, in seconds, since the begninning of the capture that this frame activates.
            - sections: TAG_LIST A set of three-dimensional, 16x16x16 "chunks" containing voxel data.
                - (a section): TAG_COMPOUND
                    - x: TAG_INT The x position of this section in section coordinates.
                    - y: TAG_INT The y position of this section in section coordinates.
                    - z: TAG_INT The z position of this seciton in section coordinates.
                    - palette: TAG_LIST: A list of the different voxel types in this chunk, where each entry
                    is a simple string tag indicating a model ID.
                    - blocks: TAG_BYTE_ARRAY: The actual block data within the chunk. Each byte represents
                    a different block, making the array 4096 bytes in length. Blocks are
                    sorted by height (bottom to top) then length then width—the index of the block at X,Y,Z is
                    `(Y×length + Z)×width + X`. Read as signed numbers, the values in the bytes corriate to the index
                    in the palette which the intended model ID resides.

This format is modeled losely off of Minecraft [schematic](https://minecraft.fandom.com/wiki/Schematic_file_format) files,
modified to fit the requirements for vcap.

## Meshes
One of the strengths of vcap is that is entirely self-contained. Wheras other formats require an external library of textures
and meshes in order to render them, vcap files contain all the assets needed out of the box, occlusion data and all.

Within the `mesh` folder of the archive is a series of `.obj` files containing the mesh data. Each model ID gets it's own file,
with the simple naming scheme of `[model ID].obj`. It is expected that these models have their occlusion optimizations pre-applied,
meaning that each each variant of a block will have a seperate model ID. In other words, while a dirt block may use the same ID 
regardless of where it's placed normally, a free-floating dirt block and a block of dirt in the ground will use different model IDs here,
and therefore have seperate palette entries.

See the [OBJ file](https://en.wikipedia.org/wiki/Wavefront_.obj_file) specification for details about the content within the mesh files.

## Textures
The final packed assets are textured, and these are fairly straightforward. Within the `tex` folder is an arbitrary amount of 
PNG files containing the textures the meshes rely on. Multiple meshes may reference the same texture, and this texture should not
be duplicated when that happens.