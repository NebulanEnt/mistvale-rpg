package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.prologue.BrannochEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Draws Brannoch from his Blockbench model (geo/entity/brannoch.geo.json), his picture (textures/entity/brannoch.png) and his
 * animations (animations/entity/brannoch.animation.json), and puts the pirate saber in his right hand.
 */
public class BrannochRenderer extends GeoEntityRenderer<BrannochEntity> {

    public BrannochRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        this.shadowRadius = 0.5F;
        addRenderLayer(new SaberLayer(this));
    }

    /**
     * Where he looks is worked out here, on the screen of each player, from where that player stands. The game itself does not turn him
     * (that was the cause of the head and body spinning): his body turns slowly after you, his head follows faster, up to 50 degrees
     * away from the body, and nothing about it depends on the angles the server keeps. While he looks around at the mist he holds still.
     */
    private static final class Facing {
        float body;          // yaw of his body, in degrees, like an entity's yaw
        float head;          // how far his head is turned away from the body (degrees)
        float pitch;         // how far his head is tipped up or down (degrees, negative = up)
        long lastNanos;
        boolean started;
    }

    private static final Map<Integer, Facing> FACING = new HashMap<>();

    private static Facing facing(BrannochEntity entity) {
        return FACING.computeIfAbsent(entity.getId(), id -> new Facing());
    }

    private static void updateFacing(BrannochEntity entity, float partialTick) {
        Facing f = facing(entity);
        long now = System.nanoTime();
        float dt = f.started ? Math.min((now - f.lastNanos) / 1.0e9F, 0.1F) : 0.0F;
        f.lastNanos = now;
        if (!f.started) {
            f.started = true;
            f.body = entity.getYRot();
        }
        if (FACING.size() > 64) {
            FACING.keySet().removeIf(id -> id != entity.getId());
            FACING.put(entity.getId(), f);
        }
        if (entity.isLookingAround()) {
            f.head = 0.0F;
            f.pitch = 0.0F;
            return;
        }
        var player = Minecraft.getInstance().player;
        Vec3 here = entity.getPosition(partialTick);
        if (player == null || player.distanceToSqr(here) > 24.0 * 24.0) {
            f.head = Mth.approach(f.head, 0.0F, 120.0F * dt);
            f.pitch = Mth.approach(f.pitch, 0.0F, 60.0F * dt);
            return;
        }
        Vec3 eyes = player.getEyePosition(partialTick);
        float target = (float) (Math.toDegrees(Math.atan2(eyes.z - here.z, eyes.x - here.x)) - 90.0);
        float rel = Mth.wrapDegrees(target - f.body);
        if (Math.abs(rel) > 8.0F) {
            float step = (Math.abs(rel) > 45.0F ? 240.0F : 60.0F) * dt;
            f.body += Mth.clamp(rel, -step, step);
        }
        f.body = Mth.wrapDegrees(f.body);
        float headWanted = Mth.clamp(Mth.wrapDegrees(target - f.body), -50.0F, 50.0F);
        f.head += Mth.clamp(headWanted - f.head, -360.0F * dt, 360.0F * dt);
        double along = Math.hypot(eyes.x - here.x, eyes.z - here.z);
        float pitchWanted = Mth.clamp((float) -Math.toDegrees(Math.atan2(eyes.y - (here.y + entity.getEyeHeight()), Math.max(along, 0.01))), -30.0F, 30.0F);
        f.pitch += Mth.clamp(pitchWanted - f.pitch, -180.0F * dt, 180.0F * dt);
    }

    @Override
    public void render(BrannochEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        updateFacing(entity, partialTick);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    // The body is turned by the yaw worked out above, not by the one of the entity.
    @Override
    protected void applyRotations(BrannochEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(entity, poseStack, ageInTicks, facing(entity).body, partialTick, nativeScale);
    }

    // No name floats over his head.
    @Override
    public boolean shouldShowName(BrannochEntity entity) {
        return false;
    }

    public static class Model extends GeoModel<BrannochEntity> {
        @Override
        public ResourceLocation getModelResource(BrannochEntity entity) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "geo/entity/brannoch.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(BrannochEntity entity) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "textures/entity/brannoch.png");
        }

        @Override
        public ResourceLocation getAnimationResource(BrannochEntity entity) {
            return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "animations/entity/brannoch.animation.json");
        }

        // His head follows where the entity looks (up, down, left, right), except while his own "look around" animation plays.
        @Override
        public void setCustomAnimations(BrannochEntity entity, long instanceId, AnimationState<BrannochEntity> state) {
            super.setCustomAnimations(entity, instanceId, state);
            if (entity.isLookingAround()) {
                return;
            }
            getBone("head").ifPresent(head -> {
                Facing f = facing(entity);
                head.setRotX(f.pitch * Mth.DEG_TO_RAD);
                head.setRotY(f.head * Mth.DEG_TO_RAD);
                head.setRotZ(0.0F);
            });
        }
    }

    /**
     * Draws whatever is in his main hand (the saber of Bosses'Rise) at the bone "right_hand_item" of the model. That bone sits at
     * the shoulder of the right arm, and the item is placed from there exactly like the game places a sword in a player's hand.
     */
    private static class SaberLayer extends BlockAndItemGeoLayer<BrannochEntity> {
        SaberLayer(GeoRenderer<BrannochEntity> renderer) {
            super(renderer);
        }

        @Override
        protected ItemStack getStackForBone(GeoBone bone, BrannochEntity entity) {
            return "right_hand_item".equals(bone.getName()) ? entity.getMainHandItem() : null;
        }

        @Override
        protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, BrannochEntity entity) {
            return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }

        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, BrannochEntity entity,
                MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            poseStack.pushPose();
            // WHERE THE SABER SITS. Every line is applied to the saber, in this order.
            // 1-4 are the game's own "item in hand" placement (what a skeleton or a player does), turned into GeckoLib's upright space.
            //     They put the saber's grip into his fist: the point is 10 pixels below the shoulder, the pivot of the right_arm bone.
            //     Do not change these.
            // 5-7 are our own turns, in degrees, round the grip:
            //     the first (x) swings the blade forward and back (0 = pointing straight ahead, -90 = hanging down, 90 = pointing up, 180 = back),
            //     the second (y) turns the saber round its own length (the flat of the blade faces forward, sideways or back),
            //     the third (z) swings it sideways (out from his leg, or in).
            poseStack.scale(-1.0F, -1.0F, 1.0F);                                    // 1
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));                     // 2
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));                     // 3
            poseStack.translate(1.0F / 16.0F, 0.125F, -0.625F);                     // 4
            poseStack.mulPose(Axis.XP.rotationDegrees(0.0F));                      // 5
            poseStack.mulPose(Axis.YP.rotationDegrees(0.0F));                     // 6
            poseStack.mulPose(Axis.ZP.rotationDegrees(0.0F));                       // 7
            super.renderStackForBone(poseStack, bone, stack, entity, bufferSource, partialTick, packedLight, packedOverlay);
            poseStack.popPose();
        }
    }
}
