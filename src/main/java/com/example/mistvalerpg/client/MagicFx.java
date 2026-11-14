package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Draws flat glowing pictures that always face the camera (called "billboards").
 * They are cut from one sheet, soul_magic.png: 8 pictures across, 3 rows down.
 */
public final class MagicFx {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "textures/effect/soul_magic.png");

    // The three rows of the sheet.
    public static final int AURA = 0;   // a soft glow
    public static final int BURST = 1;  // an expanding shockwave
    public static final int SPARK = 2;  // a little twinkle

    private static final int COLUMNS = 8;
    private static final int ROWS = 3;

    /**
     * @param row which row of the sheet (AURA, BURST or SPARK)
     * @param frame which picture in the row; it wraps around, so you can just keep counting up
     * @param halfSize half the width of the square, in blocks
     * @param alpha 0 = invisible, 1 = solid
     * @param rgb the colour to paint it in, as 0xRRGGBB (white keeps the sheet's own colours)
     */
    public static void billboard(PoseStack pose, VertexConsumer buffer, int row, int frame, float halfSize, float alpha, int rgb) {
        if (alpha <= 0.01F || halfSize <= 0.0F) {
            return;
        }
        pose.pushPose();
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation()); // turn to face the camera
        PoseStack.Pose last = pose.last();

        frame = Math.floorMod(frame, COLUMNS);
        float u0 = (float) frame / COLUMNS;
        float u1 = (float) (frame + 1) / COLUMNS;
        float v0 = (float) row / ROWS;
        float v1 = (float) (row + 1) / ROWS;
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);

        vertex(buffer, last, -halfSize, -halfSize, u0, v1, rgb, a);
        vertex(buffer, last, halfSize, -halfSize, u1, v1, rgb, a);
        vertex(buffer, last, halfSize, halfSize, u1, v0, rgb, a);
        vertex(buffer, last, -halfSize, halfSize, u0, v0, rgb, a);
        pose.popPose();
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v, int rgb, int alpha) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(FastColor.ARGB32.red(rgb), FastColor.ARGB32.green(rgb), FastColor.ARGB32.blue(rgb), alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT) // glows in the dark
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private MagicFx() {
    }
}