# Public capture validation

**1.5.0-public.1+mc26.3** starts from public `mc26.3` commit `4b7a5c0`.
Private history, framework adapters, server companions, custom mod JARs/assets,
cosmetics, recordings and local deployment manifests are excluded.

Java 25 build and all **102 Java tests** pass. Build exporter/addon via README;
build the synthetic harness separately with `./gradlew captureHarnessJar
-Preplaymod_jar=/absolute/path/to/replaymod.jar`. The harness is excluded from the
shipped client and sources JARs.

## Verified public run on 2026-10-08

Native Linux X11/OpenGL on AMD Radeon 8060S (Mesa 25.2.8), Java 25.0.2+10 and
Blender 4.5.3 LTS passed the Minecraft harness and all four Blender regressions.
Only matching ReplayMod 26.3-2.6.27-dirty, Fabric API 0.161.0+26.3, the public
exporter and synthetic harness were installed. No private/custom mod or resource
pack was present. The export contains **76 environment samples, 5 semantic cues
and 29 sound requests**; counts describe this short fixture, not a limit.

Two idless adapter snapshots stayed distinct; duplicated server event packets
became three unique cues. Invalid JSON/unknown-version events were omitted.
Recording, native replay keyboard/mouse controls, video rendering and scene
export passed. Blender tests cover retiming/muting/event weather/time choices,
stock/custom sounds, loops/stops, vanilla model changes/nonuniform transforms,
paper head attachments, reversible color/grass-overlay materials and eight
rendered LabPBR/SEUS channel checks. Text/block displays report missing geometry.

[Sanitized evidence](../test-evidence/public-capture-2026-10-08.json).
These are short smoke tests, not full mod/renderer compatibility, dedicated-server
integration, audible output or multi-hour recording coverage.

## Reproduce in a new runtime

`tests/run_capture_smoke.py` requires a new empty runtime and existing licensed
Minecraft assets/libraries. It stages exactly four mod JARs. The opt-in harness
creates a fixture world, records, exports and exits its own process. Never install
it in a normal instance. Linux requires a native X11 display; audio uses silent
OpenAL. All test Blender launches use factory preferences and four threads.

```sh
python tests/run_capture_smoke.py \
  --runtime /path/to/new-empty-runtime \
  --java /path/to/java-25/bin/java \
  --libraries /path/to/flat-minecraft-fabric-native-libraries \
  --assets /path/to/launcher/assets --asset-index 34 \
  --exporter build/libs/worldexport-1.5.0-public.1+mc26.3.jar \
  --replaymod /path/to/replaymod-26.3.jar \
  --fabric-api /path/to/fabric-api-26.3.jar \
  --harness build/libs/capture-harness-1.5.0-public.1+mc26.3.jar \
  --blender /path/to/blender \
  --addon build/libs/worldexport_blender-1.5.0-public.1+mc26.3.zip
```

Stock-sound lookup uses the supplied asset index. Temporary audio/color packs are
synthetic. Keep logs/worlds/recordings/Blender scenes/textures under ignored
`build/`; none are distributed. No normal instance or live server is modified.
