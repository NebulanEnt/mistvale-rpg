package com.example.mistvalerpg.client;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.cutscene.CutscenePayloads;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Makes the player's view find somebody: it turns along a smooth curve until the player is looking at them,
 * then it follows them and zooms in a little, like a camera operator who has found the speaker. When the story
 * lets go, the zoom eases away. Used when the Spirit speaks (see DialogueManager).
 *
 * Everything here is worked out before every picture the game draws, not once per tick: the game draws many more
 * pictures than there are ticks, and a turn made only twenty times a second looks like steps.
 *
 * The turning is done the way the mouse does it, with player.turn(...). That matters: in the over-the-shoulder view
 * Leawind's Third Person keeps its own camera direction, which only listens to that call, so setting the player's
 * angles directly would change nothing on screen.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class FocusCamera {
    private static final float TURN_TICKS = 16.0F;    // the turn takes this long: 16 ticks, a little under a second
    private static final float ZOOM_STEP = 0.08F;     // how fast the zoom builds up, per tick (1.0 = fully zoomed after 12.5 ticks)
    private static final float ZOOM_AMOUNT = 0.12F;   // fully zoomed, the view is this much narrower
    private static final float MOUSE_SCALE = 0.15F;   // the game turns the player by this many degrees for every unit of mouse movement

    private static int target = CutscenePayloads.Focus.RELEASE;   // id of the entity we look at, or RELEASE
    private static float zoom;                                    // 0 = no zoom, 1 = fully zoomed
    private static float elapsed;                                 // how many ticks the turn has been going on
    private static boolean started;                               // true once we know from where the turn begins
    private static boolean wasShot;                               // a film camera had the view in the last picture
    private static long filmEndedMs = -100000L;                   // when the last film camera let go of the view
    private static boolean afterFilm;                             // this turn began right after a film
    private static float startYaw;                                // the view when the turn began
    private static float startPitch;
    private static float viewYaw;                                 // where we have turned the view to so far
    private static float viewPitch;

    /** Called when the message from the server arrives. */
    public static void onPacket(CutscenePayloads.Focus packet) {
        target = packet.entityId();
        elapsed = 0.0F;
        started = false;
    }

    // A point on a cubic Bezier curve with four values, t going from 0 to 1 (the same formula as the camera flights use,
    // but for one number instead of a position). With the handles at 0 and 1 the curve starts slowly, speeds up and
    // ends slowly: exactly the "ease in, ease out" we want for a turn.
    private static float bezier(float p0, float p1, float p2, float p3, float t) {
        float u = 1.0F - t;
        return u * u * u * p0 + 3.0F * u * u * t * p1 + 3.0F * u * t * t * p2 + t * t * t * p3;
    }

    // Runs right before every picture the game draws.
    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean shot = CameraShots.isActive();
        if (wasShot && !shot) {
            filmEndedMs = System.currentTimeMillis();
        }
        wasShot = shot;
        Entity entity = player == null || target == CutscenePayloads.Focus.RELEASE ? null : player.level().getEntity(target);

        // How much time has passed since the last picture, counted in ticks (a tick is 1/20 of a second).
        float ticks = event.getPartialTick().getRealtimeDeltaTicks();

        // The zoom grows while we are looking at someone and fades away when we are not.
        zoom = Mth.clamp(zoom + (entity != null ? ZOOM_STEP : -ZOOM_STEP) * ticks, 0.0F, 1.0F);
        if (entity == null || shot) {
            started = false;
            return; // nobody to look at (or a camera flight has the view): leave the player's view alone
        }

        // Where is the view now, and where is it looking from? What is on screen decides, so we ask the camera. In the
        // over-the-shoulder view that is not the same as the player's own angles. (In the "front" view the camera looks back
        // at the player, so there we use the player's own angles.)
        Camera camera = minecraft.gameRenderer.getMainCamera();
        // Right after a film, do not ask the camera: for a moment it still shows the last picture of the film, and the camera
        // mod glides from there to its own view. We would turn from the wrong place, and the view would swing round to look at
        // the player's own face. The player's own angles are what the camera mod starts from, so we use those.
        if (!started) {
            afterFilm = System.currentTimeMillis() - filmEndedMs < 3000L;
        }
        boolean ownAngles = afterFilm || !camera.isInitialized() || minecraft.options.getCameraType().isMirrored();
        Vec3 from = ownAngles ? player.getEyePosition() : camera.getPosition();
        float yaw = ownAngles ? player.getYRot() : camera.getYRot();
        float pitch = ownAngles ? player.getXRot() : camera.getXRot();
        if (!started) {
            started = true;
            startYaw = viewYaw = yaw;
            startPitch = viewPitch = pitch;
        }
        // From here on we remember where we turned the view to, instead of asking the camera again every picture: the
        // camera mods may blend their picture a little, and a view that is still catching up would make us turn too far.

        // Which way is "towards them"? Work out the angle from there to the middle of their body.
        Vec3 delta = entity.getBoundingBox().getCenter().subtract(from);
        float wantedYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float wantedPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));

        // How far along the turn are we (0 to 1), and how much of the way is that? The curve decides.
        elapsed += ticks;
        float progress = Mth.clamp(elapsed / TURN_TICKS, 0.0F, 1.0F);
        float eased = bezier(0.0F, 0.0F, 1.0F, 1.0F, progress);

        // Where the view should be right now: part of the way from where it began to where they are. "wrapDegrees" makes it
        // turn the short way round, never the long way. Worked out again for every picture, so if they move, we follow.
        float shouldYaw = startYaw + Mth.wrapDegrees(wantedYaw - startYaw) * eased;
        float shouldPitch = startPitch + (wantedPitch - startPitch) * eased;

        // Turn the way the mouse turns. The numbers are mouse units, so we divide the degrees by the mouse scale.
        player.turn(Mth.wrapDegrees(shouldYaw - viewYaw) / MOUSE_SCALE, (shouldPitch - viewPitch) / MOUSE_SCALE);
        viewYaw = shouldYaw;
        viewPitch = Mth.clamp(shouldPitch, -90.0F, 90.0F);
    }

    // The zoom: narrow the field of view (how wide the picture is) a little.
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (zoom > 0.0F) {
            float eased = bezier(0.0F, 0.0F, 1.0F, 1.0F, zoom);   // slow, fast, slow
            event.setFOV(event.getFOV() * (1.0 - ZOOM_AMOUNT * eased));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        target = CutscenePayloads.Focus.RELEASE;
        zoom = 0.0F;
        started = false;
        wasShot = false;
    }

    private FocusCamera() {
    }
}
