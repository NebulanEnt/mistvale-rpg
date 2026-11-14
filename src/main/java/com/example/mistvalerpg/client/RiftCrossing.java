package com.example.mistvalerpg.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.rift.RiftBlock;
import com.example.mistvalerpg.rift.RiftBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * What you see of a player while they pass through a rift: their body gets squashed thin
 * as it enters the sheet, vanishes in the middle, and trails sparkles of the rift's colour.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class RiftCrossing {
    private static final double FADE_DISTANCE = 0.9;  // squashing starts this close to the sheet
    private static final double HIDE_DISTANCE = 0.12; // inside this, the player is not drawn at all

    // Every rift the renderer has drawn recently. Filled in by RiftRenderer.
    private static final Set<RiftBlockEntity> RIFTS = ConcurrentHashMap.newKeySet();

    public static void track(RiftBlockEntity rift) {
        RIFTS.add(rift);
    }

    /** A player standing in an open rift's sheet: which rift, how deep ("closeness" 0..1), and where. */
    private record Crossing(Direction facing, Vec3 riftBase, double depth, double closeness) {
    }

    // Works out whether a player is inside the sheet of any open rift.
    @Nullable
    private static Crossing find(Player player, float partialTick) {
        Vec3 feet = new Vec3(
                Mth.lerp(partialTick, player.xOld, player.getX()),
                Mth.lerp(partialTick, player.yOld, player.getY()),
                Mth.lerp(partialTick, player.zOld, player.getZ()));
        RIFTS.removeIf(rift -> rift.isRemoved());

        for (RiftBlockEntity rift : RIFTS) {
            if (!rift.isPassable()) {
                continue;
            }
            Direction facing = rift.getBlockState().getValue(RiftBlock.FACING);
            Vec3 base = Vec3.atBottomCenterOf(rift.getBlockPos());
            Vec3 offset = feet.subtract(base);
            double depth = facing.getAxis() == Direction.Axis.Z ? offset.z : offset.x;
            double sideways = facing.getAxis() == Direction.Axis.Z ? offset.x : offset.z;
            if (RiftBlockEntity.insideSheet(sideways, offset.y) && Math.abs(depth) <= FADE_DISTANCE) {
                return new Crossing(facing, base, depth, 1.0 - Math.abs(depth) / FADE_DISTANCE);
            }
        }
        return null;
    }

    // Just before a player is drawn: squash them flat along the rift's direction, or hide them completely.
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        Crossing crossing = find(player, event.getPartialTick());
        if (crossing == null) {
            return;
        }
        if (Math.abs(crossing.depth()) < HIDE_DISTANCE) {
            event.setCanceled(true);
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 playerPos = new Vec3(
                Mth.lerp(event.getPartialTick(), player.xOld, player.getX()),
                Mth.lerp(event.getPartialTick(), player.yOld, player.getY()),
                Mth.lerp(event.getPartialTick(), player.zOld, player.getZ()));
        Vec3 toRift = crossing.riftBase().subtract(playerPos);
        float squash = (float) Mth.clamp(1.0 - 0.85 * crossing.closeness() * crossing.closeness(), 0.1, 1.0);

        // scale around the rift's own position instead of the player's: move there, scale, move back
        pose.translate(toRift.x, toRift.y, toRift.z);
        if (crossing.facing().getAxis() == Direction.Axis.Z) {
            pose.scale(1.0F, 1.0F, squash);
        } else {
            pose.scale(squash, 1.0F, 1.0F);
        }
        pose.translate(-toRift.x, -toRift.y, -toRift.z);
    }

    // Every tick: a few sparkles around anyone deep in a rift.
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        for (Player player : minecraft.level.players()) {
            Crossing crossing = find(player, 1.0F);
            if (crossing == null || crossing.closeness() < 0.35) {
                continue;
            }
            for (RiftBlockEntity rift : RIFTS) {
                if (rift.getBlockPos().equals(net.minecraft.core.BlockPos.containing(crossing.riftBase()))) {
                    ParticleOptions particle = rift.getVariant().crossParticle();
                    for (int i = 0; i < 3; i++) {
                        minecraft.level.addParticle(particle, player.getRandomX(0.5), player.getRandomY(), player.getRandomZ(0.5), 0.0, 0.02, 0.0);
                    }
                    break;
                }
            }
        }
    }

    private RiftCrossing() {
    }
}