package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.soul.CagedSoulBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws the Caged Soul Block: the 3D model, with its own animation, a picture for each charge,
 * the glowing parts lit up in the dark, the living soul inside (with its runes), and the soul-coloured effects on top.
 */
public class CagedSoulRenderer extends GeoBlockRenderer<CagedSoulBlockEntity> {
    /** The little cube in the middle of the cage, and the thin shell around it that carries the runes; see caged_soul.geo.json. */
    static final String CORE_BONE = "SoulCore";
    static final String RUNES_BONE = "SoulRunes";

    // When this is null we draw everything EXCEPT the two core bones. When it holds a bone's name we draw ONLY that bone.
    // The core needs its own pictures (the animated Charged Soul Block texture, then the runes on top),
    // and one picture can't be shared with the rest of the cage, so CagedSoulCoreLayer draws it in extra passes.
    private String onlyBone = null;

    public CagedSoulRenderer() {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this)); // lights up every pixel marked in the _glowmask picture
        addRenderLayer(new CagedSoulCoreLayer(this));
        addRenderLayer(new CagedSoulFxLayer(this));
    }

    void setOnlyBone(String onlyBone) {
        this.onlyBone = onlyBone;
    }

    // GeckoLib calls this for every bone it draws. We simply skip the bones that belong to the other pass.
    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        String name = bone.getName();
        boolean wanted = onlyBone == null ? !(CORE_BONE.equals(name) || RUNES_BONE.equals(name)) : onlyBone.equals(name);
        if (!wanted) {
            return;
        }
        super.renderCubesOfBone(pose, bone, buffer, packedLight, packedOverlay, colour);
    }

    // The effects reach beyond the block, so Minecraft must not stop drawing us when the block itself is off-screen.
    @Override
    public AABB getRenderBoundingBox(CagedSoulBlockEntity cage) {
        return new AABB(cage.getBlockPos()).inflate(4.5, 4.0, 4.5);
    }

    /** Tells GeckoLib which three files make up this model. */
    public static class Model extends GeoModel<CagedSoulBlockEntity> {
        @Override
        public ResourceLocation getModelResource(CagedSoulBlockEntity cage) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "geo/block/caged_soul.geo.json");
        }

        // A different picture for each charge: stage 0, 1 and 2 of the cage.
        @Override
        public ResourceLocation getTextureResource(CagedSoulBlockEntity cage) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "textures/block/caged_soul_model_" + cage.charge() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(CagedSoulBlockEntity cage) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "animations/block/caged_soul.animation.json");
        }
    }
}
