package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.ModSounds;
import com.example.mistvalerpg.rift.RiftVariant;
import com.example.mistvalerpg.rift.RiftWarp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * What the player sees and hears while a rift pulls them through: the screen floods with streaks
 * of the rift's colour, the field of view stretches (or, for the Corrupted Rift, squeezes and
 * tilts), and everything fades out again once they have arrived.
 *
 * The server only sends three short messages (see RiftWarp); everything else happens here.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class WarpOverlay {
    private enum Phase { NONE, PULL, ARRIVED, ABORT }

    private static final long PULL_MS = 850L;      // build-up while you are held in place; matches the rift's 18-tick delay
    private static final long ARRIVED_MS = 1400L;  // fade-out after landing
    private static final long ABORT_MS = 500L;     // quick fade if the rift closes on you

    private static Phase phase = Phase.NONE;
    private static RiftVariant variant = RiftVariant.BLUE;
    private static long phaseStart;
    private static float abortFrom;

    // The streaks are made once, at random, and then just animated.
    private static final int STREAKS = 110;
    private static final float[] ANGLE = new float[STREAKS];
    private static final float[] PHASE_OFFSET = new float[STREAKS];
    private static final float[] SPEED = new float[STREAKS];
    private static final float[] LENGTH = new float[STREAKS];
    private static final float[] WIDTH = new float[STREAKS];
    static {
        java.util.Random random = new java.util.Random(12345L);
        for (int i = 0; i < STREAKS; i++) {
            ANGLE[i] = random.nextFloat() * Mth.TWO_PI;
            PHASE_OFFSET[i] = random.nextFloat();
            SPEED[i] = 0.6F + random.nextFloat() * 1.4F;
            LENGTH[i] = 0.08F + random.nextFloat() * 0.22F;
            WIDTH[i] = 1.0F + random.nextFloat() * 2.5F;
        }
    }

    /** Called when one of the three messages from the server arrives. */
    public static void onPacket(RiftWarp.Warp packet) {
        RiftVariant[] all = RiftVariant.values();
        RiftVariant incoming = all[Math.floorMod(packet.variant(), all.length)];
        long now = System.currentTimeMillis();

        switch (packet.phase()) {
            case RiftWarp.IN -> {
                variant = incoming;
                phase = Phase.PULL;
                phaseStart = now;
                playSound(incoming == RiftVariant.CORRUPTED ? ModSounds.VOID_CHARGE.get() : ModSounds.WARP_CHARGE.get(), RiftWarp.pitch(incoming));
            }
            case RiftWarp.OUT -> {
                variant = incoming;
                phase = Phase.ARRIVED;
                phaseStart = now;
                playSound(incoming == RiftVariant.CORRUPTED ? ModSounds.VOID_DEVOUR.get() : ModSounds.WARP_BLOW.get(), RiftWarp.pitch(incoming));
            }
            default -> {
                if (phase == Phase.PULL) {
                    abortFrom = intensity(now);
                    phase = Phase.ABORT;
                    phaseStart = now;
                }
            }
        }
    }

    // These sounds belong to the screen effect, so they play "on the player's ear" instead of at a place in the world.
    private static void playSound(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 1.0F));
    }

    /** How strong the effect is right now, from 0 (nothing) to 1 (full). */
    private static float intensity(long now) {
        long t = now - phaseStart;
        return switch (phase) {
            case PULL -> Mth.clamp((float) t / PULL_MS, 0.0F, 1.0F);
            case ARRIVED -> 1.0F - Mth.clamp((float) t / ARRIVED_MS, 0.0F, 1.0F);
            case ABORT -> abortFrom * (1.0F - Mth.clamp((float) t / ABORT_MS, 0.0F, 1.0F));
            default -> 0.0F;
        };
    }

    // The colours of the streaks, as 0xRRGGBB.
    private static int[] colours(RiftVariant v) {
        return switch (v) {
            case BLUE -> new int[] { 0xFFFFFF, 0x2E9BFF, 0x6FB8FF, 0x1F6FD0 };
            case ORANGE -> new int[] { 0xFFFFFF, 0xFF9A1E, 0xFFC04A, 0xFF6A00 };
            case CORRUPTED -> new int[] { 0x7A1FD0, 0x2B0A4A, 0x13BFB0, 0xB07CFF };
            case WHITE -> new int[] { 0xFFFFFF, 0xF4F8FB, 0xDCE9F2, 0xBFD6E6 };
            case END -> new int[] { 0xFFFFFF, 0xB07CFF, 0x7A3FE0, 0xE0C8FF };
            case DEEP -> new int[] { 0xFFFFFF, 0x6FE8FF, 0xBFFFFF, 0xFF9AD0 };
        };
    }

    // Draws the effect on top of everything else on screen. Registered in MistvaleRpgClient.
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (phase == Phase.NONE) {
            return;
        }
        long now = System.currentTimeMillis();
        float k = intensity(now);
        if (k <= 0.0F) {
            if (phase != Phase.PULL) {
                phase = Phase.NONE; // finished fading
            }
            return;
        }

        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int[] colours = colours(variant);

        // a wash of the main colour over everything
        int wash = colours[1];
        graphics.fill(0, 0, w, h, FastColor.ARGB32.color((int) (150 * k * k), FastColor.ARGB32.red(wash), FastColor.ARGB32.green(wash), FastColor.ARGB32.blue(wash)));

        // streaks rushing out from the middle of the screen (or, for the Corrupted Rift, into it)
        boolean inward = variant == RiftVariant.CORRUPTED;
        float reach = (float) Math.hypot(w / 2.0, h / 2.0);
        float seconds = now / 1000.0F;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(w / 2.0F, h / 2.0F, 0.0F);
        for (int i = 0; i < STREAKS; i++) {
            float travel = (seconds * SPEED[i] * (0.5F + k) + PHASE_OFFSET[i]) % 1.0F;
            if (inward) {
                travel = 1.0F - travel;
            }
            float start = travel * reach;
            float end = start + LENGTH[i] * reach * k;
            int colour = colours[i % colours.length];
            int alpha = (int) (230 * k * Mth.sin(travel * Mth.PI));
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(ANGLE[i]));
            graphics.fill((int) start, (int) -WIDTH[i], (int) end, (int) WIDTH[i], FastColor.ARGB32.color(alpha, FastColor.ARGB32.red(colour), FastColor.ARGB32.green(colour), FastColor.ARGB32.blue(colour)));
            pose.popPose();
        }
        pose.popPose();

        // a white flash right at the end of the pull
        if (phase == Phase.PULL && k > 0.8F) {
            graphics.fill(0, 0, w, h, FastColor.ARGB32.color((int) (255 * (k - 0.8F) * 5.0F), 255, 255, 255));
        }
    }

    // Stretch the view while you are pulled through (the Corrupted Rift squeezes it instead).
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (phase == Phase.NONE) {
            return;
        }
        float k = intensity(System.currentTimeMillis());
        if (k <= 0.0F) {
            return;
        }
        double fov = event.getFOV();
        if (variant == RiftVariant.CORRUPTED) {
            fov = Math.max(28.0, fov - 45.0 * k * k);
        } else {
            fov = Math.min(150.0, fov + 62.0 * k * k);
        }
        event.setFOV(fov);
    }

    // The Corrupted Rift also tilts the whole picture back and forth.
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (phase == Phase.NONE || variant != RiftVariant.CORRUPTED) {
            return;
        }
        long now = System.currentTimeMillis();
        float k = intensity(now);
        if (k > 0.0F) {
            event.setRoll(event.getRoll() + (float) Math.sin(now / 260.0) * 14.0F * k * k);
        }
    }

    // Leaving a world in the middle of a warp must not leave the effect stuck on.
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        phase = Phase.NONE;
    }

    private WarpOverlay() {
    }
}
