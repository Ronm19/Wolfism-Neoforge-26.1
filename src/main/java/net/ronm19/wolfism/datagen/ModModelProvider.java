package net.ronm19.wolfism.datagen;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.data.PackOutput;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.registry.ModItems;

public final class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, Wolfism.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(ModItems.TIMBER_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ARCTIC_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BLACK_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SAND_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DIRE_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FIRE_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FROST_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STORM_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WATER_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EARTH_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SOLAR_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LUNAR_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPIRIT_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SHADOW_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GOLDEN_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CHERRY_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.VIOLET_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GEM_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MUSHROOM_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BEE_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ZOMBIE_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SKELETON_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HUSK_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DROWNED_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PHANTOM_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BLOOD_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.END_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SCULK_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.INFERNAL_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.OMEN_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ASTRAL_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ANGEL_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DEMON_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GRAVE_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RIFT_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.VOID_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SALVA_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WOLF_KING_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PRIMORDIAL_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MAGMA_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.VAMPIRE_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPECTRAL_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TOXIC_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WAR_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ILLAGER_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ANCIENT_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BLADE_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RAVEN_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.COMMAND_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ASH_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WITHER_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BLAZE_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HALLOWEEN_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CHRISTMAS_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SAINT_PATRICKS_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NEW_YEARS_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.VALENTINES_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EASTER_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FIREWORK_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CREATOR_WOLF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);

        generateWolfStaffModels(itemModels);
    }
    /**
     * Generates the Wolf Staff as a proper handheld item, including every command-mode
     * texture/model used by WolfStaffItem through DataComponents.ITEM_MODEL.
     *
     * <p>The texture files are still normal assets:</p>
     * <ul>
     *     <li>wolf_staff_follow.png</li>
     *     <li>wolf_staff_sit.png</li>
     *     <li>wolf_staff_guard.png</li>
     *     <li>wolf_staff_attack.png</li>
     *     <li>wolf_staff_recall.png</li>
     * </ul>
     *
     * <p>Datagen creates both the handheld model JSON and the arbitrarily named
     * client-item definition required by the staff's runtime ITEM_MODEL swap.</p>
     */
    private static void generateWolfStaffModels(ItemModelGenerators itemModels) {
        Item staff = ModItems.WOLF_STAFF.get();

        // Fallback/default registered item model. WolfStaffItem immediately resolves
        // to FOLLOW, but keeping the base model handheld prevents an ugly flat frame.
        itemModels.generateFlatItem(staff, ModelTemplates.FLAT_HANDHELD_ITEM);

        generateWolfStaffMode(itemModels, staff, "follow");
        generateWolfStaffMode(itemModels, staff, "sit");
        generateWolfStaffMode(itemModels, staff, "guard");
        generateWolfStaffMode(itemModels, staff, "attack");
        generateWolfStaffMode(itemModels, staff, "recall");
    }

    private static void generateWolfStaffMode(
            ItemModelGenerators itemModels,
            Item staff,
            String mode) {

        String suffix = "_" + mode;

        // models/item/wolf_staff_<mode>.json
        Identifier modelId = itemModels.createFlatItemModel(
                staff,
                suffix,
                ModelTemplates.FLAT_HANDHELD_ITEM);

        // items/wolf_staff_<mode>.json
        // These are the IDs WolfStaffItem writes into DataComponents.ITEM_MODEL.
        Identifier clientItemId = Identifier.fromNamespaceAndPath(
                Wolfism.MOD_ID,
                "wolf_staff_" + mode);

        itemModels.itemModelOutput.register(
                clientItemId,
                new ClientItem(
                        ItemModelUtils.plainModel(modelId),
                        ClientItem.Properties.DEFAULT));
    }

}
