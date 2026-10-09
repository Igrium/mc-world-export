# Generic capture and Blender editing

**1.5.0-public.1+mc26.3**, targeting the matching 26.3 Fabric/ReplayMod port.
No private/custom mod, server companion or resource pack is required.

## Additive capture format

World/entity meshes, UVs, materials and animation remain in `.replay`. New optional
schema-version-1 sidecars use the same export clock (**20 ticks per second**):

| Sidecar | Captured data | Blender result |
| --- | --- | --- |
| `appearance.json` | Instance IDs/types, model parts, item selectors, head attachments, warnings/geometry counts | Custom properties and Capture Coverage panel |
| `sounds.json` | Event/sample/pack IDs, requested volume/pitch, category, loops/stops, positions and available source links/context | Editable cue objects and opt-in stock/custom audio strips |
| `environment.json` | Dimension, game/default/overworld clocks, weather allowed, rain/thunder states and intensities | Constant-keyed environment controller |
| `events.json` | Explicit semantic events and opt-in decoded-payload adapters | Editable event objects/markers |

Older archives import with an explicit legacy coverage report. Blender maps ticks
to scene FPS. Receipt timing is distinct from server timestamps; snapshot receipt
is not the completion time of a quest, level or story event.

## Appearance

Item displays resolve actual item renderer quads and retain model selectors,
left/right rotations, nonuniform scale and model hierarchy. Supported non-skull
head-item meshes attach to the captured head bone. Any supported resource
namespace works; those models/assets are not bundled here.

In **N > Replay Export > Custom Texture / PBR Packs**, choose ZIPs/folders,
precedence, LabPBR/SEUS format, base-color replacement and selected/full-scene
scope. Color-only packs apply when base-color replacement is enabled; static
grass overlays preserve existing tint/mask wiring. Restore preserves meshes,
UVs, weights and animation. Only matching textures used by the export apply.
Pack entries do not create objects or replace block/entity model geometry.

Text-display holograms and block displays currently have identity/coverage
warnings rather than captured geometry. Per-block semantic grids, arbitrary
renderer support, animated PBR, POM/displacement and complete entity-model
replacement are outside this contribution. Minecraft shader effects are not
transferred as Blender shader materials.

## Sound cues

Select a cue in **Sound Cues**. Choose its recorded stock sample via your own
local Minecraft asset index, or map a custom file to one cue/all of its event
kind. Audio loads only when explicitly placed. Move cue keys before placement;
loop delay/stop bounds control repeats/trimming. Key `sound_enabled` to mute
placed strips. Repeating placement adds strips; remove unwanted copies.

Duplicate requests stay independent for editing. Only repeated semantic network
IDs are deduplicated; idless snapshots remain distinct. Entity-bound sounds have
explicit links; positional block context and nearby entities are candidates,
not invented causality. Sound bytes are not included. References describe the
sample observed during capture/export; original random client ambience and
sample choices may be absent from older ReplayMod recordings. Requested pitch
and spatial metadata are retained, but automatic pitch/spatial mixing and voice
chat capture are not implemented. Supply custom sounds locally.

## Weather, time and semantic cues

Select **Recorded Environment** or an event in **Environment and Events**.
Weather/time effects start disabled. Key recorded state, custom overrides and
sun-clock period. The optional owned sun/rain starter rig preserves existing
World/lights; rain is a fixed local volume and thunder does not generate lightning.

Events have separate enable/audio/effect controls. Map local event audio, place
it or key weather/time at its retimed cue. Disabling effect permission skips
creating keys; previously created environment keys remain editable independently.
Placed audio follows keyed enable controls. Other mods opt in via [EVENT_API.md](EVENT_API.md).
No arbitrary quests/story caches/chat/account payloads are captured automatically.
World, animation, sound and weather/time exports work without a semantic adapter.
