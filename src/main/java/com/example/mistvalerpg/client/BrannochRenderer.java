package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.prologue.BrannochEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws Brannoch. He has the body of a player (the slim kind, with thin arms) plus the captain's extras of {@link BrannochModel},
 * and his own picture of 128 x 128 pixels wrapped around all of it.
 */
public class BrannochRenderer extends HumanoidMobRenderer<BrannochEntity, PlayerModel<BrannochEntity>> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "textures/entity/brannoch.png");

    public BrannochRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(BrannochModel.LAYER), true), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(BrannochEntity entity) {
        return SKIN;
    }

    // No name floats over his head.
    @Override
    protected boolean shouldShowName(BrannochEntity entity) {
        return false;
    }
}
