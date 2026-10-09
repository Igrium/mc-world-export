# Generic capture and Blender editing - experimental 26.3 contribution

`1.5.0-public.1+mc26.3` adds resolved item-display models and head attachments,
entity identities/coverage reports, sound-reference cues, weather/time tracks,
and an opt-in semantic event API. The matching Blender extension lets you retime,
suppress and replace cue audio, key weather/time choices, and apply color-only
or PBR resource packs with reversible assignments and grass-overlay support.

The public implementation requires no private/custom mod, server companion or
resource pack. Mods can opt into [the generic event API](docs/EVENT_API.md).
See [capture format, editing workflow and coverage](docs/CAPTURE.md) and
[public-build test results](docs/PUBLIC_TESTING.md). No custom mod binaries,
cosmetics, recordings, texture packs or private commit history are included.

The original project and public 26.3 port instructions follow.

---

# Minecraft 26.3 Fabric port (experimental)

This fork ports Igrium's Replay Exporter to Minecraft 26.3 and the matching
[DragonbornElric ReplayMod port](https://github.com/DragonbornElric/ReplayMod).
It is a separate companion mod, not a replacement for ReplayMod.

Requirements: Minecraft 26.3, Java 25, Fabric Loader 0.19.5+, Fabric API
0.161.0+26.3 and the matching rebuilt ReplayMod. The Blender importer requires
Blender 4.4+; the local round-trip test used Blender 4.5.3 LTS.

Build on Windows with `./build-26.3.ps1`, or pass `-ReplayModJar` explicitly.
For other platforms, use Java 25 and:

```sh
./gradlew build buildAddon -Preplaymod_jar=/absolute/path/to/replaymod-26.3.jar -Pblender_executable=/path/to/blender
```

Output: `build/libs/worldexport-1.5.0-public.1+mc26.3.jar` and
`build/libs/worldexport_blender-1.5.0-public.1+mc26.3.zip`.
Install the JAR alongside ReplayMod and install the ZIP through Blender's extension
preferences. Use ReplayMod's **Export Replay File** button, then Blender's
**File > Import > Minecraft Replay File (.replay)**.

[Build and limited compatibility results](COMPATIBILITY.md) describe the exact
mod versions, platforms and short test scope. These are not complete companion-mod
feature or compatibility suites. EMF/ETF being installed does not establish that
all custom model/resource packs export correctly. Minecraft shader-pack effects
are not transferred as Blender shader materials.

Version 1.4.1 adds a **Replay Export > Custom Texture / PBR Packs** sidebar in Blender.
Apply your own resource-pack ZIPs or extracted folders using exported texture
identities, with LabPBR 1.3 or SEUS decoding and reversible material assignments.
See [custom PBR usage and test scope](PBR.md). Terrain from older exports needs
a fresh export with this JAR; an existing recording can be reused.

Camera animation is exported separately by ReplayMod's camera-path option as
GLB. Replay Exporter's `.replay` file contains world/entity data, not the camera.
Match the frame rate, frame-zero alignment and export origin when combining them.

The original project documentation follows.

---

# Igrium's Replay Exporter

An addon for the [Replay Mod](https://www.replaymod.com/) that allows you to export your replays
into [Blender](https://www.blender.org/).

**This mod is designed for experienced Blender users!** It is designed to export your Minecraft scene, and nothing more.

**[Example](https://youtu.be/eDdBe3me0es)**

# Usage

## Exporting

To begin, open Minecraft and record the replay you wish to export. If you don't know how to do that, this mod is not for
you. Then, open the replay editor and add at least two time and camera keyframes. These will determine the start and end
points of your animation.

> Warning: nether portals and other forms of cross-dimension travel are not supported. Use multiple exports you intend
> on doing that.

Once you're happy with your replay, open the render screen and click "Export Replay File":

<img title="" src="doc/images/export_replay.png" alt="the Export Replay button" data-align="inline">

This will open a screen for you to select an output file and configure some replay settings. Adjust
your bounds to be as tight as possible. Raise the lower depth as high as it will go before it cuts off the visible
landscape, and reduce the perimeter depending on your planned shot.

The "Block Updates" and "Entities" regions add significantly more overhead than the "World" region, so it's recommended
to
tighten those even further.

<img title="" src="doc/images/export_settings.png" alt="The Export Settings menu" data-align="center">
<img title="" src="doc/images/export_bounds.png" alt="The Export Bounds menu" data-align="center">

## Importing

After exporting from Minecraft, you should be left with a `.replay` file, which can be imported into Blender using the
provided addon.

Once you've ensured the addon is installed, create a new project and go to
`File > Import > Minecraft Replay File (.replay)`. Navigate to your file, but before you import it, note the settings on
the right:

<img src="doc/images/import_settings.png" title="" alt="Replay Import Settings" data-align="center">

The main two you need to worry about are `World` and `Entities`. While you usually want to import both,
they each can take quite a bit of time, so it may be useful to disable one or the other.

Everything else can be left as default.

Once you're ready, click "Import Minecraft Replay" at the bottom. Blender will appear to hang for a few minutes. Don't
worry; this is because the contents of a Minecraft world is quite large, and Blender provides no (reliable) way to
update the UI while it is processing. (It might do to enable the system console to monitor progress.)

Once it is finished importing, you should have a Minecraft world and most of its entities in your scene! If something
goes wrong, make sure to submit a bug report!

# Contributing

Replay Exporter is a Fabric mod, so building it follows the same basic principles as any other mod (which will not be
reiterated here). However, due to it's nature as a "companion mod", Replay Exporter ships with slightly more complex
build features than others.

## Mod Integrations

Mod integrations are split into separate source sets: `main` holds only the common code for encoding replay animations,
and `replaymod` is responsible for actually triggering the export process while using Replay Mod. This is intended to
support for other mods such as Flashback and ReplayLab in the future, but only Replay Mod is implemented for now.

This segregation of disciplines ensures that no code dependencies from integrations find their way into contexts where
the target mod might not be present. If you were to try and depend on code from Replay Mod (or anything from the
`replaymod` source set) in `main`, you would get a compile error. However, this is purely a compile-time construct. At
runtime, Replay Exporter is still packaged as a singular mod, avoiding code-paths depending on mods that are not
present.

## Debugger

The exception to this is the `debugger` source set:

`debugger` is a separate "test" mod which provides an imgui-based UI for inspecting exported replay files. This is
useful for debugging entity model adapters, but it's not of any concern to the average user, so that code isn't shipped
with the full build.

Keep in mind that the debugger isn't exactly the *cleanest* code. It relies on native libraries that might be fragile
across different operating systems, and as a niche development tool, maintaining it not a priority.

It can be launched with `./gradlew runDebuggerClient`

## Blender Addon

Version 2 of the addon is structured very differently than Version 1. The primary change is the addition of a
`prefabs.blend` file.

Unlike the first version, which generated all its shaders in code, the new version includes a number of manually-created
assets that get appended as needed. This makes it much easier to ship stock effects such as enchantment glints, as the
node-tree can be created using the Blender interface, rather than having to do it in code.

`./gradlew buildAddon` will compile the addon into a zip and place it in `build/libs`, updating the version tag to
whatever is defined in `gradle.properties`. Note that this requires Blender in your PATH, for that's what's used to
build the extension.

The addon is slated to support Blender `4.4 - Current`. **Do *not* save the prefabs file in any version newer than 4.4!**

