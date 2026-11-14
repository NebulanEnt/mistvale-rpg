package com.example.mistvalerpg.cutscene;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The cutscene toolbox, as the rest of the mod uses it. Every method here is one line a story script can write
 * ("fade the screen", "show a subtitle", ...); each one just sends a message to the player (see CutscenePayloads).
 */
public final class Cutscene {

    /** Fades the player's screen to {@code color} and back. Times are in ticks, 20 ticks = 1 second. */
    public static void fade(ServerPlayer player, int in, int hold, int out, int color) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.Fade(in, hold, out, color));
    }

    private Cutscene() {
    }
}