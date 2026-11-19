package com.example.mistvalerpg.client;

import java.util.Locale;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.cutscene.CutscenePayloads;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The title card of a scene, in the bottom left corner: a small line in the game's own font that types in letter by letter,
 * with a little sound for every letter, and under it a big line in the blocky "Dungeons" font that fades in. Both stay for a moment and fade away together.
 * The server only says what to write and when to start (see CutscenePayloads.Title).
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class TitleCard {
    // Where everything goes, as a part of the screen, so it looks the same on a big and on a small window.
    private static final float SMALL_X = 0.099F;       // the small line starts this far from the left edge (0.1 = a tenth of the width)
    private static final float SMALL_Y = 0.667F;       // and this far from the top
    private static final float BIG_X = 0.150F;         // the big line is pushed in a little
    private static final float BIG_Y = 0.730F;
    private static final float SMALL_SIZE = 1.35F;     // how much bigger than normal text the lines are drawn
    private static final float BIG_SIZE = 3.6F;
    // The blocky font of the big line: a picture of the letters, listed in assets/mistvale_rpg/font/dungeons.json.
    private static final Style DUNGEONS = Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "dungeons"));
    private static final SoundEvent TYPE_SOUND = SoundEvents.UI_BUTTON_CLICK.value();   // the sound for every letter that appears

    private static Component small;
    private static Component big;
    private static long startMs;
    private static long typingMs;
    private static long pauseMs;
    private static long fadeMs;
    private static long holdMs;
    private static int lettersHeard;                    // how many letters already made their sound

    /** Called when the message from the server arrives. A ticks value is turned into milliseconds (1 tick = 50 ms). */
    public static void onPacket(CutscenePayloads.Title packet) {
        small = packet.small();
        big = packet.big();
        startMs = System.currentTimeMillis();
        typingMs = Math.max(1L, packet.typing() * 50L);
        pauseMs = packet.pause() * 50L;
        fadeMs = Math.max(1L, packet.fade() * 50L);
        holdMs = packet.hold() * 50L;
        lettersHeard = 0;
    }

    // Draws the card. Registered in MistvaleRpgClient, above the fade.
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (small == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        long bigStart = typingMs + pauseMs;           // the big line starts after the typing and a pause
        long holdEnd = bigStart + fadeMs + holdMs;    // and stays, fully there, for the hold
        if (elapsed >= holdEnd + fadeMs) {
            small = null; // finished
            return;
        }

        // The whole card fades out at the end.
        float outFade = elapsed < holdEnd ? 1.0F : 1.0F - (float) (elapsed - holdEnd) / fadeMs;
        // The big line fades in after the small one has finished typing and the pause.
        float bigFade = Mth.clamp((float) (elapsed - bigStart) / fadeMs, 0.0F, 1.0F);

        // The small line: as many letters as the time says, with a click for every new one.
        String words = small.getString();
        int shown = Mth.clamp((int) (elapsed * words.length() / typingMs), 0, words.length());
        while (lettersHeard < shown) {
            if (!Character.isWhitespace(words.charAt(lettersHeard))) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(TYPE_SOUND, 1.5F, 0.5F));
            }
            lettersHeard++;
        }

        Font font = Minecraft.getInstance().font;
        float unit = graphics.guiHeight() / 270.0F;   // 1.0 at the usual size of a full HD screen
        drawLine(graphics, font, Component.literal(words.substring(0, shown)), SMALL_X, SMALL_Y, SMALL_SIZE * unit, outFade);
        if (bigFade > 0.0F) {
            Component name = Component.literal(big.getString().toUpperCase(Locale.ROOT)).withStyle(DUNGEONS);   // the font only has capitals
            drawLine(graphics, font, name, BIG_X, BIG_Y, BIG_SIZE * unit, bigFade * outFade);
        }
    }

    // Draws one line of white text at a place on the screen (as a part of its width and height), scaled up, with some opacity.
    private static void drawLine(GuiGraphics graphics, Font font, Component line, float x, float y, float size, float opacity) {
        // A tiny bit of opacity (below about 4/255) is drawn as fully solid by the game, so never go under 4.
        int alpha = Mth.clamp((int) (opacity * 255.0F), 4, 255);
        graphics.pose().pushPose();
        graphics.pose().scale(size, size, 1.0F);
        graphics.drawString(font, line, Math.round(graphics.guiWidth() * x / size), Math.round(graphics.guiHeight() * y / size), alpha << 24 | 0xFFFFFF, false);
        graphics.pose().popPose();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        small = null;
    }

    private TitleCard() {
    }
}
