package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * While a scene runs the player is only watching: they cannot walk, jump, click or press any game key.
 * Chat (and Escape for the menu) still work, so nobody is ever locked out of the game.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class CutsceneLock {

    // The walking keys are read once per tick; here we wipe what was read.
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (CameraShots.isActive()) {
            Input input = event.getInput();
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }

    // The mouse: moving it must not turn the player either. The game turns the player by (sensitivity * 0.6 + 0.2)^3 * 8 for
    // every bit the mouse moves. At a sensitivity of -1/3 the part in brackets is exactly zero, so the mouse turns nothing.
    // (The player's own setting is not touched: the event only changes the value used for this one calculation.)
    @SubscribeEvent
    public static void onTurn(CalculatePlayerTurnEvent event) {
        if (CameraShots.isActive()) {
            event.setMouseSensitivity(-1.0 / 3.0);
            event.setCinematicCameraEnabled(false);
        }
    }

    // Left click (attack) and right click (use, place) are cancelled before they do anything.
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (CameraShots.isActive()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    // Every other key press (inventory, drop, hotbar, F5...) is "consumed" before the game can act on it.
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!CameraShots.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        for (KeyMapping key : minecraft.options.keyMappings) {
            if (key == minecraft.options.keyChat || key == minecraft.options.keyCommand) {
                continue;
            }
            while (key.consumeClick()) {
                // read and thrown away
            }
        }
    }

    private CutsceneLock() {
    }
}