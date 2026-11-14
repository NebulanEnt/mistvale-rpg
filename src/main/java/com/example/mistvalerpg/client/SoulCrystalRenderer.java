package com.example.mistvalerpg.client;

import org.joml.Quaternionf;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.soul.SoulCrystalEntity;
import com.example.mistvalerpg.soul.SoulId;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the Soul Crystal the way the end crystal is drawn, with the same sizes and the same turning:
 * three cubes inside each other, each one tilted and spun against the last.
 * The two outer cubes are see-through ghosts of the soul block; the inner one is the real, animated block,
 * runes and all. Around it comes the cage's own soul magic: the swirling halo, an
 * expanding ring now and then, and little twinkles.
 */
public class SoulCrystalRenderer extends EntityRenderer<SoulCrystalEntity> {
    // The end crystal's own numbers (see EndCrystalRenderer): a cube of 1 block, then one 0.875 times as big inside it, then another 0.875 times inside that.
    private static final float INNER_SCALE = 0.875F;
    private static final float SIN_QUARTER_PI = (float) Math.sin(Math.PI / 4.0);
    private static final float TILT = (float) Math.toRadians(60.0);
    private static final float DEGREES_PER_TICK = 3.0F;
    private static final float GHOST_ALPHA = 0.28F;

    public SoulCrystalRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
    }

    @Override
    public ResourceLocation getTextureLocation(SoulCrystalEntity crystal) {
        return MagicFx.TEXTURE; // not used: the cubes come from the block's own model
    }

    /** How high the middle of the crystal floats above the bottom of its cell: the end crystal's own bobbing, scaled down to fit a cell. */
    private static float centreHeight(float time) {
        float f = Mth.sin(time * 0.2F) / 2.0F + 0.5F;
        f = (f * f + f) * 0.4F; // 0 to 0.8, the end crystal's curve
        return 0.58F + f * 0.35F;
    }

    @Override
    public void render(SoulCrystalEntity crystal, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float time = crystal.tickCount + partialTick;
        float spin = time * DEGREES_PER_TICK;
        float height = centreHeight(time);
        SoulId soul = crystal.soul();

        BlockState state = MistvaleRpg.CHARGED_SOUL_BLOCK.get().defaultBlockState().setValue(SoulId.PROPERTY, soul);
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        // The real block is drawn SOLID, with the paint the game uses for solid things: it is drawn first and it writes
        // depth, so the magic light behind it stays hidden. The two ghost cubes use our own see-through paint that never
        // writes depth (see GhostRenderType), so a ghost face can never cut the solid block off. The ghosts get an extra fade.
        MultiBufferSource solid = type -> buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        MultiBufferSource ghost = type -> new AlphaConsumer(buffers.getBuffer(GhostRenderType.CUBES), GHOST_ALPHA);
        Quaternionf tilt = new Quaternionf().setAngleAxis(TILT, SIN_QUARTER_PI, 0.0F, SIN_QUARTER_PI);

        pose.pushPose();
        pose.translate(0.0, height, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(spin));

        pose.mulPose(tilt);
        cube(blocks, state, pose, ghost);          // outer ghost, 1 block

        pose.scale(INNER_SCALE, INNER_SCALE, INNER_SCALE);
        pose.mulPose(tilt);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        cube(blocks, state, pose, ghost);          // inner ghost, 0.875

        pose.scale(INNER_SCALE, INNER_SCALE, INNER_SCALE);
        pose.mulPose(tilt);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        cube(blocks, state, pose, solid);          // the soul block itself, 0.766
        pose.popPose();

        // The cage's own magic, in the soul's colour, around the middle of the crystal.
        pose.pushPose();
        pose.translate(0.0, height, 0.0);
        magic(pose, buffers.getBuffer(RenderType.eyes(MagicFx.TEXTURE)), time, soul.color());
        pose.popPose();

        super.render(crystal, yaw, partialTick, pose, buffers, packedLight);
    }

    // One cube centred on the current point: the block model fills 0..1, so it is moved back by half.
    private static void cube(BlockRenderDispatcher blocks, BlockState state, PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    // The same three effects as the cage (halo, ring, twinkles), made to fit a one-block crystal.
    private static void magic(PoseStack pose, VertexConsumer fx, float time, int rgb) {
        float flicker = 0.9F + 0.1F * Mth.sin(time * 0.25F);
        MagicFx.billboard(pose, fx, MagicFx.AURA, (int) (time / 1.5F), 0.62F, flicker, rgb);

        float ring = (time % 70.0F) / 70.0F; // a ring leaves the crystal every 3.5 seconds
        MagicFx.billboard(pose, fx, MagicFx.BURST, Math.min(7, (int) (ring * 8.0F)), 0.4F + 0.5F * ring, 1.0F - 0.7F * ring, rgb);

        int sparks = 5;
        for (int i = 0; i < sparks; i++) {
            float angle = time * 0.05F + i * Mth.TWO_PI / sparks;
            float bob = 0.2F * Mth.sin(time * 0.08F + i * 1.9F);
            pose.pushPose();
            pose.translate(Mth.cos(angle) * 0.52F, bob, Mth.sin(angle) * 0.52F);
            MagicFx.billboard(pose, fx, MagicFx.SPARK, (int) (time * 0.45F) + i * 3, 0.1F, 0.95F, rgb);
            pose.popPose();
        }
    }
}
