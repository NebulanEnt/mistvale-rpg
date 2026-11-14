package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.soul.CagedSoulBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The living soul in the middle of the cage. It looks exactly like a Charged Soul Block:
 * the same moving picture, glowing at full brightness, with the soul's six runes carved on its faces.
 * It is the same little cube in every stage; the runes only get stronger with each shard.
 */
public class CagedSoulCoreLayer extends GeoRenderLayer<CagedSoulBlockEntity> {
    // The very same pictures the Charged Soul Block uses.
    private static final ResourceLocation MOVING = ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "textures/block/charged_soul_block.png");
    // How strongly the runes show at 0, 1 and 2 shards.
    private static final float[] RUNE_STRENGTH = { 0.6F, 0.85F, 1.0F };

    public CagedSoulCoreLayer(GeoRenderer<CagedSoulBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack pose, CagedSoulBlockEntity cage, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!(getRenderer() instanceof CagedSoulRenderer cageRenderer)) {
            return;
        }
        int full = LightTexture.FULL_BRIGHT;
        float strength = RUNE_STRENGTH[Mth.clamp(cage.charge(), 0, 2)];
        ResourceLocation runes = ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID,
                "textures/block/rune/core_" + cage.soul().getSerializedName() + ".png");

        try {
            // 1. The moving picture. A picture that animates needs to be told to move forward to its next frame.
            cageRenderer.setOnlyBone(CagedSoulRenderer.CORE_BONE);
            AnimatableTexture.setAndUpdate(MOVING);
            RenderType base = RenderType.entityCutoutNoCull(MOVING);
            cageRenderer.reRender(model, pose, buffers, cage, base, buffers.getBuffer(base), partialTick, full, packedOverlay, 0xFFFFFFFF);

            // 2. The six runes on top: a see-through picture on a slightly larger shell around the core.
            cageRenderer.setOnlyBone(CagedSoulRenderer.RUNES_BONE);
            RenderType carved = RenderType.entityTranslucent(runes);
            int colour = FastColor.ARGB32.color((int) (255 * strength), 255, 255, 255);
            cageRenderer.reRender(model, pose, buffers, cage, carved, buffers.getBuffer(carved), partialTick, full, packedOverlay, colour);
        } finally {
            cageRenderer.setOnlyBone(null);
        }
    }
}
