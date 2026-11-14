package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.soul.CagedSoulBlockEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws the Caged Soul Block: the 3D model, with its own animation, a picture for each charge,
 * the glowing parts lit up in the dark, and the soul-coloured effects on top.
 */
public class CagedSoulRenderer extends GeoBlockRenderer<CagedSoulBlockEntity> {

    public CagedSoulRenderer() {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this)); // lights up every pixel marked in the _glowmask picture
        addRenderLayer(new CagedSoulFxLayer(this));
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