package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.spirit.SpiritEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the Spirit from her model, picture and animation files.
 */
public class SpiritRenderer extends GeoEntityRenderer<SpiritEntity> {

    public SpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        this.shadowRadius = 0.0F; // she floats, so no shadow on the ground
    }

    // No name tag floating over her head.
    @Override
    public boolean shouldShowName(SpiritEntity spirit) {
        return false;
    }

    public static class Model extends GeoModel<SpiritEntity> {
        @Override
        public ResourceLocation getModelResource(SpiritEntity spirit) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "geo/entity/spirit.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(SpiritEntity spirit) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "textures/entity/spirit.png");
        }

        @Override
        public ResourceLocation getAnimationResource(SpiritEntity spirit) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "animations/entity/spirit.animation.json");
        }
    }
}