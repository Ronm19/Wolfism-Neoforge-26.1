# Wolfism visual effects

The detailed Minecraft style pass replaces the first pass's twelve generic burst recipes with **60 named wolf palettes and 14 contour motifs** rendered by Photon. Identity still comes from the existing wolf and ability: frost forms crystal fans, storm uses broken lightning strokes, celestial wolves use halos/crescents, roots branch along the ground, magic draws angular runes, fire rises in cinder strands, and physical wolves produce brief claw/fang/shard marks. Existing damage, targeting, cooldowns, beam renderers and ability duration remain authoritative.

The contour appears first, a smaller accent follows two ticks later, and a few individual flecks disperse. Glow is restrained and restricted to magical profiles; smoke is translucent. Natural wolves do not acquire permanent magic auras. Primordial remains a physical instinct-driven hunter: earthy claw marks, without supernatural glow or root spells.

## Installing and building

Wolfism **1.0.0** targets Minecraft **26.1.2**, NeoForge **26.1.2.94**, and Java **25**.
Run `gradlew.bat build` with a Java 25 JDK. Install the resulting
`build/libs/wolfism-1.0.0.jar` on both the client and dedicated server.

The one Wolfism download contains these original runtime libraries through NeoForge
Jar-in-Jar. Separate library downloads are unnecessary for this package.

| Mod | Pinned version | Official source |
| --- | --- | --- |
| Photon | 26.1.2.2 | https://github.com/Low-Drag-MC/Photon/tree/26.1 |
| LowDragLib2 | 26.1.2.39 | https://github.com/Low-Drag-MC/LDLib2 |
| KilaGraph | 26.1.0.14 | https://github.com/Low-Drag-MC/KilaGraph |
| TerraBlender | 26.1.0.2 | https://github.com/Glitchfiend/TerraBlender |

Photon, LowDragLib2, and TerraBlender are direct embedded jars. Photon already contains
KilaGraph; LowDragLib2 already contains Kotlin 2.1.20 and Taffy 1.1.4. These nested jars
remain unchanged. Required common registries and matching network channels remain
available on both physical sides. Waystones is an optional separate integration and
is not bundled; see [runtime details](WOLFISM_RUNTIME.md).

Gradle retrieves the graphics libraries from the author's official repository at
https://maven.firstdark.dev/snapshots and TerraBlender from its pinned CurseMaven file.
Versions use exact static constraints. Transitive resolution is disabled because the
original jars already include their nested dependencies, and upstream development
POMs must not change this project's NeoForge version.

## Dedicated-server library compatibility

Photon 26.1.2.2 invokes its client editor registry discovery during common initialization.
Its `photon:fx_object` and `photon:timeline_track` registrations reference Minecraft
client particle and sound classes that do not exist on a dedicated server. Previously
those attempted class loads were caught by LowDragLib2 and logged as startup errors.
Upgrading NeoForge does not provide the absent client classes.

Wolfism supplies a narrowly scoped mixin that cancels discovery only for those two
registry identifiers on the physical dedicated server, and only with Photon 26.1.2.2.
This prevents invalid class loading; it does not filter or suppress logged exceptions.
Other common registries, codecs, setup, and network channels still initialize. The
physical client keeps Photon's original discovery, rendering, and editor behavior.
Server-side FX-object/timeline editor serialization is outside this compatibility
patch; Wolfism's server sends its own bounded effect descriptions for clients to render.

The tested-version gate deliberately leaves future Photon versions untouched. Any
library update needs a new dedicated startup, multiplayer, and graphical test. See
[implementation and validation steps](WOLFISM_RUNTIME.md).

## Actual integration and coverage

* **572 existing emission calls** across named wolf and gameplay-event sources now carry their species identity. The shared adapter also covers staff, holiday/base and shared events.
* The inventory identifies **89 count-one call sites**. Supported elemental/magical samples now use batched Photon geometry; structured colored samples remain vanilla. All count-one calls previously bypassed Photon entirely. Multiple samples from an authored beam/circle become one emitter, retaining the world-space samples. Very long paths use an endpoint-preserving bounded sample reservoir.
* Successful damage creates a species-specific impact via the actual NeoForge post-damage event. This covers all sixty wolves, including the five natural wolves whose classes had no original effect calls. Failed/cancelled hits never create false hit feedback.
* Actual wolf-owned projectile launches get a short muzzle cue; moving projectiles receive a sparse sampled trail every four ticks. Loaded projectiles do not replay launch cues.
* Six presentation tiers distinguish a tiny passive detail, a cast contour, a path, an area edge, an impact, and a major burst. Existing raw emission counts/spreads choose the adapter tier; this does not reclassify or change gameplay ultimates.
* Hearts, hit/status icons, structured dust colors, block/item fragments, zero-count directed velocity and unsupported particle types keep their precise vanilla data. Existing purely client-side ambient and dedicated beam renderers remain.

## Performance and networking

The viewing range is **48 blocks**. Source-aware per-tick batching precedes network admission, so a single large ability cannot fill the budget with one packet per point. The server allows **24 packets per observer per tick**. Both server and client give successful emergency rescues a small bounded share (server: at most four of 24; All/Decreased/Minimal client: four/two/one). Three quarters of the remaining slots prefer cast, impact, area, ultimate and authored beam signatures. Sources rotate within that share; the remaining share first visits wolves that have not received a slot, including sources producing only quiet decoration. The fallback for non-entity/shared events groups by species and spatial cell.

Minecraft's All / Decreased / Minimal particle settings allow **16 / 8 / 3 effect runtimes per client tick**, with reduced contour sampling and fewer layers at lower settings. Hard limits are **80 active effects** and **2,400 reserved live particles**. No runtime loops; visible lifetimes are 8 to 28 ticks plus at most three ticks of layer delay. Finished effects and all old-world effects are removed. Under load, cosmetic detail can be dropped without affecting gameplay.

The server holds at most 128 source buckets and eight merged groups per source. Pack admission rotates instead of favoring the first wolf that happens to tick. A long path carries at most 32 relative samples. Dimension, finite coordinates, packet enum values, species length and geometry bounds are validated. The protocol version is now 3, so client and server need matching Wolfism builds.

## Attribution and asset provenance

Photon by **KilaBash** uses **CC BY-NC-SA 4.0 with the author's additional terms**.
Its license explicitly permits unmodified Jar-in-Jar inclusion when the containing mod
is not sold separately or directly monetized. Commercial redistribution requires the
author's permission. [Official Photon license](https://github.com/Low-Drag-MC/Photon/blob/26.1/LICENSE).

LowDragLib2 and TerraBlender use LGPLv3; KilaGraph and Taffy-Java use MIT; Kotlin uses
Apache 2.0. The package preserves their unmodified jars, license texts, attributions,
and exact LGPL release source archives. Wolfism's own license does not restrict the
separate rights granted for those components. See [runtime notices and replacement
instructions](WOLFISM_RUNTIME.md), with machine-readable hashes in
`META-INF/licenses/wolfism-runtime/PROVENANCE.json` inside the packaged jar.

Wolfism references Photon's existing `circle.png`, `ring.png`, `smoke.png`, `white.png`
and `laser.png` textures at runtime from the unchanged Photon jar. No third-party
texture is modified or relicensed. Effect definitions and this integration are
authored for Wolfism; no external effect pack is used.

## Emergency rescue confirmations

Salva Last Stand and Grave Death Interception now explicitly identify the real rescuing wolf when a save succeeds. Their main confirmation burst becomes one eight-particle open bracket in the species accent, lasting eight ticks, just above the saved ally. Four of the existing 80 runtime slots and 32 of the existing 2,400 particle slots remain available for these cues; ordinary effects use the remaining 76/2,368. This replaces existing large confirmation bursts, while retaining their original sounds, hearts, outline, connecting path and all gameplay costs. Minimal can carry a four-cue wave across four ticks. Unadmitted emergency cues expire after six client ticks; larger or sustained rescue storms are still best-effort within the hard caps. Ordinary signature sources keep their source-fair share.
