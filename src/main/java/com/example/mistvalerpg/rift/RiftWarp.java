package com.example.mistvalerpg.rift;

import com.example.mistvalerpg.ModSounds;
import com.example.mistvalerpg.MistvaleRpg;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Everything around the moment of being pulled through a rift: the sounds, the burst of particles,
 * and the message to the player's own screen that makes the swirling effect play.
 */
public final class RiftWarp {
    /** The three things the server can tell your screen. */
    public static final int IN = 0;      // you are being pulled in
    public static final int OUT = 1;     // you have arrived
    public static final int CANCEL = 2;  // the rift closed on you, stop the effect

    // ---- The message the server sends to the player's game ----
    // A "payload" is a small named package of data. This one carries which variant, and which of the three phases.
    public record Warp(int variant, int phase) implements CustomPacketPayload {
        public static final Type<Warp> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "rift_warp"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Warp> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Warp::variant,
                ByteBufCodecs.VAR_INT, Warp::phase,
                Warp::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- The three moments ----

    /** The player just touched the rift. */
    public static void begin(ServerPlayer player, RiftVariant variant) {
        PacketDistributor.sendToPlayer(player, new Warp(variant.ordinal(), IN));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RiftBlockEntity.TELEPORT_DELAY + 6, 3, false, false, false)); // held in place while the pull builds
        ServerLevel level = player.serverLevel();
        level.playSound(player, player.getX(), player.getY(), player.getZ(), charge(variant), SoundSource.PLAYERS, 1.1F, pitch(variant));
        burst(level, variant, player.getX(), player.getY() + 1.0, player.getZ(), 0.6, 18);
    }

    /** The player has been moved. {@code from} is where they were standing when they left. */
    public static void arrive(ServerPlayer player, RiftVariant variant, ServerLevel from, Vec3 leftAt) {
        PacketDistributor.sendToPlayer(player, new Warp(variant.ordinal(), OUT));
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        // a flash at the spot they left behind...
        from.playSound(null, leftAt.x, leftAt.y, leftAt.z, blow(variant), SoundSource.PLAYERS, 1.0F, pitch(variant));
        burst(from, variant, leftAt.x, leftAt.y + 1.0, leftAt.z, 1.4, 60);
        // ...and a ring where they land
        ServerLevel landed = player.serverLevel();
        landed.playSound(player, player.getX(), player.getY(), player.getZ(), land(variant), SoundSource.PLAYERS, 1.0F, pitch(variant));
        ring(landed, variant, player.getX(), player.getY(), player.getZ());
    }

    /** The rift closed before it could carry the player away. */
    public static void cancel(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Warp(0, CANCEL));
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    // ---- Sounds ----

    private static SoundEvent charge(RiftVariant v) {
        return (v == RiftVariant.CORRUPTED ? ModSounds.VOID_CHARGE : ModSounds.WARP_CHARGE).get();
    }

    private static SoundEvent blow(RiftVariant v) {
        return (v == RiftVariant.CORRUPTED ? ModSounds.VOID_DEVOUR : ModSounds.WARP_BLOW).get();
    }

    private static SoundEvent land(RiftVariant v) {
        return (v == RiftVariant.CORRUPTED ? ModSounds.VOID_ARRIVE : ModSounds.WARP_ARRIVE).get();
    }

    /** Every variant sounds a little higher or lower, so you can tell them apart blindfolded. */
    public static float pitch(RiftVariant v) {
        return switch (v) {
            case WHITE -> 1.12F;
            case BLUE -> 1.0F;
            case ORANGE -> 0.82F;
            case CORRUPTED -> 1.0F;
            case END -> 0.9F;
            case DEEP -> 1.06F;
        };
    }

    // ---- Particles ----

    private static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }

    // Four particle types per variant: two "sparkly" ones and two coloured dusts.
    private static ParticleOptions[] palette(RiftVariant v) {
        return switch (v) {
            case WHITE -> new ParticleOptions[] { ParticleTypes.END_ROD, ParticleTypes.SNOWFLAKE, dust(1.0F, 1.0F, 1.0F, 1.2F), dust(0.82F, 0.88F, 0.95F, 1.0F) };
            case BLUE -> new ParticleOptions[] { ParticleTypes.END_ROD, ParticleTypes.SOUL_FIRE_FLAME, dust(0.25F, 0.6F, 1.0F, 1.2F), dust(1.0F, 1.0F, 1.0F, 1.0F) };
            case ORANGE -> new ParticleOptions[] { ParticleTypes.END_ROD, ParticleTypes.FLAME, dust(1.0F, 0.55F, 0.1F, 1.2F), dust(1.0F, 0.85F, 0.5F, 1.0F) };
            case CORRUPTED -> new ParticleOptions[] { ParticleTypes.SCULK_SOUL, ParticleTypes.ELECTRIC_SPARK, dust(0.2F, 0.02F, 0.3F, 1.4F), dust(0.1F, 0.85F, 0.8F, 0.9F) };
            case END -> new ParticleOptions[] { ParticleTypes.PORTAL, ParticleTypes.END_ROD, dust(0.65F, 0.25F, 0.95F, 1.2F), dust(0.85F, 0.7F, 1.0F, 1.0F) };
            case DEEP -> new ParticleOptions[] { ParticleTypes.END_ROD, ParticleTypes.SOUL_FIRE_FLAME, dust(0.35F, 0.95F, 1.0F, 1.2F), dust(1.0F, 0.6F, 0.85F, 1.0F) };
        };
    }

    // An explosion of the variant's particles around a point.
    private static void burst(ServerLevel level, RiftVariant v, double x, double y, double z, double spread, int count) {
        ParticleOptions[] palette = palette(v);
        for (ParticleOptions particle : palette) {
            if (particle instanceof DustParticleOptions) {
                level.sendParticles(particle, x, y, z, count / 2, spread, spread * 1.1, spread, 0.0);
            } else {
                level.sendParticles(particle, x, y, z, count / 3, spread * 0.3, spread * 0.3, spread * 0.3, v == RiftVariant.CORRUPTED ? 0.12 : 0.35);
            }
        }
    }

    // A ring of particles shooting outward along the ground where the player lands.
    private static void ring(ServerLevel level, RiftVariant v, double x, double y, double z) {
        ParticleOptions[] palette = palette(v);
        int points = 32;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            // count = 0 makes the three "offset" numbers act as a direction, and the last number as the speed
            level.sendParticles(i % 2 == 0 ? palette[0] : palette[1], x + cos * 0.3, y + 0.15, z + sin * 0.3, 0, cos, 0.04, sin,
                    v == RiftVariant.CORRUPTED ? 0.18 : 0.3);
        }
        for (int i = 0; i < 3; i++) {
            level.sendParticles(palette[2 + (i & 1)], x, y + 0.2 + i * 0.7, z, 14, 0.5, 0.35, 0.5, 0.0);
        }
        if (v == RiftVariant.CORRUPTED) {
            level.sendParticles(ParticleTypes.SMOKE, x, y + 1.0, z, 30, 0.5, 0.8, 0.5, 0.05);
        } else {
            level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 24, 0.5, 1.0, 0.5, 0.1);
        }
    }

    private RiftWarp() {
    }
}
