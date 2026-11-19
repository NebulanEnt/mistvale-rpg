package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.cutscene.CutscenePayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Turns the player's body and head by a given angle over a given time, on a smooth curve: how a film makes a character
 * "look around". The server only says how far and how long (see CutscenePayloads.Look). The turn is worked out before
 * every picture the game draws, so it is smooth however fast the screen is.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class LookAround {
    private static long startMs = -1L;   // -1 means no turn is going on
    private static long durationMs;
    private static float startYaw;       // where the player looked when the turn began
    private static float degrees;        // how far to turn in all

    /** Called when the message from the server arrives. */
    public static void onPacket(CutscenePayloads.Look packet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        startMs = System.currentTimeMillis();
        durationMs = Math.max(1L, packet.ticks() * 50L);
        startYaw = player.getYRot();
        degrees = packet.degrees();
    }

    // A point on a cubic Bezier curve with four values, t going from 0 to 1. With the handles at 0 and 1 it starts slowly,
    // speeds up and ends slowly: the same curve the Spirit's focus uses.
    private static float bezier(float p0, float p1, float p2, float p3, float t) {
        float u = 1.0F - t;
        return u * u * u * p0 + 3.0F * u * u * t * p1 + 3.0F * u * t * t * p2 + t * t * t * p3;
    }

    // Runs right before every picture the game draws.
    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (startMs < 0L || player == null) {
            return;
        }
        float t = Mth.clamp((System.currentTimeMillis() - startMs) / (float) durationMs, 0.0F, 1.0F);
        float yaw = startYaw + degrees * bezier(0.0F, 0.0F, 1.0F, 1.0F, t);
        // The game blends a player between "a tick ago" and "now". We know the exact angle for this very picture, so both are set to it.
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        if (t >= 1.0F) {
            startMs = -1L;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        startMs = -1L;
    }

    private LookAround() {
    }
}