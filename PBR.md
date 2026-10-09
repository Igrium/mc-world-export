# Custom resource-pack PBR materials (1.4.1+mc26.3)

The Blender importer can apply your own PBR pack to an exported replay, including
terrain, items, armor and entities whose textures have stable resource identities.
MCprep 3.6.3 can prepare identifiable individual Minecraft textures, but its normal
material preparation does not identify each block inside Replay Exporter's shared
atlas. This extension records the actual atlas sprite identities during export
and resolves them without guessing texture names or changing mesh UVs.
MCprep can remain installed; this feature does not change its configuration.

## Use

1. Install the matching `worldexport-1.4.1+mc26.3.jar` alongside ReplayMod and the
   matching `worldexport_blender-1.4.1+mc26.3.zip` in Blender. Restart the applications
   after updating. An already running application keeps its previously loaded code.
2. Export a `.replay` using ReplayMod's **Export Replay File** button, then import
   it using **File > Import > Minecraft Replay File (.replay)**. Existing recordings
   can be exported again; terrain in old exports lacks the new atlas identities.
3. In Blender's 3D View press **N**, select **Replay Export**, and open
   **Custom PBR Packs**. Add resource-pack ZIPs or use **Add Folder** and select
   `pack.mcmeta` from an extracted resource-pack root.
4. Order your packs: the **bottom pack wins**. Each resource is resolved separately,
   so a later pack can replace a specular pass while retaining an earlier base color.
   Multi-selected `part-0001` through `part-0010` bundles are added in numerical order.
   Use the arrows to position an independent custom pack according to your preference.
5. Select **LabPBR 1.3** or **SEUS**, then click **Apply PBR Packs**. Reapply after
   adjusting pack order, normal strength, emission strength or format. Use
   **Restore Original Materials** to restore assignments; apply/restore also support Undo.
6. Save your `.blend`. Applied texture images are packed into it. Pack locations
   are still needed to reapply after changing settings or editing your textures.

**Replace Base Colors** uses the pack's albedo while preserving the export's tint
and opacity node wiring. Disable it to keep the recorded base colors and apply only
PBR passes. **Multiply Texture AO** is optional and disabled by default. These
settings apply to all meshes in the current scene, including hidden animation variants.
Nothing changes the resource packs or active pack selection in Minecraft.

## Formats

LabPBR 1.3 is the default. The inspected custom samples have a signature consistent
with LabPBR (including metal values in specular green and emission in alpha); this
is an inference from the images, not verified information from their generator.

- Normal red/green encode tangent X/Y; green is inverted for Blender and Z is
  reconstructed. Normal blue is optional AO; alpha height is not applied.
- Specular red smoothness becomes linear roughness `(1 - red)^2`. Green values
  0–229 encode dielectric F0; 230–237 use predefined metal F0 colors. Other metal
  codes use albedo as an approximation in Blender's Principled shader.
- Specular blue's SSS range drives subsurface weight. Alpha 0–254 drives emission;
  **255 means emission off**. Emission strength is adjustable.
- SEUS uses RGB normals (DirectX green inversion), red smoothness, green metalness
  and blue emission. Pick this explicitly for packs that use that convention.

These are Blender materials, not a transfer of an Iris shader's complete lighting
model. Complex conductor Fresnel is approximated by Principled F0, porosity/wetness
is not implemented, and height/displacement/POM is not applied. Animated source
textures retain their original imported materials and are skipped for PBR; arbitrary
custom animated texture replacement is not supported. Geometry/UV or rig changes
from custom Minecraft models are outside this feature. Grass overlay textures keep
their existing export wiring; custom overlay combinations have not been exhaustively tested.

## Verification on 2026-10-08

The build and all 84 existing exporter unit tests passed. A short Windows OpenGL
recording/playback/export/shutdown test passed with the exact ten companion mods
listed in [COMPATIBILITY.md](COMPATIBILITY.md), shaders disabled. It captured about
nine seconds and exported 20 scene frames. The final scene contained 1,310 atlas
sprite identities. This is not a full companion-mod compatibility suite.

The actual packaged extension was tested in Blender 4.5.3 LTS with four local
custom packs (base, armor/weapons, creatures and emission fix). It applied 15
materials to 7,992 faces, left 1,324 faces without PBR maps unchanged, and skipped
304 animated-texture faces. Geometry, UVs, weights, action associations/ranges,
pack precedence, restore slot counts, reapplication and rollback after a damaged
texture were checked. A four-material Cycles preview was rendered. Eight separate
numeric Cycles tests checked roughness, metal thresholds, emission boundaries,
SEUS channels and LabPBR normal reconstruction using the packaged extension.

**The 1.4.1 PBR changes have not been tested on Linux.** Earlier Linux results
apply to the 1.4.0 port only. Full custom-pack, animation and mod-feature coverage
has not been established. Texture packs and `.blend` files containing those packs
are not included in this repository.

Machine-readable results and artifact hashes: [PBR evidence](test-evidence/pbr-2026-10-08.json).
To repeat with your own scene and packs:

```sh
blender -b --factory-startup --python-exit-code 1 --python tests/blender_pbr_smoke.py -- scene.replay output pack.zip overlay.zip
blender -b --factory-startup --python-exit-code 1 --python tests/blender_pbr_channels.py
```

Set `WORLDEXPORT_ADDON_ROOT` to the parent folder of an extracted
`worldexport_blender` package to test that package instead of repository source.

References: [LabPBR standard](https://shaderlabs.org/wiki/LabPBR_Material_Standard),
[MCprep usage](https://github.com/Moo-Ack-Productions/MCprep#how-to-use-this-addon).


## Generic capture contribution

The 1.5.0-public.1 extension also applies color-only packs when **Replace Base
Color** is enabled. Disabling that setting keeps PBR-only behavior. Static grass
side and side-overlay color textures can be replaced independently while
preserving existing biome tint/mask wiring. Apply to selected imported meshes or
the full scene using the existing scope control. The pack affects matching
textures actually used by that export; unused pack entries do not create objects.
Later packs take priority for each available pass. Geometry and animation are
preserved; changing a resource-pack model is not a mesh replacement operation.
