package net.ronm19.wolfism.creator;

import java.util.Locale;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;

public final class CreatorDeveloperAccess {

    /**
     * MASTER SWITCH.
     *
     * true  = registered developers may receive Creator automatically.
     * false = developer auto-assignment is completely disabled.
     */
    public static final boolean ENABLE_CREATOR_AUTO_ASSIGNMENT = true;

    /**
     * TEMPORARY TEST SWITCH.
     *
     * Set true when you want to test the normal-player Creator progression
     * even while playing on a registered developer account.
     */
    public static final boolean FORCE_NON_DEVELOPER_FOR_TESTING = false;

    /**
     * Minecraft profile names that count as official Wolfism developers.
     *
     * Keep these lowercase because player names are normalized before checking.
     */
    private static final Set<String> REGISTERED_DEVELOPERS = Set.of(
            "dev",
            "maxgaming6"
    );

    private CreatorDeveloperAccess() {
    }

    public static boolean isDeveloper(ServerPlayer player) {
        if (!ENABLE_CREATOR_AUTO_ASSIGNMENT) {
            return false;
        }

        if (FORCE_NON_DEVELOPER_FOR_TESTING) {
            return false;
        }

        String name = player.getGameProfile()
                .name()
                .toLowerCase(Locale.ROOT);

        return REGISTERED_DEVELOPERS.contains(name);
    }
}