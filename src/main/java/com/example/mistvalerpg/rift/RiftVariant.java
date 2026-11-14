package com.example.mistvalerpg.rift;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.ModParticles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The six kinds of rift. A variant decides how the rift looks (its texture), which particles it
 * sheds, and what happens to the player who walks in.
 */
public enum RiftVariant implements StringRepresentable {
    // name, texture, animation prefix, plays flash effects?, particle shown on a crossing player
    BLUE("blue", "", true, () -> ParticleTypes.SOUL_FIRE_FLAME),
    ORANGE("orange", "", true, () -> ParticleTypes.FLAME),
    CORRUPTED("corrupted", "c_", false, () -> ParticleTypes.SCULK_SOUL),
    WHITE("white", "", true, () -> ParticleTypes.SNOWFLAKE),
    END("end", "", true, () -> ParticleTypes.PORTAL),
    DEEP("deep", "", true, () -> ParticleTypes.END_ROD);

    private final String id;
    /** The picture wrapped around the rift's model. The Deep rift shares the blue one. */
    public final ResourceLocation texture;
    /** The Corrupted Rift has its own opening/closing animations, named "c_opening" and so on. */
    public final String animPrefix;
    public final boolean flashFx;
    private final Supplier<ParticleOptions> crossParticle;

    RiftVariant(String id, String animPrefix, boolean flashFx, Supplier<ParticleOptions> crossParticle) {
        this.id = id;
        String textureName = id.equals("deep") ? "blue" : id;
        this.texture = ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "textures/block/rift_" + textureName + ".png");
        this.animPrefix = animPrefix;
        this.flashFx = flashFx;
        this.crossParticle = crossParticle;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** The animation name for this variant, for example "opening" becomes "c_opening" for the Corrupted Rift. */
    public String anim(String name) {
        return animPrefix + name;
    }

    public ParticleOptions crossParticle() {
        return crossParticle.get();
    }

    /** The particle that drifts out of an open rift of this kind. */
    public ParticleOptions idleParticle(RandomSource random) {
        return switch (this) {
            case BLUE -> ModParticles.RIFT_SOUL.get();
            case ORANGE -> random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME;
            case CORRUPTED -> ParticleTypes.SCULK_SOUL;
            case END -> random.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.PORTAL;
            case DEEP -> random.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME;
            case WHITE -> random.nextInt(4) == 0 ? ParticleTypes.END_ROD : ParticleTypes.SNOWFLAKE;
        };
    }

    public static RiftVariant byName(String name) {
        for (RiftVariant variant : values()) {
            if (variant.id.equals(name)) {
                return variant;
            }
        }
        return BLUE;
    }

    /**
     * What happens once a player has been pulled through this rift.
     * Blue takes you home and Corrupted takes you to its destination.
     * The other four belong to later parts of the story, so for now they also take you home.
     */
    public void onEnter(ServerPlayer player, RiftBlockEntity rift) {
        if (this == CORRUPTED) {
            enterCorrupted(player, rift.getDestination());
        } else {
            goHome(player);
        }
    }

    private static void enterCorrupted(ServerPlayer player, @Nullable GlobalPos destination) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (destination == null) {
            // Nobody has told this rift where to lead. Drop the player at the world spawn.
            player.sendSystemMessage(Component.translatable("message.mistvale_rpg.rift.leads_nowhere")
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            teleport(player, overworld.dimension(), Vec3.atBottomCenterOf(groundAt(overworld, spawn)), player.getYRot());
            overworld.playSound(null, spawn, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.6F, 0.5F);
            return;
        }
        ServerLevel target = server.getLevel(destination.dimension());
        if (target != null) {
            target.getChunk(destination.pos().getX() >> 4, destination.pos().getZ() >> 4); // make sure the chunk is loaded
            teleport(player, destination.dimension(), Vec3.atBottomCenterOf(destination.pos()), player.getYRot());
            target.playSound(null, destination.pos(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.6F, 0.5F);
        }
    }

    /** Sends the player to their bed (or world spawn if they have none). */
    private static void goHome(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        BlockPos bed = player.getRespawnPosition();
        ServerLevel bedLevel = bed == null ? null : server.getLevel(player.getRespawnDimension());
        if (bedLevel != null) {
            BlockState bedState = bedLevel.getBlockState(bed);
            Vec3 standAt = bedState.getBlock() instanceof BedBlock
                    ? BedBlock.findStandUpPosition(player.getType(), bedLevel, bed, bedState.getValue(BedBlock.FACING), player.getRespawnAngle())
                    .orElse(Vec3.atBottomCenterOf(bed.above()))
                    : Vec3.atBottomCenterOf(bed.above());
            teleport(player, bedLevel.dimension(), standAt, player.getRespawnAngle());
        } else {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            teleport(player, overworld.dimension(), Vec3.atBottomCenterOf(groundAt(overworld, spawn)), player.getYRot());
        }
    }

    /** The first solid ground at or below a position (so nobody lands inside a mountain). */
    public static BlockPos groundAt(ServerLevel level, BlockPos pos) {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
        if (top.getY() <= level.getMinBuildHeight() + 1) {
            return pos.getY() > level.getMinBuildHeight() + 1 ? pos : new BlockPos(pos.getX(), level.getSeaLevel(), pos.getZ());
        }
        return top;
    }

    // Moves the player and cancels any fall, so a long drop on the other side does not hurt.
    private static void teleport(ServerPlayer player, ResourceKey<Level> dimension, Vec3 where, float yaw) {
        ServerLevel target = player.getServer().getLevel(dimension);
        if (target != null) {
            player.teleportTo(target, where.x, where.y, where.z, yaw, player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
        }
    }
}