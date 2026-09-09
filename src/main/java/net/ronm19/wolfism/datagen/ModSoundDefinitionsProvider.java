package net.ronm19.wolfism.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.data.SoundDefinition;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.registry.ModSounds;


public final class ModSoundDefinitionsProvider extends SoundDefinitionsProvider {

    public ModSoundDefinitionsProvider( PackOutput output) {
        super(output, Wolfism.MOD_ID);
    }

    @Override
    public void registerSounds() {

        // -------------------------------------------------------------
        // Wolf King — deep authoritative command howl
        // -------------------------------------------------------------

        add(ModSounds.WOLF_KING_HOWL, SoundDefinition.definition()
                .with(
                        sound(id("entity/wolf_king/howl_1"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(3),

                        sound(id("entity/wolf_king/howl_2"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(2),

                        sound(id("entity/wolf_king/howl_3"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(2))
                .subtitle("subtitles.wolfism.wolf_king.howl"));


        // -------------------------------------------------------------
        // Primordial Wolf — raw ancestral / first-pack howl
        // -------------------------------------------------------------

        add(ModSounds.PRIMORDIAL_WOLF_HOWL, SoundDefinition.definition()
                .with(
                        sound(id("entity/primordial_wolf/howl_1"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(3),

                        sound(id("entity/primordial_wolf/howl_2"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(2),

                        sound(id("entity/primordial_wolf/howl_3"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(2))
                .subtitle("subtitles.wolfism.primordial_wolf.howl"));


        // -------------------------------------------------------------
        // Creator Wolf — divine arrival howl + roar-like combat growl
        // -------------------------------------------------------------

        add(ModSounds.CREATOR_WOLF_HOWL, SoundDefinition.definition()
                .with(
                        sound(id("entity/creator_wolf/howl_1"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(3),
                        sound(id("entity/creator_wolf/howl_2"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(2),
                        sound(id("entity/creator_wolf/howl_3"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(2))
                .subtitle("subtitles.wolfism.creator_wolf.howl"));

        add(ModSounds.CREATOR_WOLF_GROWL, SoundDefinition.definition()
                .with(
                        sound(id("entity/creator_wolf/growl_1"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(3),
                        sound(id("entity/creator_wolf/growl_2"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(2),
                        sound(id("entity/creator_wolf/growl_3"))
                                .volume(1.0F)
                                .pitch(1.0F)
                                .weight(2))
                .subtitle("subtitles.wolfism.creator_wolf.growl"));

        /*
         * FUTURE PATTERN
         * --------------
         *
         * Every wolf can have one SoundEvent with several recordings:
         *
         * add(
         *     ModSounds.TIMBER_AMBIENT,
         *     SoundDefinition.definition()
         *         .with(
         *             sound(id("entity/timber_wolf/ambient_1")),
         *             sound(id("entity/timber_wolf/ambient_2")),
         *             sound(id("entity/timber_wolf/ambient_3"))
         *         )
         * );
         *
         * No hand-written sounds.json required.
         */
    }

    private static Identifier id( String path) {
        return Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, path);
    }
}
