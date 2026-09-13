package net.ronm19.wolfism.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.data.SoundDefinition;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.registry.ModSounds;

/** Reproduces all 60 species' combined recording events without empty placeholders. */
public final class ModSoundDefinitionsProvider extends SoundDefinitionsProvider {
    public ModSoundDefinitionsProvider(PackOutput output) {
        super(output, Wolfism.MOD_ID);
    }

    @Override
    public void registerSounds() {
        for (ModSounds.WolfVoice voice : ModSounds.WOLF_VOICES.values()) {
            for (ModSounds.Vocalization vocalization : ModSounds.Vocalization.values()) {
                SoundDefinition definition = SoundDefinition.definition()
                        .subtitle(voice.subtitle(vocalization));
                for (int variant = 1; variant <= vocalization.variants(); ++variant) {
                    definition.with(sound(Identifier.fromNamespaceAndPath(
                            Wolfism.MOD_ID, voice.asset(vocalization, variant))));
                }
                add(voice.event(vocalization), definition);
            }
        }
    }
}
