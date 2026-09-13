package net.ronm19.wolfism.registry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;

/** One species voice registry shared by gameplay, sound datagen and validation. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Wolfism.MOD_ID);

    public enum Vocalization {
        AMBIENT("ambient", 2),
        GROWL("growl", 2),
        WHINE("whine", 1),
        HURT("hurt", 1),
        DEATH("death", 1),
        HOWL("howl", 1);

        private final String path;
        private final int variants;

        Vocalization(String path, int variants) {
            this.path = path;
            this.variants = variants;
        }

        public String path() { return this.path; }
        public int variants() { return this.variants; }
    }

    public record WolfVoice(String species, Map<Vocalization, Holder<SoundEvent>> events) {
        public Holder<SoundEvent> event(Vocalization vocalization) {
            return this.events.get(vocalization);
        }

        public String subtitle(Vocalization vocalization) {
            return "subtitles.wolfism." + this.species + "." + vocalization.path();
        }

        public String asset(Vocalization vocalization, int variant) {
            return "entity/" + this.species + "/" + vocalization.path() + variant;
        }
    }

    // Enumerate the actual entity registrations; projectiles and portals do not
    // receive voices. Adding another wolf automatically adds its six events.
    public static final Map<String, WolfVoice> WOLF_VOICES = createWolfVoices();

    // Preserve existing ability event IDs and all species-specific playback
    // timing/volume. These aliases now resolve to the finished combined recordings.
    public static final Holder<SoundEvent> WOLF_KING_HOWL = voice("wolf_king").event(Vocalization.HOWL);
    public static final Holder<SoundEvent> PRIMORDIAL_WOLF_HOWL = voice("primordial_wolf").event(Vocalization.HOWL);
    public static final Holder<SoundEvent> CREATOR_WOLF_HOWL = voice("creator_wolf").event(Vocalization.HOWL);
    public static final Holder<SoundEvent> CREATOR_WOLF_GROWL = voice("creator_wolf").event(Vocalization.GROWL);

    private static Map<String, WolfVoice> createWolfVoices() {
        Map<String, WolfVoice> voices = new LinkedHashMap<>();
        for (var entity : ModEntities.ENTITY_TYPES.getEntries()) {
            String species = entity.getId().getPath();
            if (!species.endsWith("_wolf") && !species.equals("wolf_king")) continue;
            Map<Vocalization, Holder<SoundEvent>> events = new EnumMap<>(Vocalization.class);
            for (Vocalization vocalization : Vocalization.values()) {
                events.put(vocalization, SOUND_EVENTS.register(
                        "entity." + species + "." + vocalization.path(),
                        SoundEvent::createVariableRangeEvent));
            }
            voices.put(species, new WolfVoice(species, Collections.unmodifiableMap(events)));
        }
        return Collections.unmodifiableMap(voices);
    }

    public static WolfVoice voice(String species) {
        WolfVoice voice = WOLF_VOICES.get(species);
        if (voice == null) throw new IllegalArgumentException("No Wolfism voice for " + species);
        return voice;
    }

    public static WolfVoice voice(EntityType<?> type) {
        return voice(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    private ModSounds() {
    }
}
