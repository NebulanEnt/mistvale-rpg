package com.example.mistvalerpg.cutscene;

import net.minecraft.network.chat.Component;
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

    /** Slides the black film bars in ({@code true}) or out ({@code false}) and hides the heads-up display while they are in. */
    public static void bars(ServerPlayer player, boolean on) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.Bars(on));
    }

    /** Shows a line of text in the middle of the player's screen. Times are in ticks. */
    public static void subtitle(ServerPlayer player, Component text, int in, int hold, int out) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.Subtitle(text, in, hold, out));
    }

    private Cutscene() {
    }
}
