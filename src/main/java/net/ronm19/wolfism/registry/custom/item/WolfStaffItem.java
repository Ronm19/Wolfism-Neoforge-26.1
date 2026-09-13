package net.ronm19.wolfism.registry.custom.item;

import net.ronm19.wolfism.vfx.WolfVfx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.WolfStaffCombatRole;
import net.ronm19.wolfism.entity.WolfStaffCommandMode;
import org.jspecify.annotations.Nullable;

/**
 * Universal command item for owned Wolfism wolves.
 *
 * <p>Controls intentionally stay simple:</p>
 * <ul>
 *     <li>Right click with no aimed target: cycle mode and issue it.</li>
 *     <li>Right click toward a living target: focused tactical Attack.</li>
 *     <li>Shift + right click: emergency Recall within 100 blocks.</li>
 * </ul>
 */
public final class WolfStaffItem extends Item {
    private static final String MODE_KEY = "WolfStaffMode";
    private static final String RECALL_FLASH_KEY = "WolfStaffRecallFlash";

    private static final double NORMAL_COMMAND_RADIUS = 100.0D;
    private static final double RECALL_RADIUS = 100.0D;
    private static final double FOCUS_TARGET_RANGE = 30.0D;
    private static final double FOCUS_DISPATCH_RADIUS = 64.0D;
    private static final double AIM_DOT_THRESHOLD = 0.955D;
    private static final int MAX_FOCUSED_RESPONDERS = 8;
    private static final int FOCUSED_TARGET_TICKS = 20 * 20;
    private static final int RECALL_FLASH_TICKS = 10;

    public WolfStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            recall(serverLevel, player, stack);
            return InteractionResult.SUCCESS;
        }

        LivingEntity aimedTarget = findAimedTarget(serverLevel, player);
        if (aimedTarget != null) {
            setMode(stack, WolfStaffCommandMode.ATTACK);
            int responders = dispatchFocusedAttack(serverLevel, player, aimedTarget);

            player.sendOverlayMessage(Component.translatable(
                    "message.wolfism.wolf_staff.focus_attack",
                    aimedTarget.getDisplayName(),
                    responders));

            playCommandFeedback(serverLevel, player, WolfStaffCommandMode.ATTACK);
            return InteractionResult.SUCCESS;
        }

        WolfStaffCommandMode next = getMode(stack).next();
        setMode(stack, next);
        int commanded = issueMode(serverLevel, player, next);

        player.sendOverlayMessage(Component.translatable(
                "message.wolfism.wolf_staff.mode",
                Component.translatable("message.wolfism.wolf_staff.mode." + next.serializedName()),
                commanded));

        playCommandFeedback(serverLevel, player, next);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        int flash = getRecallFlashTicks(stack);
        if (flash <= 0) {
            ensureModeModel(stack);
            return;
        }

        if (flash == 1) {
            setRecallFlashTicks(stack, 0);
            applyModeModel(stack, getMode(stack));
        } else {
            setRecallFlashTicks(stack, flash - 1);
        }
    }

    private static int issueMode(ServerLevel level, Player player, WolfStaffCommandMode mode) {
        List<AbstractWolfismWolf> wolves = ownedWolves(level, player, NORMAL_COMMAND_RADIUS);
        for (AbstractWolfismWolf wolf : wolves) {
            wolf.applyWolfStaffCommand(mode);
        }
        return wolves.size();
    }

    private static void recall(ServerLevel level, Player player, ItemStack stack) {
        List<AbstractWolfismWolf> wolves = ownedWolves(level, player, RECALL_RADIUS);
        for (AbstractWolfismWolf wolf : wolves) {
            wolf.beginWolfStaffRecall();
        }

        setRecallFlashTicks(stack, RECALL_FLASH_TICKS);
        stack.set(DataComponents.ITEM_MODEL, modelId("recall"));

        WolfVfx.sendParticles(level,
                ParticleTypes.END_ROD,
                player.getX(), player.getY(0.6D), player.getZ(),
                28, 1.4D, 0.55D, 1.4D, 0.035D);
        WolfVfx.sendParticles(level,
                ParticleTypes.POOF,
                player.getX(), player.getY(0.2D), player.getZ(),
                22, 1.7D, 0.15D, 1.7D, 0.02D);

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                1.0F,
                1.45F);

        player.sendOverlayMessage(Component.translatable(
                "message.wolfism.wolf_staff.recall",
                wolves.size()));
    }

    private static int dispatchFocusedAttack(ServerLevel level, Player player, LivingEntity target) {
        List<AbstractWolfismWolf> available = ownedWolves(level, player, FOCUS_DISPATCH_RADIUS);
        available.removeIf(wolf -> !wolf.canParticipateInWolfismCombat()
                || !wolf.canAttack(target));

        double targetDistance = player.distanceTo(target);
        available.sort(Comparator.comparingDouble(
                wolf -> -dispatchScore(wolf, target, targetDistance)));

        int count = Math.min(MAX_FOCUSED_RESPONDERS, available.size());
        for (int i = 0; i < count; ++i) {
            available.get(i).focusWolfStaffTarget(target, FOCUSED_TARGET_TICKS);
        }
        return count;
    }

    private static double dispatchScore(
            AbstractWolfismWolf wolf,
            LivingEntity target,
            double ownerTargetDistance) {

        WolfStaffCombatRole role = WolfStaffCombatRole.classify(wolf);
        double rolePriority;

        if (ownerTargetDistance < 5.0D) {
            rolePriority = switch (role) {
                case MELEE -> 400.0D;
                case RANGED -> 330.0D;
                case FLYING -> 235.0D;
            };
        } else if (ownerTargetDistance <= 12.0D) {
            rolePriority = switch (role) {
                case RANGED -> 380.0D;
                case MELEE -> 350.0D;
                case FLYING -> 285.0D;
            };
        } else if (ownerTargetDistance <= 20.0D) {
            rolePriority = switch (role) {
                case RANGED -> 420.0D;
                case FLYING -> 350.0D;
                case MELEE -> 250.0D;
            };
        } else {
            rolePriority = switch (role) {
                case FLYING -> 450.0D;
                case RANGED -> 385.0D;
                case MELEE -> 210.0D;
            };
        }

        // Within the preferred role, wolves already nearer the target respond first.
        double proximityBonus = Math.max(0.0D, 120.0D - wolf.distanceTo(target) * 3.0D);
        return rolePriority + proximityBonus;
    }

    private static List<AbstractWolfismWolf> ownedWolves(ServerLevel level, Player player, double radius) {
        return new ArrayList<>(level.getEntitiesOfClass(
                AbstractWolfismWolf.class,
                player.getBoundingBox().inflate(radius),
                wolf -> wolf.isAlive()
                        && wolf.isTame()
                        && wolf.isOwnedBy(player)));
    }

    private static @Nullable LivingEntity findAimedTarget(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(FOCUS_TARGET_RANGE));
        AABB searchBox = player.getBoundingBox()
                .expandTowards(look.scale(FOCUS_TARGET_RANGE))
                .inflate(4.0D);

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity candidate : level.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> isValidFocusedTarget(player, entity))) {

            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 toTarget = center.subtract(eye);
            double distance = toTarget.length();
            if (distance <= 0.001D || distance > FOCUS_TARGET_RANGE) {
                continue;
            }

            double dot = look.dot(toTarget.scale(1.0D / distance));
            if (dot < AIM_DOT_THRESHOLD || !player.hasLineOfSight(candidate)) {
                continue;
            }

            // Strongly prefer what the crosshair is closest to, then the nearest.
            double angularPenalty = (1.0D - dot) * 120.0D;
            double score = angularPenalty + distance * 0.02D;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private static boolean isValidFocusedTarget(Player owner, LivingEntity entity) {
        if (entity == owner || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }

        if (entity instanceof Player) {
            return false;
        }

        // All tamed wolves are family, including another player's vanilla pet.
        if (entity instanceof Wolf wolf && wolf.isTame()) {
            return false;
        }

        if (entity instanceof AbstractWolfismWolf wolf && wolf.isOwnedBy(owner)) {
            return false;
        }

        if (entity instanceof TamableAnimal tamable && tamable.isOwnedBy(owner)) {
            return false;
        }

        return !owner.isAlliedTo(entity);
    }

    public static WolfStaffCommandMode getMode(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        String value = data.copyTag().getStringOr(MODE_KEY, WolfStaffCommandMode.FOLLOW.serializedName());
        return WolfStaffCommandMode.byName(value);
    }

    private static void setMode(ItemStack stack, WolfStaffCommandMode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putString(MODE_KEY, mode.serializedName()));
        setRecallFlashTicks(stack, 0);
        applyModeModel(stack, mode);
    }

    private static int getRecallFlashTicks(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getIntOr(RECALL_FLASH_KEY, 0);
    }

    private static void setRecallFlashTicks(ItemStack stack, int ticks) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (ticks <= 0) {
                tag.remove(RECALL_FLASH_KEY);
            } else {
                tag.putInt(RECALL_FLASH_KEY, ticks);
            }
        });
    }

    private static void ensureModeModel(ItemStack stack) {
        Identifier expected = modelId(getMode(stack).serializedName());
        Identifier current = stack.get(DataComponents.ITEM_MODEL);
        if (!expected.equals(current)) {
            stack.set(DataComponents.ITEM_MODEL, expected);
        }
    }

    private static void applyModeModel(ItemStack stack, WolfStaffCommandMode mode) {
        stack.set(DataComponents.ITEM_MODEL, modelId(mode.serializedName()));
    }

    private static Identifier modelId(String suffix) {
        return Identifier.fromNamespaceAndPath(Wolfism.MOD_ID, "wolf_staff_" + suffix.toLowerCase(Locale.ROOT));
    }

    private static void playCommandFeedback(ServerLevel level, Player player, WolfStaffCommandMode mode) {
        float pitch = switch (mode) {
            case FOLLOW -> 1.25F;
            case SIT -> 0.85F;
            case GUARD -> 1.05F;
            case ATTACK -> 0.65F;
        };

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.8F,
                pitch);
    }
}
