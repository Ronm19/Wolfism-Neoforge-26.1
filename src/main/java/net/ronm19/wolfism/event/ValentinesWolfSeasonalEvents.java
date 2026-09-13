package net.ronm19.wolfism.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.ValentinesWolf;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.tag.ModBiomeTags;

/** Common February encounters for Valentine's Wolf. */
@EventBusSubscriber(modid = Wolfism.MOD_ID)
public final class ValentinesWolfSeasonalEvents {
    private static final int CHECK_INTERVAL = 20 * 8;
    private static final String NEXT_ENCOUNTER = "WolfismValentinesNextEncounter";

    private ValentinesWolfSeasonalEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        HolidayWolfSeasonalSpawner.trySpawnEncounter(
                player,
                NEXT_ENCOUNTER,
                ValentinesWolf::isValentinesSeasonOpen,
                ModBiomeTags.VALENTINES_WOLF_SPAWNS,
                ModEntities.VALENTINES_WOLF.get(),
                ValentinesWolf::isSafeValentinesSurface,
                0.50F);
    }
}
