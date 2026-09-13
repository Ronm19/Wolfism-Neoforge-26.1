# Wolfism

Wolfism adds 60 wolves with distinct abilities, behavior and personalities, including elemental companions, magical wolves and seasonal holiday wolves.

## Requirements

- Minecraft 26.1.2
- NeoForge 26.1.2.94 or later
- Java 25

Install one `wolfism-1.0.0.jar` on both the client and server. Required effects and world-generation libraries are bundled in the JAR. Waystones is optional; nearby owned standing wolves can travel with their owner while seated wolves stay behind.

## Natural encounters

Wolfism replenishes wild wolves in loaded, active habitats near players, including existing worlds whose ordinary animal spawn pool is full. Encounters follow the current biome spawn tables, species restrictions and weighted rarity. The helper attempts at most one pack per dimension every five seconds and uses a default local limit of 12 wild wolves. It considers spots 32–64 blocks from the selected player and keeps the normal 24-block player exclusion distance. Each attempt checks at most 16 terrain columns, without loading new chunks. Existing-world encounters can still be limited by nearby wild wolves, terrain, time of day and species restrictions.

The server’s `wolfism-server.toml` exposes `habitatReplenishment` and `habitatWildCap`. The limit controls supplemental encounters; normal Minecraft spawning and existing animals remain governed by their own rules. Tamed companions do not count against this encounter limit. Holiday wolves keep their separate seasonal system, and Creator keeps his progression encounter.

Ordinary wolf selection weights now favor uncommon, findable encounters, with gentler increases for specialist species. Cherry retains its established weight; Primordial and Wolf King remain rare, Sculk keeps its custom-biome weight, and holiday/progression encounters retain their own rules. Weights are relative to other entries in the same biome and spawn category, not a fixed chance per second. Habitat restrictions, pack sizes and encounter limits are unchanged.

Grave Wolf appears at night in Dark Forests and Pale Gardens. Harmless grass, flowers and leaf litter no longer exclude otherwise valid spawn positions; solid obstacles, liquids, suitable-ground checks and his limit of two nearby wild Grave Wolves still apply.

## Building

With a Java 25 JDK, run:

```sh
./gradlew build
```

On Windows, use `gradlew.bat build`. The release JAR is written to `build/libs/`.

The normal development runs are `runClient`, `runServer` and `runData`. Add `-PwithWaystones` to include the optional Waystones integration in a local run. Generated game folders, build output, caches and local IDE settings are excluded from Git.

## Documentation and credits

- [Installation, bundled libraries and rebuilding](WOLFISM_RUNTIME.md)
- [Effects and performance limits](WOLFISM_VFX.md)
- [Custom audio, sources and attribution](WOLFISM_AUDIO.md)
- [Wolf surface materials](docs/WOLF_SURFACES.md)

Third-party licenses, notices and required source archives are retained under `src/main/resources/META-INF/licenses/` and in the packaged mod. Ronm19's original Wolfism work is licensed under [MIT](LICENSE). Third-party libraries and recordings retain their own licenses; the MIT notice does not relicense them. Photon's additional redistribution terms remain applicable; see [runtime licensing](WOLFISM_RUNTIME.md).
