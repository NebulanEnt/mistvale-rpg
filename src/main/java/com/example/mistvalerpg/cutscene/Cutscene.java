package com.example.mistvalerpg.cutscene;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
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

    /** Puts the camera behind the player's shoulder and keeps it there ({@code true}), or gives the player their own camera back ({@code false}). */
    public static void shoulderView(ServerPlayer player, boolean on) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.ShoulderView(on));
    }

    /**
     * Flies the camera from {@code from} to {@code to} in {@code ticks} ticks while it looks at a point moving from {@code aimFrom} to {@code aimTo}.
     * With {@code queue} the flight waits for the one in progress; without it, it cuts that one off.
     * Positions are in the world, and the camera sits at exactly that spot (where eyes would be).
     * Always end a scene with {@link #endShots}, or the player stays frozen in the cutscene.
     */
    public static void shot(ServerPlayer player, Vec3 from, Vec3 to, Vec3 aimFrom, Vec3 aimTo, int ticks, boolean smooth, boolean queue) {
        // A straight flight is a curve whose two handles sit on the line, a third and two thirds of the way along it.
        curve(player, from, from.lerp(to, 1.0 / 3.0), from.lerp(to, 2.0 / 3.0), to, aimFrom, aimTo, ticks, smooth, queue);
    }

    /**
     * Like {@link #shot}, but along a curve. The camera leaves {@code from} heading towards {@code control1}, arrives at {@code to}
     * coming from {@code control2}, and bends smoothly in between: a cubic Bezier curve. The two control points are not on the
     * path, they pull it, like the handles of a path tool in a drawing program.
     */
    public static void curve(ServerPlayer player, Vec3 from, Vec3 control1, Vec3 control2, Vec3 to, Vec3 aimFrom, Vec3 aimTo, int ticks, boolean smooth, boolean queue) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.Shot(from, control1, control2, to, aimFrom, aimTo, ticks, smooth, queue));
    }

    /** Gives the player their own view back and lets them move again. */
    public static void endShots(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.EndShots());
    }

    private Cutscene() {
    }
}
