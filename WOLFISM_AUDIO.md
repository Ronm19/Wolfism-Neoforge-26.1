# Wolfism voices — natural recording revision 2

All 60 wolf types have an eight-file palette: two short ambient cues, two growls, a whine, a hurt cue, a death cue and a complete howl. All 480 positional mono Ogg Vorbis files were rebuilt from the licensed source recordings at 48 kHz with higher encoding quality. Together they occupy 9,345,468 bytes (8.91 MiB). Existing sound event IDs and subtitles remain compatible.

## What changed after the first sound pass

- Removed every production pitch/rate adjustment and the frequency-domain equalizer. Each source retains its original voice, formants, pitch movement and internal timing. Sample-rate conversion uses a high-quality resampling filter without changing playback speed.
- Selected complete recorded howl phrases between natural pauses instead of arbitrary short cuts through an ongoing call. Howls now last approximately 6.36–10.53 seconds, including their quiet boundaries. Everyday ambient cues use short natural grumbles and whimpers so normal nearby wolves do not constantly play long choruses.
- Added smooth, gradual vocal attacks, 28 ms of leading silence for immediate hurt cues or 75 ms for other cues, natural release fades, and 85 ms of final silence. Boundary silence is retained in the actual decoded OGG files.
- Removed the author-slowed ghost-breath effect and the lower-register alternate whine. The airy/undead accents now use quiet unshifted canine rasp alongside appropriate recorded creature and environmental sounds.
- Moved theme accents behind the vocal attack. Chimes begin after their sharp mallet strike; bone/firework accents are softer and delayed. Supporting layers sit roughly 19–25 dB below the main voice by their target RMS level. The final mixes use constant gain for peak control, with no hard limiter, distortion, artificial chorus, double-tracked copy, vocoder, ring modulation or noise gate.

These are designed palettes drawn from shared source recordings, not recordings of 60 different individual wolves. Each complete species palette differs. There are 462 distinct decoded clips; a few unthemed short cues intentionally reuse the same natural recording. Genuine National Park Service wolf recordings supply every howl. Close grumbles, whines and reaction cues are licensed domestic-canid recordings. Hurt/death cues are performance edits of ordinary vocalizations; no animal injury or death is claimed to have been recorded.

Zombie Wolf blends a natural canine/wolf voice with a performed zombie moan. Vampire Wolf blends wolf/canine voices with bat and canine rasp accents. Storm uses real thunder; fire wolves use recorded wildfire; watery wolves use splashes; skeletal wolves use rattle foley; Angel and Christmas use chime decays. Every supporting layer is mixed into the same OGG file as the animal voice.

## Source credits and licenses

The credits below also ship inside the mod JAR at `META-INF/licenses/wolfism-audio/CREDITS.txt`. Preserve that directory when redistributing the audio, especially the attribution for IMadeIt's CC BY 3.0 bee effect. The source licenses apply to their recorded material independently of the mod's code license. All sources were retrieved on 2026-09-11. No source author or the NPS endorses Wolfism.

| Source | Credit | License / use |
| --- | --- | --- |
| [Wolf, Denali](https://www.nps.gov/subjects/sound/sounds-wolf.htm) | National Park Service | Public domain, declared in the [NPS Sound Gallery](https://www.nps.gov/subjects/sound/gallery.htm); one wolf field recording |
| [Wolves, Yellowstone](https://www.nps.gov/yell/learn/photosmultimedia/sounds-wolves.htm) | NPS & MSU Acoustic Atlas / Jennifer Jerrett | Public domain under the [NPS Sound Library declaration](https://www.nps.gov/yell/learn/photosmultimedia/soundlibrary.htm); Soda Butte chorus and Christmas 2013 chorus |
| [Fire, Yellowstone](https://www.nps.gov/yell/learn/photosmultimedia/sounds-fire.htm) | NPS / Jennifer Jerrett | Same NPS public-domain declaration; three wildfire recordings |
| [Thunder, Yellowstone](https://www.nps.gov/yell/learn/photosmultimedia/sounds-thunder.htm) | NPS / Jennifer Jerrett | Same NPS public-domain declaration; one weather recording |
| [Dog Growl](https://opengameart.org/content/dog-growl) | bonebrah | CC0 1.0; real dog recorded with a phone |
| [Dog Sounds](https://opengameart.org/content/dog-sounds) | pauliuw | CC0 1.0; recorded barks and whines |
| [Dog Snarl, Grunt, Grumble](https://opengameart.org/content/dog-snarl-grunt-grumble) | qubodup | CC0 1.0; recorded dog play vocalizations; [original source](https://freesound.org/people/qubodup/sounds/122183/) |
| [Zombie Moans](https://opengameart.org/content/zombie-moans) | Darsycho | CC0 1.0; performed fictional-creature vocal effects |
| [Bat Screeches](https://opengameart.org/content/bat-screeches) | polymorpheva (recording), AntumDeluge (editing) | CC0 1.0; three bat excerpts; [original source](https://freesound.org/s/104205/) |
| [Bell Dings/Chimes](https://opengameart.org/content/bell-dingschimes) | PWL | CC0 1.0; four bell/chime effects |
| [Bones Rattle](https://opengameart.org/content/bones-rattle) | blukotek (recording), congusbongus (editing) | CC0 1.0; rattle foley, not a claim of actual bones; [original source](https://freesound.org/people/blukotek/sounds/249319/) |
| [6 Short Water Splashes](https://opengameart.org/content/6-short-water-splashes) | ezwa (recording), qubodup (submission) | CC0 1.0; three selected splashes |
| [Crow Caw](https://opengameart.org/content/crow-caw) | zeroisnotnull | CC0 1.0; real crow supporting Raven and Omen Wolf, not a literal raven recording |
| [25 CC0 Bang / Firework SFX](https://opengameart.org/content/25-cc0-bang-firework-sfx) | rubberduck | CC0 1.0; six firework-derived effects |
| [Single Bee Sound](https://opengameart.org/content/single-bee-sound) | IMadeIt | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/); excerpted, gently high-pass filtered, faded, gain-adjusted and mixed into all eight Bee Wolf voice files; original pitch and speed retained |

[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) and CC BY 3.0 legal texts and the bee author's original notice are included in the JAR's audio license directory. There are 15 source pages, 21 used downloads/archives, and 44 selected audio files after extraction. The unused NPS passing-car wolf recording was rejected because vehicle noise dominates it. Unused archive members and raw downloads are not bundled as runtime resources.

Complete source intervals, unchanged pitch/rate settings, mixing offsets and hashes are in `tools/audio/audio_recipes.json`. The download manifest is `tools/audio/audio_sources.json`. Raw source recordings are production inputs and are not bundled into the runtime JAR.

## Rebuilding

Normal Gradle builds use the completed OGGs and need no audio production tools. To rebuild them, install Python 3 with `numpy` and `soundfile>=0.13`, FFmpeg and bsdtar (Windows includes `tar`). From the project directory:

```text
python tools/audio/fetch_sources.py --source-dir ../audio-sources
python tools/audio/build_voices.py --source-dir ../audio-sources --preview ../Wolfism-Voice-Sampler-v2.wav
```

Use `--ffmpeg PATH` when necessary. `--preview-only` renders the six preview palettes first; a full render must follow before distributing a new set. `--compare-dir PATH` can compare against a read-only previous `sounds/entity` directory. Source download and extracted-member hashes are verified by the fetcher. The renderer makes no network requests and never changes source pitch or playback speed. Bounded encoder writes support the longer complete howls on Windows. Source choices and PCM mixes are deterministic; Vorbis container identifiers may differ between runs.

The source manifest intentionally excludes the removed processed breath and low-register alternate. Existing CC0/CC BY legal texts and required attribution remain bundled in the JAR.
