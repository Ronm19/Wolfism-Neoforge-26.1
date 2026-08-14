package net.ronm19.wolfism.registry;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.ArcticWolf;
import net.ronm19.wolfism.entity.custom.TimberWolf;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(Wolfism.MOD_ID);

    public static final Supplier<EntityType<TimberWolf>> TIMBER_WOLF = ENTITY_TYPES.registerEntityType(
            "timber_wolf",
            TimberWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(10));

    public static final Supplier<EntityType<ArcticWolf>> ARCTIC_WOLF = ENTITY_TYPES.registerEntityType(
            "arctic_wolf",
            ArcticWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(10));

    private ModEntities() {
    }
}
