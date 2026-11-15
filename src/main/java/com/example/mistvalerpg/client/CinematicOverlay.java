package com.example.mistvalerpg.client;

import java.util.List;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.cutscene.CutscenePayloads;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The two things that make a scene look like a film: black bars at the top and bottom of the screen,
 * and a line of text in the middle. The server only says when to start (see CutscenePayloads).
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class CinematicOverlay {
    private static final long SLIDE_MS = 700L;        // how long the bars take to slide in or out
    private static final float BAR_HEIGHT = 0.125F;   // each bar covers this part of the screen height
    private static final float TEXT_SCALE = 1.5F;

    // The bars
    private static boolean barsOn;
    private static long barsChangedMs = -1L;
    private static float barsFrom;
    private static Boolean hideGuiBefore;             // what "hide the HUD" was set to before the bars came, so we can put it back

    // The subtitle
    private static Component text;
    private static long textStartMs;
    private static long textInMs;
    private static long textHoldMs;
    private static long textOutMs;

    /** Called when a bars message arrives. */
    public static void onBars(CutscenePayloads.Bars packet) {
        barsFrom = barAmount();
        barsOn = packet.on();
        barsChangedMs = System.currentTimeMillis();

        // The bars look best on a clean picture, so the HUD (hearts, hotbar, crosshair) is hidden while they are in.
        Minecraft minecraft = Minecraft.getInstance();
        if (barsOn) {
            if (hideGuiBefore == null) {
                hideGuiBefore = minecraft.options.hideGui;
            }
            minecraft.options.hideGui = true;
        } else if (hideGuiBefore != null) {
            minecraft.options.hideGui = hideGuiBefore;
            hideGuiBefore = null;
        }
    }

    /** Called when a subtitle message arrives. */
    public static void onSubtitle(CutscenePayloads.Subtitle packet) {
        text = packet.text();
        textStartMs = System.currentTimeMillis();
        textInMs = packet.in() * 50L;
        textHoldMs = packet.hold() * 50L;
        textOutMs = packet.out() * 50L;
    }

    /** How far the bars are in right now, from 0 (gone) to 1 (fully in). They ease in and out, so they do not start or stop with a jerk. */
    private static float barAmount() {
        if (barsChangedMs < 0L) {
            return 0.0F;
        }
        float t = Mth.clamp((System.currentTimeMillis() - barsChangedMs) / (float) SLIDE_MS, 0.0F, 1.0F);
        t = t * t * (3.0F - 2.0F * t);   // "smoothstep": slow, fast, slow
        float target = barsOn ? 1.0F : 0.0F;
        return barsFrom + (target - barsFrom) * t;
    }

    // Draws the bars and the text on top of everything else on screen. Registered in MistvaleRpgClient.
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        renderBars(graphics);
        renderText(graphics);
    }

    private static void renderBars(GuiGraphics graphics) {
        float amount = barAmount();
        if (amount <= 0.001F) {
            return;
        }
        int height = (int) (graphics.guiHeight() * BAR_HEIGHT * amount);
        graphics.fill(0, 0, graphics.guiWidth(), height, 0xFF000000);
        graphics.fill(0, graphics.guiHeight() - height, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
    }

    private static void renderText(GuiGraphics graphics) {
        if (text == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - textStartMs;
        float opacity;
        if (elapsed < textInMs) {
            opacity = (float) elapsed / textInMs;
        } else if (elapsed < textInMs + textHoldMs) {
            opacity = 1.0F;
        } else if (elapsed < textInMs + textHoldMs + textOutMs) {
            opacity = 1.0F - (float) (elapsed - textInMs - textHoldMs) / textOutMs;
        } else {
            text = null; // finished
            return;
        }
        // A tiny bit of opacity (below about 4/255) is drawn as fully solid by the game, so never go under 4.
        int alpha = Mth.clamp((int) (opacity * 255.0F), 4, 255);

        Font font = Minecraft.getInstance().font;
        int wrapWidth = (int) (Math.min(graphics.guiWidth() - 60, 360) / TEXT_SCALE);
        List<FormattedCharSequence> lines = font.split(text, wrapWidth);
        int lineHeight = 12;
        // Draw the text scaled up: the pose stack scales everything drawn between push and pop.
        graphics.pose().pushPose();
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        float y = (graphics.guiHeight() / TEXT_SCALE - lines.size() * lineHeight) / 2.0F;
        for (FormattedCharSequence line : lines) {
            int x = (int) ((graphics.guiWidth() / TEXT_SCALE - font.width(line)) / 2.0F);
            graphics.drawString(font, line, x, (int) y, alpha << 24 | 0xE8E0D8, true);
            y += lineHeight;
        }
        graphics.pose().popPose();
    }

    // If you leave the world in the middle of a scene, put the HUD back and clear everything.
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        barsOn = false;
        barsChangedMs = -1L;
        text = null;
        if (hideGuiBefore != null) {
            Minecraft.getInstance().options.hideGui = hideGuiBefore;
            hideGuiBefore = null;
        }
    }

    private CinematicOverlay() {
    }
}
