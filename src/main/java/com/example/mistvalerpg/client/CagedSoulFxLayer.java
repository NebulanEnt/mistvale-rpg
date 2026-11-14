package com.example.mistvalerpg.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.example.mistvalerpg.soul.CagedSoulBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The glowing effects around the cage, painted in the trapped soul's colour:
 * a halo that grows with each shard, a pulse every time a shard goes in,
 * little sparks that circle the cage, and a big shockwave when it breaks.
 */
public class CagedSoulFxLayer extends GeoRenderLayer<CagedSoulBlockEntity> {
    private static final float PULSE_TICKS = 10.0F;
    // One value for each charge: 0, 1 and 2 shards
    private static final float[] HALO_SIZE = { 0.8F, 1.05F, 1.3F };
    private static final float[] HALO_ALPHA = { 0.5F, 0.72F, 0.95F };
    private static final int[] SPARKS = { 0, 4, 6 };

    // Remembers, per cage, what we saw last frame so we can notice "the charge just went up".
    private static final Map<CagedSoulBlockEntity, Memory> MEMORY = new WeakHashMap<>();

    public CagedSoulFxLayer(GeoRenderer<CagedSoulBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack pose, CagedSoulBlockEntity cage, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        Level level = cage.getLevel();
        if (level == null) {
            return;
        }
        float time = level.getGameTime() + partialTick;
        int charge = Mth.clamp(cage.charge(), 0, 2);
        int rgb = cage.soul().color();

        Memory memory = MEMORY.computeIfAbsent(cage, c -> new Memory());
        if (memory.charge < 0) {
            memory.charge = charge;
        } else if (charge != memory.charge) {
            memory.charge = charge;
            memory.pulseStart = time; // a shard just went in
        }
        if (cage.isBreaking() && !memory.breaking) {
            memory.breaking = true;
            memory.breakStart = time;
        }
        if (!cage.isBreaking()) {
            memory.breaking = false;
        }

        VertexConsumer fx = buffers.getBuffer(RenderType.eyes(MagicFx.TEXTURE));
        pose.pushPose();
        pose.translate(0.0, 0.5, 0.0); // the middle of the block

        float sinceBreak = memory.breaking ? time - memory.breakStart : -1.0F;

        // The halo behind the cage: bigger and brighter with every shard, and flickering a little.
        float haloSize = HALO_SIZE[charge];
        float haloAlpha = HALO_ALPHA[charge];
        if (sinceBreak >= 0.0F) {
            haloSize = 1.3F + 0.05F * sinceBreak;
            haloAlpha = 0.9F * (1.0F - Mth.clamp((sinceBreak - 18.0F) / 10.0F, 0.0F, 1.0F));
        }
        haloAlpha *= 0.9F + 0.1F * Mth.sin(time * 0.25F);
        MagicFx.billboard(pose, fx, MagicFx.AURA, (int) (time / 1.5F), haloSize, haloAlpha, rgb);

        // A quick expanding ring right after a shard goes in.
        float sincePulse = time - memory.pulseStart;
        if (sincePulse >= 0.0F && sincePulse < PULSE_TICKS) {
            float t = sincePulse / PULSE_TICKS;
            MagicFx.billboard(pose, fx, MagicFx.BURST, Math.min(7, (int) (t * 8.0F)), 0.9F + 1.1F * t, 1.0F - 0.25F * t, rgb);
        }

        // Two shockwaves when the cage breaks.
        if (sinceBreak >= 4.0F) {
            float t = Mth.clamp((sinceBreak - 4.0F) / 24.0F, 0.0F, 1.0F);
            MagicFx.billboard(pose, fx, MagicFx.BURST, Math.min(7, (int) (t * 8.0F)), 1.1F + 3.2F * t, 1.0F, rgb);
            if (sinceBreak >= 10.0F) {
                float t2 = Mth.clamp((sinceBreak - 10.0F) / 18.0F, 0.0F, 1.0F);
                MagicFx.billboard(pose, fx, MagicFx.BURST, Math.min(7, (int) (t2 * 8.0F)), 0.9F + 2.4F * t2, 0.85F, rgb);
            }
        }

        // Sparks circling the cage, more of them with more charge, and flying wider when it breaks.
        int sparks = sinceBreak >= 0.0F ? 8 : SPARKS[charge];
        float radius = sinceBreak >= 0.0F ? 0.65F + 0.06F * sinceBreak : 0.62F;
        for (int i = 0; i < sparks; i++) {
            float angle = time * 0.05F + i * Mth.TWO_PI / sparks;
            float bob = 0.38F * Mth.sin(time * 0.08F + i * 1.9F);
            pose.pushPose();
            pose.translate(Mth.cos(angle) * radius, bob, Mth.sin(angle) * radius);
            MagicFx.billboard(pose, fx, MagicFx.SPARK, (int) (time * 0.45F) + i * 3, 0.14F, 0.95F, rgb);
            pose.popPose();
        }
        pose.popPose();
    }

    private static final class Memory {
        int charge = -1;
        boolean breaking;
        float pulseStart = -1000.0F;
        float breakStart = -1000.0F;
    }
}