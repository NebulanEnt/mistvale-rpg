package com.example.mistvalerpg.soul;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.rift.RiftBlock;
import com.example.mistvalerpg.rift.RiftVariant;
import com.example.mistvalerpg.spirit.SpiritEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The ritual. Nine Charged Soul Blocks, all holding different souls, laid as a flat 3x3 square
 * wake up and, after a few seconds of building power, tear open a Corrupted Rift above the middle block.
 *
 * Nothing is stored in the world for this: whenever a charged block is placed we simply look
 * around it to see if a finished square exists.
 */
public final class SoulPlatform {
    /** The ritual takes 20 steps, one every 4 ticks: 4 seconds in total. */
    public static final int STEP_TICKS = 4;
    public static final int STEPS = 20;
    /** The rift closes by itself after 6000 ticks (5 minutes). */
    public static final int RIFT_LIFETIME = 6000;

    // Which platforms are mid-ritual right now, and which step they are on. Kept in memory only.
    private static final Map<GlobalPos, Integer> RITUALS = new ConcurrentHashMap<>();

    /** Where the Corrupted Rift leads. Nothing sets it yet: until the champion's arena exists it stays empty. */
    @Nullable
    public static volatile GlobalPos championDestination = null;

    /** Looks around a freshly placed block for the middle of a finished platform. Returns null if there is none. */
    @Nullable
    public static BlockPos find(ServerLevel level, BlockPos placed) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos center = placed.offset(dx, 0, dz);
                if (isComplete(level, center)) {
                    return center;
                }
            }
        }
        return null;
    }

    /** True if the 3x3 square around this block is nine charged blocks with nine different souls. */
    public static boolean isComplete(ServerLevel level, BlockPos center) {
        EnumSet<SoulId> seen = EnumSet.noneOf(SoulId.class);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState state = level.getBlockState(center.offset(dx, 0, dz));
                if (!state.is(MistvaleRpg.CHARGED_SOUL_BLOCK.get())) {
                    return false;
                }
                if (!seen.add(state.getValue(SoulId.PROPERTY))) {
                    return false; // the same soul twice
                }
            }
        }
        return true;
    }

    public static void onChargedPlaced(ServerLevel level, BlockPos placed) {
        BlockPos center = find(level, placed);
        if (center != null) {
            startRitual(level, center);
        }
    }

    public static boolean startRitual(ServerLevel level, BlockPos center) {
        GlobalPos key = GlobalPos.of(level.dimension(), center);
        if (RITUALS.containsKey(key)) {
            return false;
        }
        BlockPos above = center.above();
        if (level.getBlockState(above).getBlock() instanceof RiftBlock) {
            tell(level, center, "message.mistvale_rpg.soul_platform.rift_open");
            return false;
        }
        if (!level.getBlockState(above).canBeReplaced()) {
            tell(level, center, "message.mistvale_rpg.soul_platform.no_room");
            return false;
        }
        RITUALS.put(key, 0);
        level.playSound(null, center, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 0.8F, 0.5F);
        // "Wake me up in 4 ticks": the middle block's tick() will call ritualTick below.
        level.scheduleTick(center, MistvaleRpg.CHARGED_SOUL_BLOCK.get(), STEP_TICKS);
        return true;
    }

    /** One step of the ritual. Called by the middle block every STEP_TICKS ticks. */
    public static void ritualTick(ServerLevel level, BlockPos center) {
        GlobalPos key = GlobalPos.of(level.dimension(), center);
        Integer step = RITUALS.get(key);
        if (step == null) {
            return;
        }
        if (!isComplete(level, center)) {
            // Somebody broke a block: the power drains away.
            RITUALS.remove(key);
            level.playSound(null, center, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
            tell(level, center, "message.mistvale_rpg.soul_platform.fizzle");
            return;
        }

        int next = step + 1;
        float progress = (float) next / STEPS;
        Vec3 focus = Vec3.atCenterOf(center).add(0.0, 1.6, 0.0); // the point above the middle where the rift will open

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                SoulId soul = level.getBlockState(pos).getValue(SoulId.PROPERTY);
                Vec3 rgb = Vec3.fromRGB24(soul.color());
                Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
                // a puff of the soul's own colour...
                level.sendParticles(new DustParticleOptions(rgb.toVector3f(), 1.2F), top.x, top.y, top.z, 3, 0.25, 0.05, 0.25, 0.0);
                // ...and a spark that flies toward the focus, faster as the ritual builds
                Vec3 pull = focus.subtract(top).scale(0.07 + 0.05 * progress);
                level.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 0, pull.x, pull.y, pull.z, 1.0);
            }
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, focus.x, focus.y, focus.z, 2 + (int) (6 * progress), 0.35, 0.5, 0.35, 0.01);
        level.playSound(null, center, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.9F, 0.5F + progress);
        if (next == STEPS / 2) {
            level.playSound(null, center, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 1.5F, 0.7F);
        }

        if (next >= STEPS) {
            RITUALS.remove(key);
            openRift(level, center);
        } else {
            RITUALS.put(key, next);
            level.scheduleTick(center, MistvaleRpg.CHARGED_SOUL_BLOCK.get(), STEP_TICKS);
        }
    }

    private static void openRift(ServerLevel level, BlockPos center) {
        // The rift faces whoever is standing closest, so they can walk straight into it.
        Player nearest = level.getNearestPlayer(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 32.0, false);
        Direction facing = nearest == null
                ? Direction.NORTH
                : Direction.getNearest(nearest.getX() - (center.getX() + 0.5), 0.0, nearest.getZ() - (center.getZ() + 0.5));
        if (facing.getAxis() == Direction.Axis.Y) {
            facing = Direction.NORTH;
        }

        RiftBlock.spawn(level, center.above(), facing, RiftVariant.CORRUPTED, championDestination, RIFT_LIFETIME, true);
        level.playSound(null, center, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.7F, 0.6F);
        level.playSound(null, center, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 1.2F, 0.4F);
        level.sendParticles(ParticleTypes.SONIC_BOOM, center.getX() + 0.5, center.getY() + 1.8, center.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);

        // The Spirit comes to witness it, close to the rift and out of the way.
        SpiritEntity.appearNear(level, center.relative(facing, 3).above());
    }

    // A short line above the hotbar for everyone standing near the platform.
    private static void tell(ServerLevel level, BlockPos center, String translationKey) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(center).inflate(12.0))) {
            player.displayClientMessage(Component.translatable(translationKey), true);
        }
    }

    private SoulPlatform() {
    }
}