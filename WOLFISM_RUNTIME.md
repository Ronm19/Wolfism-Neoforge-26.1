# Wolfism 1.0.0 runtime package

## Installation

Use Minecraft **26.1.2**, NeoForge **26.1.2.94 or later**, and Java **25**.
The Survival Lab profile uses NeoForge **26.1.2.109**. Install the single
`wolfism-1.0.0.jar` in the client and server `mods` folders. NeoForge discovers its
embedded original libraries automatically. Existing separate copies are unnecessary;
third-party modpacks that select other dependency versions need their own compatibility
check. The Photon server compatibility hook is deliberately limited to version 26.1.2.2.

Public Wolfism 1.0.0 uses the Minecraft/NeoForge family introduced during the
internal builds after Wolfism 1.1.1. The supported Waystones release needs this newer
family. Public release numbering starts at 1.0.0; older 1.2.x test reports retain their
original build numbers. Back up a world before opening it in a newer Minecraft installation.

Waystones is optional and remains a separate user choice. For the tested combination,
install Waystones **26.1.2.16**, Balm **26.1.2.14**, and Shogi **26.1.2.8** on both sides.
Their official Modrinth version identifiers are `VaeMtOuU`, `XfUUh0yW`, and `LVd3JsbR`.
None of those optional mods, their sources, or their API classes are bundled with Wolfism.

Both client and server need the same Wolfism build. Public 1.0.0 retains version 3
of the custom effects channel, introduced in internal test builds starting at 1.2.1
for emergency rescue cues.

## Companion controls and Waystones

Right-click an owned wolf normally to sit it down or stand it up. That individual
order releases its previous Staff command, including Staff SIT, so the Staff no
longer immediately reverses the manual order. Use the Staff again to give it a new
pack command. Feeding, dyeing and armor interactions keep the current Staff order.

With Waystones installed, completing an ordinary trip automatically brings owned,
standing Wolfism companions within 24 blocks. Wolves ordered to sit stay behind.
Waystones prepares destination chunks and waits briefly for their entity data before
companion transfer. A safe landing area is required; a blocked or canceled transfer
leaves the original wolf in its source dimension. A short message reports the number
that arrived and any eligible companions that could not travel. Another player's
wolves are not enrolled. Recovery, NoAI, riding and vehicle states are excluded.

The server's `wolfism-server.toml` provides `waystonesCompanions` (default `true`)
and `waystonesCompanionRange` (default `24`, allowed `4` to `48`). This Wolfism feature
works independently of Waystones' general pet-transport toggle. It respects actual
Waystones trip checks, cancellation and entity teleport denial rules. Turning the
Wolfism feature off leaves Waystones' own generic pet and leash rules in charge.
Waystones may separately carry leashed pets; its native leash transport is not
controlled by Wolfism's automatic standing-companion enrollment.

For a newly loaded Waystones destination,companion travel waits within the same Waystones transaction until its entity data is ready. If readiness cannot be established within the bounded wait, the trip reports failure before charging or moving the owner. Temporary loading requests expire and are released on completion or cancellation. A third-party mod using the direct synchronous teleport API can leave a companion home when a destination is still cold; the normal asynchronous Waystones route performs the readiness wait.

## Aquatic companions

When ordered to stand, Water and Drowned Wolves can follow their owner underwater
and toward a reachable shore. When the owner steps onto land, they keep swimming toward the
shore route's waypoints until they can walk out. Water Wolf's idle underwater
patrol yields at the existing 3.5-block owner-follow distance. Sitting commands,
combat/rescue priorities, movement speeds and the normal quadruped pose are preserved.

The aquatic regression checks cover flowing-water resistance, submerged breathing
and gravity, upward owner following, and entry/exit over a stepped shore. Controlled
pools and an ordinary-wolf movement comparison do not replace extended Survival play over
varied natural terrain or compatibility checks with third-party fluids.

## Temporary terrain and Halloween lighting

Magma's obsidian crossings and eruption magma remember the exact lava they replaced.
Cleanup continues independently of the creating wolf and survives a normal save and
restart. Unloaded terrain waits until its surrounding chunks are loaded; the cleanup
does not load chunks itself. Occupied crossings wait for travelers to leave. Claim
denials postpone restoration without discarding its saved record, and turning off
mob griefing stops new terrain creation without stranding previously authorized work.

Mining or replacing a temporary block relinquishes Wolfism's ownership, including a
successful player placement of the same block state. Harvesting does not also recreate
the original lava. The repair applies to newly recorded terrain: untracked blocks from
older builds cannot safely be identified and are not guessed or migrated.

Halloween's level-10 personal light ignores other recorded Halloween lights when
checking a daytime dark room, while still respecting sunlight, torches and other
external light sources. Daytime combat presentation remains active, and the coat
remains non-emissive. Shared lights keep their remaining owners; saved, expired lights
can be cleaned up without their original wolf after the surrounding terrain reloads.
Changed blocks and successful player replacements are preserved.

These records support ordinary saved-world restarts. They do not promise crash-atomic
transactions across Minecraft's independently written chunk and saved-data files.

## Contents and provenance

| Component | Release | Location in the package | License |
| --- | --- | --- | --- |
| Photon | 26.1.2.2 | Wolfism's Jar-in-Jar directory | CC BY-NC-SA 4.0 plus author terms |
| LowDragLib2 | 26.1.2.39 | Wolfism's Jar-in-Jar directory | LGPL-3.0 |
| TerraBlender | 26.1.0.2 | Wolfism's Jar-in-Jar directory | LGPLv3 |
| KilaGraph | 26.1.0.14 | Nested inside original Photon | MIT |
| Kotlin standard library | 2.1.20 | Nested inside original LowDragLib2 | Apache-2.0 |
| Taffy-Java | 1.1.4 | Nested inside original LowDragLib2 | MIT |

All six binaries are byte-for-byte unchanged. Their SHA-256 hashes, official source
URLs, source commits, and license/source archive hashes are in
`src/main/resources/META-INF/licenses/wolfism-runtime/PROVENANCE.json`; the same entry
is included inside the built jar. `NOTICE.txt` beside it supplies credits and library
replacement instructions. The complete matching LGPL source archives, including their
build scripts, are in `META-INF/third-party-sources` inside the jar and project resources.
They are ordinary source ZIPs, outside NeoForge's mod discovery directory.

Wolfism's own license applies to its own work. It does not remove the LGPL rights to
modify, replace, or reverse engineer the covered libraries to debug modifications.
An interface-compatible modified library can replace its separate nested ZIP entry;
there is no proprietary signature/encryption check. Preserve its nested dependencies
and update Jar-in-Jar/version metadata if choosing a different compatible version.
See the bundled NOTICE for full instructions. The integrity verifier reports altered
release bytes for QA purposes; it is not a runtime restriction on modified libraries.

Photon's author explicitly permits Jar-in-Jar redistribution when the containing mod
is not sold separately or directly monetized. Commercial redistribution requires the
author's written permission. This bundle preserves the original Photon binary and
license. [Official license](https://github.com/Low-Drag-MC/Photon/blob/26.1/LICENSE).

The 480 active custom wolf clips and their source attributions are unchanged.
Twelve unused legacy recordings with undocumented provenance were intentionally
excluded from the current source and release JAR: Creator's three underscore-named
growls and three howls, Primordial's three howls, and Wolf King's three howls.
They are retained only in a private backup outside publication and resource folders.
The current sound registry and dynamic voice lookup do not use them. The release
verifier rejects their return, missing active files, and unreferenced audio.

This release is prepared for free distribution without monetization under Photon's
existing Jar-in-Jar grant. Preserve every bundled license and credit. See
[Publishing notes](PUBLISHING.md) for the source-export and distribution constraints.

TerraBlender resolves from fixed CurseMaven file `7834071`, but a component-metadata
rule advertises its official `com.github.glitchfiend:TerraBlender-neoforge:26.1-26.1.0.2`
identity to Jar-in-Jar. This lets NeoForge recognize the same library when another mod
bundles it from the author's Maven publication. The upstream POM and every class/resource
were compared: only manifest build timestamps differ between those published binaries.
Wolfism keeps the original pinned binary unchanged. This mapping is specific to that
file/release and must be reverified when updating TerraBlender.

## Dedicated-server discovery correction

In Photon 26.1.2.2, common initialization eagerly calls `PhotonRegistries.Client.load()`.
The reflection discovery of FX-object implementations encounters the client Particle
base class; timeline discovery encounters client SoundInstance through AudioTrackEditor.
These classes are absent from the dedicated-server Minecraft distribution. LowDragLib2
caught the failures and logged startup errors; replacing NeoForge alone cannot make
those client classes exist.

`PhotonServerRegistryMixin` intercepts `PhotonRegistries.Client.registerStaticInstances`
before any class discovery, and only for `photon:fx_object` and `photon:timeline_track`.
`WolfismMixinPlugin` enables it only on the physical dedicated server with exactly
Photon 26.1.2.2. All other number-function, material, shape, mesh, animation and shared
registries/codecs remain initialized, as do the required network channels. No logs are
filtered and no exception reporting is disabled. The physical client receives the
unmodified original behavior and can create/render the full FX/timeline objects.

This is a bounded Wolfism integration correction, not general server support for
Photon's client editor or arbitrary server-side FX/timeline deserialization. Wolfism
sends its own effect descriptions and creates Photon objects on clients. The original
third-party jars are unchanged. A future Photon version requires fresh verification
and is intentionally outside the mixin's version gate.

Upstream references inspected on 12 September 2026:

- [Photon source at the exact release commit](https://github.com/Low-Drag-MC/Photon/tree/8ceb79e680ba977f9b38cfa660b9053d073935fa).
- [LowDragLib2 source at the exact release commit](https://github.com/Low-Drag-MC/LDLib2/tree/3f7fc21e033498b5f03a45ca26f9e254fe3889f9).
- [NeoForge Jar-in-Jar documentation](https://docs.neoforged.net/toolchain/docs/dependencies/jarinjar/).

## Rebuilding

With a Java 25 JDK:

```text
gradlew.bat build
python tools/verify_runtime_bundle.py build/libs/wolfism-1.0.0.jar --report build/runtime-bundle-audit.json
```

Optional compile dependencies resolve from the official restricted Modrinth Maven
repository even without the runtime flag. Shogi's unchanged nested API is extracted
by `extractShogiApi` into a compile-only build directory. `-PwithWaystones` adds the
three optional mods to the local development runtime, for example:

```text
gradlew.bat -PwithWaystones runClient
```

The archive verifier checks all six unchanged nested libraries, one copy of every
required mod, exact version metadata, license/source integrity, required both-side
dependency declarations, the server-only mixin registration, and exclusion of optional
Waystones/Balm/Shogi code. It verifies package contents; it is not a runtime restriction
on modified libraries.

## Optional Iris and Sodium pairing

For Iris 1.11.3 on Minecraft 26.1.2, use Sodium 0.9.1. Sodium 0.9.2 changes a rendering call used by this Iris release and can crash when entering a world. The Iris 1.11.3 / Sodium 0.9.1 pair passed a world-connection check with Wolfism 1.2.1, Waystones, Balm, Shogi, JEI and Jade. No shader pack was enabled during this check. Neither Iris nor Sodium is required by Wolfism.

See the [Iris rendering integration](https://github.com/IrisShaders/Iris/blob/26.1/common/src/main/java/net/irisshaders/iris/compat/sodium/mixin/MixinRenderRegionManager.java) and [official Sodium 0.9.1 release](https://modrinth.com/mod/sodium/version/UETw0GTI).
