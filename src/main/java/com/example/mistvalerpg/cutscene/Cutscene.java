package com.example.mistvalerpg.cutscene;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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

    /**
     * Shows the title card of a scene in the bottom left corner: the small line types in, then the big line fades in, they
     * stay, and both fade away. It starts at once. Times are in ticks: {@code typing} the typing, {@code pause} the wait after
     * the typing, {@code fade} the fade-in of the big line and also the fade-out of both, {@code hold} both stay after the fade-in.
     * It is drawn above everything, even a black screen.
     */
    public static void title(ServerPlayer player, Component small, Component big, int typing, int pause, int fade, int hold) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.Title(small, big, typing, pause, fade, hold));
    }

    /** Turns the player's own view by {@code degrees} (positive = right, negative = left) over {@code ticks} ticks, smoothly. */
    public static void look(ServerPlayer player, float degrees, int ticks) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.Look(degrees, ticks));
    }

    /** Puts the camera behind the player's shoulder and keeps it there ({@code true}), or gives the player their own camera back ({@code false}). */
    public static void shoulderView(ServerPlayer player, boolean on) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.ShoulderView(on, false));
    }

    /** Lets go of the forced shoulder view, but leaves the player in it: the camera stays where it is, and the player may change it again. */
    public static void keepShoulderView(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.ShoulderView(false, true));
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

    /**
     * Circles the camera once around {@code centre}, looking at it, at a distance of {@code radius} and {@code height} above it.
     * {@code startDegrees} is where the circle begins (0 = east, 90 = south). The whole circle takes {@code ticks} ticks.
     * It is eight queued flights, one for every eighth of a turn. Each is a Bezier curve whose handles point along the circle,
     * so the eight pieces join without a corner and the camera moves on a true circle at a steady speed.
     */
    public static void orbit(ServerPlayer player, Vec3 centre, double radius, double height, double startDegrees, int ticks) {
        int steps = 8;
        double turn = Math.PI * 2.0 / steps;
        double handle = 4.0 / 3.0 * Math.tan(turn / 4.0) * radius;   // how long the handles of a circular arc have to be
        for (int i = 0; i < steps; i++) {
            double a1 = Math.toRadians(startDegrees) + turn * i;
            double a2 = a1 + turn;
            Vec3 from = centre.add(Math.cos(a1) * radius, height, Math.sin(a1) * radius);
            Vec3 to = centre.add(Math.cos(a2) * radius, height, Math.sin(a2) * radius);
            Vec3 control1 = from.add(-Math.sin(a1) * handle, 0.0, Math.cos(a1) * handle);   // along the circle, forwards
            Vec3 control2 = to.add(Math.sin(a2) * handle, 0.0, -Math.cos(a2) * handle);     // along the circle, backwards
            curve(player, from, control1, control2, to, centre, centre, ticks / steps, false, true);
        }
    }

    /** Gives the player their own view back and lets them move again. */
    public static void endShots(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.EndShots());
    }

    /** Turns the player's view towards {@code target} and keeps it there, with a gentle zoom, until {@link #releaseFocus}. */
    public static void focus(ServerPlayer player, Entity target) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.Focus(target.getId()));
    }

    /** Lets the player's view go free again. */
    public static void releaseFocus(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CutscenePayloads.Focus(CutscenePayloads.Focus.RELEASE));
    }

    private Cutscene() {
    }
}
