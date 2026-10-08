# Minecraft 26.3 Fabric port: build and limited test results

Status on 2026-10-08: compiles; short Windows and Linux X11/OpenGL smoke tests
passed with the matching ReplayMod port. Both final scene exports and their
separate camera GLBs imported successfully into Blender 4.5.3 LTS.
This is an experimental port, not certification of the full mod compatibility suite.

Ported from upstream `Igrium/mc-world-export` main commit
`4bb4ec5799f548be768d72027a769cd393e9477b` (26.2 source).
The actual upstream owner is [Igrium](https://github.com/Igrium/mc-world-export).

## Build

`build-26.3.ps1` completed `build buildAddon` using Java 25.0.2 and Blender 4.5.3.
All 84 existing automated tests passed: MeshMergeVertsTest (9) and TaskManagerTest
(75). These are exporter unit tests, not tests of every companion mod.

Minecraft 26.3, Fabric Loader 0.19.5 and Fabric API 0.161.0+26.3 were used.
The exporter compiles against the exact separately installed ReplayMod JAR;
it does not bundle or replace ReplayMod. See README for the required Gradle property.
The matching ReplayMod port and its dependency sources were committed and pushed
in [ReplayMod c0bd6724](https://github.com/DragonbornElric/ReplayMod/commit/c0bd67242e99526a0efb9b6fb1be6cb526ea28e2).
Its later Linux report is on the `stable` branch.

The source adapts 26.3 RenderPearl GPU types, item-quad payloads and collector
signatures, held-item swing state, pose rotation, immutable block positions,
SDL event polling and the export window's render state. The item-layer accessor's
field type was also updated after a startup test exposed its old 26.2 signature.

## Exact companion-mod set

These ten JARs were copied byte for byte from the Elric 26.3 Prism instance;
none of the custom mod JARs were rebuilt or changed for this exporter test.
All loaded through the tested recording/playback/export/shutdown workflow.
This does not establish that all their features work or all custom assets export.

| Mod | Version |
| --- | --- |
| Emma Bridge | 0.2.0 |
| Emma SMP Client | 0.1.0+26.3 |
| Emma EndInv | 1.4.6 |
| Entity Model Features | 3.3.8 |
| Entity Texture Features | 7.2.4 |
| Fabric API | 0.161.0+26.3 |
| Iris | 1.11.7+mc26.3 |
| JourneyMap | 26.3-6.0.10 |
| Sodium | 0.9.3-alpha.1+mc26.3 |
| Simple Voice Chat | 2.6.24+26.3 |

ReplayMod's optional Voice Chat replay-authentication and Emma Bridge shutdown
workarounds are present. This test does not validate live voice/audio recording,
AI actions, configuration persistence or the full companion-mod suite.

## Short runtime workflow

Tests used isolated generated creative worlds and an offline test identity,
not the user's saved worlds or launcher accounts. A test-only harness generated
about nine seconds of recording with several vanilla entity types, sent equipment
and chest-placement commands, reopened the replay, sought playback, checked
native T-key input and twice toggled play/pause using native SDL left clicks.
It verified that the export button was present and exported a one-second clip
(20 frames at 20 fps): H.264 video at 640x360, camera GLB, and a bounded 3x3x3
chunk-section `.replay` scene including entities and world updates. Both final
clients shut down with exit code 0. The harness was not bundled in the release JAR.

| Final run | Video bytes | Scene bytes | Result |
| --- | ---: | ---: | --- |
| Windows, Prism Java 25.0.1, shaders enabled | 720272 | 1085529 | Passed, exit 0 |
| Ubuntu 25.10, Java 25.0.2, shaders enabled | 597052 | 1110076 | Passed, exit 0 |

Windows used NVIDIA RTX 5080/OpenGL and Prism's Microsoft OpenJDK 25.0.1+8 LTS.
An earlier shader-disabled run passed using Oracle Java 25.0.2. A separate
Windows Oracle Java 25.0.2 run with shaders enabled exited during native startup
with `0xC0000005`, before loading the world; its cause is not identified.
Retesting with the actual Prism runtime passed. Do not interpret these results
as compatibility with every Java 25 distribution or GPU/driver combination.

Linux ran on emmabrain: Ubuntu 25.10, kernel 6.17.0-41-generic, Java 25.0.2,
Ryzen AI Max+ 395 / Radeon 8060S, Mesa 25.2.8, SDL X11 and OpenGL 4.6.
The existing launcher libraries/assets were reused with a separate game directory;
existing instances, worlds and unrelated services were not modified.
The shader pack on both final runs was `ComplementaryReimagined_r5.9.zip`.
Nonfatal shader compiler warnings were logged. Emma Bridge logged a first-launch
Emmatone default-settings write warning in the earlier Linux baseline test;
its shutdown workaround completed. AI/configuration persistence was not tested.

Selected runtime markers: [smoke-2026-10-08.txt](test-evidence/smoke-2026-10-08.txt).

## Blender round trip

The final packaged extension ZIP was extracted and temporarily registered in
headless Blender 4.5.3 LTS with factory settings. It was not globally installed
and did not change Blender preferences. Both `.replay` files imported successfully,
then their separate GLB cameras imported into the same scene. Each camera's
animation covers frames 0 through 19 at 20 fps. Test `.blend` files were saved
and overview renders inspected. All entities listed in each archive were present.

| Imported data | Windows export | Linux export |
| --- | ---: | ---: |
| World/entity meshes | 33 | 32 |
| Vertices | 17686 | 18288 |
| Exported/imported entities | 9/9 | 8/8 |
| Armatures | 9 | 8 |
| Objects with animation actions | 41 | 39 |
| Packed texture images | 17 | 13 |

Entity counts differ because these were separate fresh runtime captures.
Armatures and animation curves were verified; this was not a frame-by-frame
visual audit of every equipment pose or material. Terrain updates use animated
mesh variants. See [Blender evidence](test-evidence/blender-2026-10-08.json).

The `.replay` scene plus ReplayMod's camera GLB can be combined in one Blender
project for custom animation. Match frame rate, export origin and frame-zero
alignment. Imported bones are not a complete animator control rig. There is no
promised round trip of Blender edits back into Minecraft.

## Limits

No full compatibility suite, custom EMF/ETF model pack, multiplayer/online AI
workflow, voice audio capture, large/long replay, cross-dimension travel, Wayland,
Vulkan or other Linux GPU/driver was tested. Minecraft shader-pack effects are
not converted into Blender shader materials. Blender materials/lighting need
normal scene setup for production rendering. Treat this as a tested short path
on the listed machines and exact mod set, not general platform certification.

## Tested artifacts

- `build/libs/worldexport-1.4.0+mc26.3.jar` (436338 bytes)
  SHA-256: `480b01e2f08c68f35d94561656de30ff3bdaad14f7b430381264d04892097442`
- `build/libs/worldexport_blender-1.4.0+mc26.3.zip` (169427 bytes)
  SHA-256: `5fabb868488168a8c4a127511ad782a24f7607fbf8d67ded85d04185591cb4de`
- Separate matching `replaymod-26.3-2.6.27-dirty.jar` (15776335 bytes)
  SHA-256: `aa577e771a463c510c71b4c63ad5e38337e24fe30406217322184769586e7ffc`

Raw build/runtime logs and generated test artifacts remain locally under
`ReplayMod/build/compat-check/exporter-test/` and the isolated emmabrain test
folder. Build output is `ReplayMod/build/logs/exporter-26.3-final-build.log`.
