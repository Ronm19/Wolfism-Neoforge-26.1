package net.ronm19.wolfism.registry;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;

/** Shared development/gameplay rules for Wolfism systems. */
public final class ModGameRules {
    /**
     * Game rules are registry objects in 26.1. They must be registered through
     * NeoForge's registry event, not by calling GameRules.register... from the
     * mod constructor after the vanilla registry has already been frozen.
     */
    public static final DeferredRegister<GameRule<?>> GAME_RULES =
            DeferredRegister.create(BuiltInRegistries.GAME_RULE, Wolfism.MOD_ID);

    /**
     * Development override for seasonal wolves.
     *
     * <p>False by default. When enabled, seasonal date checks are bypassed for
     * natural spawning so Holiday Wolves can be tested year-round.</p>
     */
    public static final Supplier<GameRule<Boolean>> FORCE_HOLIDAY_SPAWNS =
            GAME_RULES.register(
                    "force_holiday_spawns",
                    () -> new GameRule<>(
                            GameRuleCategory.SPAWNING,
                            GameRuleType.BOOL,
                            BoolArgumentType.bool(),
                            GameRuleTypeVisitor::visitBoolean,
                            Codec.BOOL,
                            value -> value ? 1 : 0,
                            false,
                            FeatureFlagSet.of()));

    private ModGameRules() {
    }
}
