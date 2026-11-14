package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.rift.RiftBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws a rift. The model is a crowd of small boxes ("shell" boxes end in _g1, _g2 or _g3).
 * We draw it twice: once normally, and once again as a glow where the shells are left out
 * and everything else is lit up at full brightness.
 */
public class RiftRenderer extends GeoBlockRenderer<RiftBlockEntity> {
    private static final int GLOW_COLOUR = 0x70FFFFFF; // white, about 44% opaque
    private boolean glowPass = false;

    public RiftRenderer() {
        super(new Model());
        addRenderLayer(new GlowLayer(this));
    }

    // Everything the rift looks like is cut-out shapes, so use the "cutout" style of drawing.
    @Override
    public RenderType getRenderType(RiftBlockEntity rift, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void render(RiftBlockEntity rift, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        RiftCrossing.track(rift);
        super.render(rift, partialTick, pose, buffers, LightTexture.FULL_BRIGHT, packedOverlay); // a rift is never in shadow
    }

    // GeckoLib calls this once for every bone of the model, which is where we hide or tint the shell boxes.
    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        boolean shell = isShell(bone.getName());
        if (glowPass) {
            super.renderCubesOfBone(pose, bone, buffer, packedLight, packedOverlay, shell ? -1 : GLOW_COLOUR);
        } else if (!shell) {
            super.renderCubesOfBone(pose, bone, buffer, packedLight, packedOverlay, colour);
        }
    }

    // A shell bone is named something like "Box_A_g2": ends with "_g" and one digit.
    private static boolean isShell(String name) {
        int n = name.length();
        return n > 3 && name.charAt(n - 3) == '_' && name.charAt(n - 2) == 'g' && Character.isDigit(name.charAt(n - 1));
    }

    @Override
    public AABB getRenderBoundingBox(RiftBlockEntity rift) {
        return new AABB(rift.getBlockPos()).inflate(4.0, 3.5, 4.0);
    }

    /** The second, glowing drawing of the rift. */
    public static class GlowLayer extends GeoRenderLayer<RiftBlockEntity> {
        private final RiftRenderer owner;

        public GlowLayer(RiftRenderer renderer) {
            super(renderer);
            this.owner = renderer;
        }

        @Override
        public void render(PoseStack pose, RiftBlockEntity rift, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            RenderType glow = RenderType.eyes(getTextureResource(rift)); // "eyes" glows like a spider's eyes
            owner.glowPass = true;
            try {
                owner.reRender(model, pose, buffers, rift, glow, buffers.getBuffer(glow), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, -1);
            } finally {
                owner.glowPass = false;
            }
        }
    }

    public static class Model extends GeoModel<RiftBlockEntity> {
        @Override
        public ResourceLocation getModelResource(RiftBlockEntity rift) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "geo/block/rift.geo.json");
        }

        // Each variant has its own picture: blue, orange, corrupted, white, end.
        @Override
        public ResourceLocation getTextureResource(RiftBlockEntity rift) {
            return rift.getVariant().texture;
        }

        @Override
        public ResourceLocation getAnimationResource(RiftBlockEntity rift) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "animations/block/rift.animation.json");
        }
    }
}