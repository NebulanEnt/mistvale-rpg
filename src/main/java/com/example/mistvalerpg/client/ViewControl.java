package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.cutscene.CutscenePayloads;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Decides how the player's camera looks at them. For the story we sometimes want the game to put the camera behind
 * the player's shoulder and keep it there, even if the player presses F5 (the key that cycles the camera).
 *
 * We ask Leawind's Third Person for its over-the-shoulder camera (see PerspectiveLink). If the Perspective API is
 * switched off in its settings, we use the game's own camera that hangs behind the player instead.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class ViewControl {
    private static final int CHECK_EVERY = 4;   // ticks between two checks that nobody changed the camera

    private static boolean forced;               // true while the story wants the shoulder view
    private static CameraType typeBefore;        // what the camera was before we took it over
    private static String perspectiveBefore;     // the same, in the Perspective API's own words (null if the API is switched off)

    /** Called when the message from the server arrives. */
    public static void onShoulder(CutscenePayloads.ShoulderView packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (packet.on()) {
            if (!forced) {
                typeBefore = minecraft.options.getCameraType();
                perspectiveBefore = PerspectiveLink.current();
                forced = true;
            }
            applyShoulder(minecraft);
        } else if (forced) {
            forced = false;
            restore(minecraft);
        }
    }

    private static void applyShoulder(Minecraft minecraft) {
        PerspectiveLink.select(PerspectiveLink.SHOULDER);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    // Puts the camera back the way it was before the story took it over.
    private static void restore(Minecraft minecraft) {
        if (perspectiveBefore != null) {
            PerspectiveLink.select(perspectiveBefore);
        }
        if (typeBefore != null) {
            minecraft.options.setCameraType(typeBefore);
        }
        typeBefore = null;
        perspectiveBefore = null;
    }

    // While the shoulder view is forced, now and then check that nobody (the player's F5 key, another mod) took it away.
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!forced || minecraft.player == null || minecraft.player.tickCount % CHECK_EVERY != 0) {
            return;
        }
        String current = PerspectiveLink.current();
        boolean wrongPerspective = current != null && !PerspectiveLink.SHOULDER.equals(current);
        if (wrongPerspective || minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            applyShoulder(minecraft);
        }
    }

    // Leaving the world: forget everything, the camera setting belongs to the next world now.
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        forced = false;
        typeBefore = null;
        perspectiveBefore = null;
    }

    private ViewControl() {
    }
}