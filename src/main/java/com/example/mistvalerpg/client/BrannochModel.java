package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The shape of Brannoch: a player's body with a captain's extras added to it: a big three-sided hat, the tails of a long coat
 * and a hook instead of his left hand. Every extra is a box ("cube") glued to a part of the body, so it moves with that part.
 * The picture that colours all of it is 128 x 128 pixels: the player's own 64 x 64 layout in the top left corner, and the
 * extras in the free space around it.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class BrannochModel {
    /** The name under which the game stores this shape. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "brannoch"), "main");

    private BrannochModel() {
    }

    // Tells the game about the shape while it is getting ready (before any renderer asks for it).
    @SubscribeEvent
    static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, BrannochModel::createLayer);
    }

    public static LayerDefinition createLayer() {
        // Start from the slim player (thin arms) and add the extras to it.
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, true);
        PartDefinition root = mesh.getRoot();

        // The hat is glued to the head. The head's top is at y = -8, so everything of the hat is above that.
        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("hat_brim", CubeListBuilder.create().texOffs(40, 64).addBox(-8.0F, -9.0F, -8.0F, 16, 1, 16), PartPose.ZERO);
        head.addOrReplaceChild("hat_crown", CubeListBuilder.create().texOffs(0, 88).addBox(-4.5F, -13.0F, -4.5F, 9, 4, 9), PartPose.ZERO);
        head.addOrReplaceChild("hat_left", CubeListBuilder.create().texOffs(40, 82).addBox(-8.0F, -12.0F, -6.0F, 1, 3, 12), PartPose.ZERO);
        head.addOrReplaceChild("hat_right", CubeListBuilder.create().texOffs(66, 82).addBox(7.0F, -12.0F, -6.0F, 1, 3, 12), PartPose.ZERO);
        head.addOrReplaceChild("hat_front", CubeListBuilder.create().texOffs(40, 97).addBox(-6.0F, -12.0F, -8.0F, 12, 3, 1), PartPose.ZERO);

        // The coat tails hang from the body: one behind and two in front with a gap between them. The legs are at y = 12 and below.
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("coat_back", CubeListBuilder.create().texOffs(0, 64).addBox(-4.5F, 11.0F, 2.0F, 9, 10, 1), PartPose.ZERO);
        body.addOrReplaceChild("coat_front_left", CubeListBuilder.create().texOffs(0, 76).addBox(0.5F, 11.0F, -3.0F, 4, 9, 1), PartPose.ZERO);
        body.addOrReplaceChild("coat_front_right", CubeListBuilder.create().texOffs(20, 76).addBox(-4.5F, 11.0F, -3.0F, 4, 9, 1), PartPose.ZERO);

        // The hook is glued to the left arm, below the cuff: a stem, a bend that points forward, and the tip.
        PartDefinition leftArm = root.getChild("left_arm");
        leftArm.addOrReplaceChild("hook_stem", CubeListBuilder.create().texOffs(0, 104).addBox(0.0F, 10.0F, -0.5F, 1, 4, 1), PartPose.ZERO);
        leftArm.addOrReplaceChild("hook_bend", CubeListBuilder.create().texOffs(10, 104).addBox(0.0F, 13.0F, -2.5F, 1, 1, 2), PartPose.ZERO);
        leftArm.addOrReplaceChild("hook_tip", CubeListBuilder.create().texOffs(20, 104).addBox(0.0F, 11.0F, -2.5F, 1, 2, 1), PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }
}
