package net.ronm19.wolfism.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.ronm19.wolfism.Wolfism;

/** Structure sets used by Creator's post-vanish reacquisition hunt. */
public final class ModStructureTags {
    public static final TagKey<Structure> CREATOR_REACQUISITION_STRUCTURES =
            TagKey.create(
                    Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath(
                            Wolfism.MOD_ID,
                            "creator_reacquisition_structures"));

    public static final TagKey<Structure> ILLAGER_WOLF_OUTPOSTS =
            TagKey.create(
                    Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath(
                            Wolfism.MOD_ID,
                            "illager_wolf_outposts"));

    private ModStructureTags() {
    }
}
