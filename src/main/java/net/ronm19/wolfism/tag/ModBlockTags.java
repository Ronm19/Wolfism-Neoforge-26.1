package net.ronm19.wolfism.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.ronm19.wolfism.Wolfism;

/** Block classifications used by shared/species gameplay systems. */
public final class ModBlockTags {
    public static final TagKey<Block> DIRE_CHARGE_BREAKABLE = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "dire_charge_breakable"));

    public static final TagKey<Block> EARTH_MANIPULABLE = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "earth_manipulable"));

    public static final TagKey<Block> GOLDEN_VALUABLE = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "golden_valuable"));

    public static final TagKey<Block> GEM_TRACKABLE_ORE = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "gem_trackable_ore"));

    private ModBlockTags() {
    }
}
