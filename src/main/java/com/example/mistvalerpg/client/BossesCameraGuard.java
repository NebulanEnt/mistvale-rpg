package com.example.mistvalerpg.client;

import java.lang.reflect.Method;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.prologue.PrologueRegistry;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Keeps Bosses'Rise's own film camera out of the prologue sea.
 *
 * When the Kraken rises, Bosses'Rise plays a 24 second film of its own: black bars, a hidden screen and a camera that is
 * supposed to ride on a bone of the Kraken's model. In our sea that camera does not follow the model, so the player only sees
 * the bars and the screen blinking. The prologue directs its own shots instead (see Prologue.krakenFilm), so here, as long as the
 * player is in the prologue sea, we switch that film camera off every time it starts.
 *
 * Bosses'Rise is not something we compile against, so it is reached by name ("reflection"). If the mod or the method is not
 * there, nothing happens.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class BossesCameraGuard {
    private static final String CAMERA_CLASS = "net.unusual.block_factorys_bosses.client.camera.BossesRiseClientCinematicCamera";

    private static boolean looked;     // have we looked for the mod's methods yet?
    private static Method isActive;    // BossesRiseClientCinematicCamera.isCameraActive()
    private static Method stop;        // BossesRiseClientCinematicCamera.stopCinematicCamera()

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        switchOff();
    }

    // Also before every picture, so not even one picture of the other camera is ever drawn.
    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        switchOff();
    }

    private static void switchOff() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().equals(PrologueRegistry.SEA)) {
            return;
        }
        if (!looked) {
            looked = true;
            try {
                Class<?> camera = Class.forName(CAMERA_CLASS, false, BossesCameraGuard.class.getClassLoader());
                isActive = camera.getMethod("isCameraActive");
                stop = camera.getMethod("stopCinematicCamera");
            } catch (ReflectiveOperationException | LinkageError e) {
                MistvaleRpg.LOGGER.info("Bosses'Rise's film camera was not found, so there is nothing to switch off: {}", e.toString());
            }
        }
        if (isActive == null || stop == null) {
            return;
        }
        try {
            if ((Boolean) isActive.invoke(null)) {
                stop.invoke(null);
            }
        } catch (ReflectiveOperationException e) {
            isActive = null;   // it did not work once; do not try again every picture
            MistvaleRpg.LOGGER.warn("Could not switch off Bosses'Rise's film camera", e);
        }
    }

    private BossesCameraGuard() {
    }
}
