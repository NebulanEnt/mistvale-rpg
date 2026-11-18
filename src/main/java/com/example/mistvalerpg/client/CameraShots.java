package com.example.mistvalerpg.client;

import java.util.ArrayDeque;
import java.util.Deque;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.cutscene.CutscenePayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * The film camera. During a scene the game looks through a <em>camera of our own</em> instead of the player's eyes:
 * an invisible, empty armour stand that we move a little before every picture the game draws, along the path the server asked for
 * (see CutscenePayloads.Shot). The player's own body stays where it is, and shows up on screen like any other character.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class CameraShots {
    private static final int MAX_TICKS = 20 * 180;   // safety net: after 3 minutes the player always gets their view back

    private static final Deque<CutscenePayloads.Shot> QUEUE = new ArrayDeque<>();   // flights waiting for their turn
    private static CutscenePayloads.Shot shot;       // the flight in progress, or null
    private static ArmorStand camera;                // our camera; null when no scene is on
    private static int shotTick;                     // how many whole ticks of the flight have passed
    private static int sceneTicks;                   // how long the whole scene has been on

    /** True while a scene has the camera. Other code (the input lock) asks this. */
    public static boolean isActive() {
        return camera != null;
    }

    /** Called when a flight message arrives. */
    public static void onShot(CutscenePayloads.Shot packet) {
        if (packet.queue() && shot != null) {
            QUEUE.add(packet);
        } else {
            QUEUE.clear();
            start(packet, false);   // a cut: the camera appears at the start of the new flight
        }
    }

    /** Called when the end message arrives. */
    public static void onEnd() {
        stop();
    }

    // Starts one flight. If this is the first of a scene, it also takes over the camera.
    // "chained" means it follows the flight before it without a break: the camera is already where this flight starts and
    // moving, so it must not be put down again, or it would stand still for one tick at every joint.
    private static void start(CutscenePayloads.Shot next, boolean chained) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (camera == null) {
            ViewControl.pause();
            camera = new ArmorStand(EntityType.ARMOR_STAND, minecraft.level);
            sceneTicks = 0;
            minecraft.setCameraEntity(camera);
        }
        shot = next;
        shotTick = 0;
        if (!chained) {
            pose(next, 0.0);   // the camera appears at the start of the flight
        }
    }

    // A point on a cubic Bezier curve, t going from 0 (at p0) to 1 (at p3). The curve is pulled towards the handles p1 and p2:
    //   (1-t)^3 * p0  +  3 (1-t)^2 t * p1  +  3 (1-t) t^2 * p2  +  t^3 * p3
    private static Vec3 bezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1.0 - t;
        return p0.scale(u * u * u).add(p1.scale(3.0 * u * u * t)).add(p2.scale(3.0 * u * t * t)).add(p3.scale(t * t * t));
    }

    // Puts the camera where the flight is "elapsed" ticks after it began (a fraction of a tick is fine: 12.4 means a little
    // after tick 12). The pose is worked out from the curve itself, so it is always exact, however often the game draws.
    private static void pose(CutscenePayloads.Shot flight, double elapsed) {
        float t = flight.ticks() <= 0 ? 1.0F : (float) Mth.clamp(elapsed / flight.ticks(), 0.0, 1.0);
        if (flight.smooth()) {
            t = t * t * (3.0F - 2.0F * t);   // "smoothstep": slow, fast, slow
        }
        place(bezier(flight.from(), flight.control1(), flight.control2(), flight.to(), t), flight.aimFrom().lerp(flight.aimTo(), t));
    }

    // Moves the camera so that its eyes are at "eyes" and it looks at "aim".
    private static void place(Vec3 eyes, Vec3 aim) {
        double y = eyes.y - camera.getEyeHeight();
        Vec3 toAim = aim.subtract(eyes);
        float yaw = (float) Math.toDegrees(Math.atan2(-toAim.x, toAim.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(toAim.y, Math.sqrt(toAim.x * toAim.x + toAim.z * toAim.z)));
        // Turn by the shortest way round, so the camera never spins a whole circle because 179 degrees became -179.
        yaw = camera.getYRot() + Mth.wrapDegrees(yaw - camera.getYRot());
        // The game normally blends an entity between where it was a tick ago and where it is now. We already put the camera at
        // the exact spot for this very picture, so "before" and "now" are made the same and nothing is blended.
        camera.xo = camera.xOld = eyes.x;
        camera.yo = camera.yOld = y;
        camera.zo = camera.zOld = eyes.z;
        camera.yRotO = yaw;
        camera.xRotO = pitch;
        camera.setPos(eyes.x, y, eyes.z);
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        camera.setYHeadRot(yaw);
    }

    // Before every picture the game draws (many more than twenty a second): put the camera where the flight is right now.
    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (camera != null && shot != null) {
            pose(shot, shotTick + event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (camera == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // The world changed under us (the player died, went through a portal, left): the scene is over.
        if (minecraft.player == null || camera.level() != minecraft.level || ++sceneTicks > MAX_TICKS) {
            stop();
            return;
        }
        if (minecraft.getCameraEntity() != camera) {
            minecraft.setCameraEntity(camera);   // something took the camera away, take it back
        }
        if (shot == null) {
            return;
        }

        shotTick++;   // the pictures in between are placed by onFrame

        if (shotTick >= shot.ticks()) {
            // This flight is done: stay at its end, then start the next one (if there is one) until the server ends the scene.
            pose(shot, shot.ticks());
            shot = null;
            CutscenePayloads.Shot following = QUEUE.poll();
            if (following != null) {
                start(following, true);
            }
        }
    }

    // The player's own hand and held item would float in front of our camera, so they are not drawn during a scene.
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (camera != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }

    // Gives the camera back to the player.
    private static void stop() {
        QUEUE.clear();
        shot = null;
        if (camera != null) {
            camera = null;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.setCameraEntity(minecraft.player);
            }
            ViewControl.resume();
        }
    }

    private CameraShots() {
    }
}