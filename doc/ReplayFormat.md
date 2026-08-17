# The Replay Format

This document specifies the format used to export `.replay` files from Minecraft and into 3D. It's a format very specific to Minecraft, although it's designed to be agnostic when it comes to 3D software.

## Versioning

Replay files use [Semantic Versioning](https://semver.org/). This document covers version `2.0.x`. In this case, patch version increases are generally used when [standard material flags](#Materials) are updated.

Versions should strive to be forward-compatible. If an importer attempts to import a replay file with a version greater than what it supports, various features may be missing, but it shouldn't break entirely.

## Overview

In essence, replay files are simply renamed, unencrypted zip archives. Any software capable of opening zip archives should be capable of opening replays and inspecting the contents.

Where possible, standard formats are re-used (`.obj`, `.mtl`, etc) to improve reliability and ease the process of importing. In these cases, the format relies primarily on "companion files" to supply additional metadata.

### Coordinate System

Replay files use Minecraft's coordinate system. That is, a **right-handed, y-up** coordinate space. Importers must adapt this to whatever format their software uses.

### Metadata

In the root of the archive is a file called `meta.json`, containing general information about the file. It is structured as follows:

```json
{
    "version": "2.0.0",
    "origin": [-104, 62, 127]
}
```

| Field     | Type   | Meaning                                                                                                                                                                      |
|:--------- |:------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `version` | string | version format string (see: [Versioning](#Versioning))                                                                                                                       |
| `origin`  | vector | integer block position of the **global world position of the replay origin**. Everything else in the file is replay-local; add `origin` to recover the original coordinates. |

All JSON vectors in the format are 3-dimensional arrays: `[x, y, z]`

---

## World

`world.json` contains a manifest of all block-world meshes, including their mesh and various metadata:

```json
{
    "world": {
        "offset": [0, 0, 0]
    },
    "section_1_4_-2": {
        "offset": [16, 64, -32]
    },
    "section_1_4_-2_0": {
        "offset": [16, 64, -32],
        "startTick": 0,
        "endTick": 419
    },
    "section_1_4_-2_1": {
        "offset": [16, 64, -32],
        "startTick": 420
    }
}
```

| Field       | Type           | Meaning                                                                       |
|:----------- |:-------------- | ----------------------------------------------------------------------------- |
| `offset`    | vector         | Mesh origin in replay space; vertices in the OBJ are relative to this origin. |
| `startTick` | int (optional) | First tick the mesh is visible. Absent = visible from the start               |
| `endTick`   | int (optional) | Last tick the mesh is visible (inclusive). Absent = visible to the end.       |

Every entry must have a matching `<name>.obj` defined within the file. These are resolved relative to the replay root. If the world mesh key contains `/`, the obj is should be nested within a folder.

Replays use [Blender's variant of the OBJ format](https://docs.blender.org/manual/en/4.0/files/import_export/obj.html#properties). Critically, block tint colors are embedded as **vertex colors** using the `XYZ RGB` OBJ extension format. (see: [Materials](#Materials))

---

## Entities

Each entity is comprised of a series of **model parts**. Each model part represents a portion of the entity mesh which can be animated, parented, or have its visibility toggled.

They are all defined in the `entities.json` file, which list all entities and their model parts' parent-child relationships:

```json
{
    "Igrium" {
        "hat": "head", // 'hat' is parented to 'head'
        "left_sleeve": "left_arm" // 'left_sleeve' is parented to 'left_arm'
    },
    "Zombie" {} // This entity has no parent-child relationships
}
```

**Not all model parts are defined** in `entities.json`; only those with parent-child relationships. The `.anim` file is the **only way** to comprehensively determine what model parts an entity has.

### Entity Meshes

Each entity has a corresponding `<name>.obj` file referenced in the archive (same as the world.) This is how the entity's mesh is defined.

Each **model part** is one OBJ **group** (`g <part name>`) inside a single object. These are turned into vertex groups, which is skinned to the entity's animated armature. If a model part does not have a group, it is considered "empty": it is still imported, but used for parenting only.

**If an entity does not have an associated obj file, it is imported as a mesh-less empty object**

## Animation

(todo: make this more understandable)

The core of exported replays come in the form of `.anim` files, scattered throughout the replay file. Every entity defined in `entities.json` **must** have an associated `<name>.anim` file.

These binary files contain a number of **curves**. Each curve pertains to a specific model part, and contain a number of **channels**, each a continuous float array, values mapped to a specific tick in the replay.

### Layout

These binary files are **big-endian**, using the following primitives:

| Type    | Length   | Meaning                                                                                                                                             |
|:------- |:-------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `byte`  | 1        | 8-bit un-signed integer                                                                                                                             |
| `int`   | 4        | 32-bit signed integer.                                                                                                                              |
| `float` | 4        | IEEE-754 single precision floating-point number                                                                                                     |
| `str`   | variable | `u2`, byte length, followed by [modified UTF-8](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInput.html#modified-utf-8) |

```
anim file
├── int  curveCount            total curves in the file
└── repeated curveCount times:
    ├── str  partName          which model part this curve belongs to
    └── curve
        ├── byte  format       which channels follow
        ├── int   frameOffset  tick the channel starts on
        ├── int   length       length of the channel in ticks
        └── float channels[]   length floats per channel, one channel at a time
```

### Curve Formats:

The `format` byte selects which values are present in any given curve, and therefore how many channels it will have. The types are as follows:

| Value | Name            | Channels | Contents                                            |
| ----- | --------------- | -------- | --------------------------------------------------- |
| 0     | `NONE`          | 0        | nothing; the curve exists only to assert visibility |
| 1     | `POS`           | 3        | position                                            |
| 2     | `POS_ROT`       | 7        | position, rotation                                  |
| 3     | `POS_ROT_SCALE` | 10       | position, rotation, scale                           |

The channel-order is fixed, and each format is a truncation of the one below:

```
index    0     1     2      3     4     5     6      7       8       9
       posX  posY  posZ   rotW  rotX  rotY  rotZ  scaleX  scaleY  scaleZ
       └────── POS ─────┘
       └─────────── POS_ROT ───────────┘
       └──────────────────── POS_ROT_SCALE ────────────────────────────┘
```

The size in bytes of each curve is determined by the number of channels it has and, by extension, which format it is. Specifically: `length = 9 + format.channels * length`

Transforms are defined in a coordinate space relative to their parent defined in `entities.json`. If they have no parent, they're defined relative to the entity root.

### Visibility

**Model parts are only visible when they have an active curve!**

If a curve belonging to a given model part begins on tick 10, and is 5 ticks in length, the model part will appear on tick 10 and disappear on tick 15. 

A second curve may be used to make it re-appear later in the file. If you need a model part to remain visible without any animation data, use curve format `NONE`.

## Materials

(The flow of this needs to be cleaned up)

By default, materials are handled natively by the OBJ importer. They're defined in `.mtl` files alongside the `.obj` files, which reference them with `mtllib` and `usemtl` as per the [OBJ standard](https://en.wikipedia.org/wiki/Wavefront_.obj_file#Reference_materials)

However, many features of replay materials don't exist in traditional a traditional OBJ pipeline. To solve this, an optional `<mtlname>.mtl.json` can be included next to the mtl, containing additional flags that can be applied to materials on import.

```json
{
    "world": {
        "vertexTint": true
    },
    "items.glint": {
        "renderMode": "blended",
        "glint": true
    }
}
```

These flags instruct importers to apply additional effects on imported materials, such as enchantment glint shaders.

The Minecraft exporter includes the following properties:

| Property                         | Type                        | Meaning                                                                                                  |
| -------------------------------- | --------------------------- | -------------------------------------------------------------------------------------------------------- |
| `vertexTint`                     | bool                        | Multiply base color by the mesh's vertex color.                                                          |
| `glint`                          | bool                        | Apply an enchantment glint shader.                                                                       |
| `renderMode`                     | `"dithered"` \| `"blended"` | Alpha handling. Default `"dithered"`.                                                                    |
| `grassOverlayU`, `grassOverlayV` | float                       | If either is nonzero, overlay a second tinted layer sampled at these UV offsets. Overrides `vertexTint`. |
| `armor`                          | bool                        | Material came from an equipment layer.                                                                   |

**This property list is non-exhaustive.** If an importer encounters a property it doesn't recognize it, it should ignore it, and likewise, the exporter must anticipate that a given property might not be respected.


