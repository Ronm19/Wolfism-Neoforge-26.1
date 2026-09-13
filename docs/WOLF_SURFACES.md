# Minecraft-native wolf surfaces

Wolfism adds a restrained material pass to every species and its pup. The renderer adds texel-aligned fur, bone, stone, metal or foliage shading over the existing colored texture. All original wolf PNGs remain byte-for-byte intact. This is a rendering/style change, not a claim that 136 original textures were repainted by hand.

Silhouettes, models, animations, markings and original colors remain authoritative. The neutral detail changes brightness by at most -18%/+16% before ordinary lighting. Eyes, muzzles, ears and extra wing/decorative UV regions retain the original treatment. The shader has no emissive or bloom term. Wetness, damage overlays, lightmap and fog use the original entity pipeline conventions. Collars, armor, invisibility and outlines retain their existing passes; glowing-outline bodies temporarily use their original surface.

## Material families

| Surface | Species selection |
| --- | --- |
| Soft fur | Default for the remaining species, including Timber and the ordinary fur of several holiday wolves |
| Shaggy fur | Arctic, Frost, Dire, Wolf King, Primordial, Christmas, Ancient |
| Sleek fur | Black, Shadow, Phantom, Vampire, Blood, Water, Drowned, Toxic, Void, Spirit, Spectral, Astral, Lunar, Solar, Infernal, Fire, End, Rift |
| Feather detail | Raven, Angel |
| Bone | Skeleton, Wither, Grave |
| Stone | Earth, Magma, Ash, Blaze, Sculk |
| Metal | Blade, War, Gem, Golden |
| Foliage | Cherry, Mushroom, Violet, Easter |

Eight material families use 32 pipeline variants to cover standard and expanded adult/pup UV layouts. Render types are cached per texture. The original 60 wolf designs have not been replaced with a shared generic skin.

## Generated material provenance

`assets/wolfism/textures/entity/material/wolf_surface_atlas.png` is a new grayscale atlas generated with the built-in image generation tool for this project on September 11, 2026. No original wolf texture was submitted for repainting or altered. The generation brief called for a flat, seamless, Minecraft-style 4-by-2 atlas containing soft fur, shaggy fur, sleek fur, feather, bone, stone, brushed metal and foliage detail, with neutral grayscale, crisp pixel clusters, no labels, no perspective and no glow. The returned image is 1774 by 887 pixels and is stored unedited in the project; the shader samples it on a 32-texel logical grid within each tile.

Material strength, pattern sampling and bounded brightness variation keep the additional detail legible without adding a glow to natural wolves.
