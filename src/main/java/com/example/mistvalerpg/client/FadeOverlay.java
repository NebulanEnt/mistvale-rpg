package com.example.mistvalerpg.client;

import com.example.mistvalerpg.cutscene.CutscenePayloads;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * A flat colour laid over the whole screen that fades in, stays, and fades out again: the "cut to black"
 * of a film. The server only says when to start (see CutscenePayloads.Fade); the timing runs here.
 */
public final class FadeOverlay {
    private static long startMs = -1L;   // -1 means nothing is fading
    private static long inMs;
    private static long holdMs;
    private static long outMs;
    private static int color;

    /** Called when the message from the server arrives. A ticks value is turned into milliseconds (1 tick = 50 ms). */
    public static void onPacket(CutscenePayloads.Fade packet) {
        startMs = System.currentTimeMillis();
        inMs = packet.in() * 50L;
        holdMs = packet.hold() * 50L;
        outMs = packet.out() * 50L;
        color = packet.color() & 0xFFFFFF;
    }

    /** How dark the screen is right now, from 0 (not at all) to 1 (fully covered). */
    private static float amount(long elapsed) {
        if (elapsed < inMs) {
            return inMs == 0 ? 1.0F : (float) elapsed / inMs;
        }
        if (elapsed < inMs + holdMs) {
            return 1.0F;
        }
        return 1.0F - (float) (elapsed - inMs - holdMs) / Math.max(1L, outMs);
    }

    // Draws the colour on top of everything else on screen. Registered in MistvaleRpgClient.
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (startMs < 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed >= inMs + holdMs + outMs) {
            startMs = -1L; // finished
            return;
        }
        int alpha = Mth.clamp((int) (amount(elapsed) * 255.0F), 0, 255);
        if (alpha > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | color);
        }
    }

    private FadeOverlay() {
    }
}