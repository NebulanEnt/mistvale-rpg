package com.example.mistvalerpg.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.example.mistvalerpg.ModAttachments;
import com.example.mistvalerpg.ModSounds;
import com.example.mistvalerpg.cutscene.Cutscene;
import com.example.mistvalerpg.cutscene.Later;
import com.example.mistvalerpg.dialogue.DialogueManager;
import com.example.mistvalerpg.dialogue.DialogueScenes;
import com.example.mistvalerpg.rift.RiftBlock;
import com.example.mistvalerpg.rift.RiftBlockEntity;
import com.example.mistvalerpg.rift.RiftVariant;
import com.example.mistvalerpg.spirit.SpiritEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The film that begins a new player's life in the land they chose. The screen fades to black, the player is sent out from
 * the world spawn towards their land, a title card types itself in on the black screen, the picture fades up on the place
 * where a rift is about to open, the player walks out of the rift and looks around, the Spirit appears, and the film
 * cuts to the over-the-shoulder view where she welcomes them.
 *
 * Like every film in the mod it is made of the cutscene toolbox and nothing else: a list of things that happen at moments
 * in time (see Later), written one under the other so it can be read like a script. All times are in ticks, 20 = 1 second.
 */
public final class SpawnScene {
    /** How far from the world spawn the rift opens, in blocks, in the direction of the chosen land. */
    private static final int DISTANCE = 50;
    /** The fade to black before the film proper: one second. */
    private static final int LEAD = 20;
    /** How long the film proper lasts, up to the cut to the conversation: 20.5 seconds. */
    private static final int FILM_LENGTH = 410;

    // For every player: the server tick at which their current scene is over. A second scene cannot start before that.
    private static final Map<UUID, Long> BUSY_UNTIL = new HashMap<>();

    /** Plays the film for the given land. */
    public static void play(ServerPlayer player, Region region) {
        if (!begin(player, LEAD + FILM_LENGTH + 40)) {
            return;
        }
        MinecraftServer server = player.server;
        Cutscene.bars(player, true);
        // Fade to black in one second, stay black until the picture is ready to come back (a hundred ticks into the film), fade up in two seconds.
        Cutscene.fade(player, LEAD, 100, 40, 0x000000);
        at(server, player, LEAD, () -> film(player, region));
    }

    /**
     * The same film, for the land the player chose in the conversation, but it waits until the conversation is closed.
     * A conversation and a film must never run at the same time: both want the screen and the camera.
     * Every half second it looks again. (A player who chose nothing gets the north.)
     */
    public static void playAfterTalk(ServerPlayer player) {
        if (player.hasDisconnected()) {
            return;
        }
        if (DialogueManager.isTalking(player)) {
            Later.after(player.server, 10, () -> playAfterTalk(player));
            return;
        }
        Region region = Region.byId(player.getData(ModAttachments.REGION));
        play(player, region == null ? Region.NORTH : region);
    }

    // The film proper. It begins when the screen is fully black: from here on, "tick 0" is that moment.
    private static void film(ServerPlayer player, Region region) {
        MinecraftServer server = player.server;
        ServerLevel level = server.overworld();

        // Where: from the world spawn, DISTANCE blocks towards the chosen land, on the ground. Asking for the chunk makes sure the ground exists.
        Direction dir = region.direction();
        BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX() + dir.getStepX() * DISTANCE;
        int z = spawn.getZ() + dir.getStepZ() * DISTANCE;
        level.getChunk(x >> 4, z >> 4);
        BlockPos riftPos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);

        // The picture: where the rift is, which way is "ahead" (the way the player walks out) and which way is "to the right".
        Vec3 floor = Vec3.atBottomCenterOf(riftPos);
        Vec3 centre = floor.add(0.0, 1.3, 0.0);
        Vec3 ahead = new Vec3(dir.getStepX(), 0.0, dir.getStepZ());
        Vec3 side = new Vec3(-dir.getStepZ(), 0.0, dir.getStepX());
        Vec3 behind = floor.subtract(ahead.scale(1.4));       // where the player waits, hidden, behind the rift
        Vec3 landing = floor.add(ahead.scale(1.9));           // where they stop, three steps out
        Vec3 chest = landing.add(0.0, 1.0, 0.0);

        // The player: sent to the place behind the rift, invisible, facing the way they will walk.
        player.teleportTo(level, behind.x, behind.y, behind.z, dir.toYRot(), 0.0F);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 400, 0, false, false, false));

        // The camera. First it waits, in front of the rift and a little to the left, for the picture to fade up (100 ticks).
        // Then it flies along one long curve to the right, bulging away from the rift, while it turns from the rift towards the player.
        Vec3 start = centre.add(ahead.scale(7.5)).add(side.scale(-3.5)).add(0.0, 0.4, 0.0);
        Vec3 end = centre.add(ahead.scale(7.5)).add(side.scale(6.0)).add(0.0, 0.6, 0.0);
        Vec3 bulge = ahead.scale(2.5);
        Cutscene.shot(player, start, start, centre, centre, 100, false, false);
        Cutscene.curve(player, start, start.add(bulge), end.add(bulge), end, centre, chest, 306, true, true);

        // The script.
        // Two seconds of black first, then the card: it types for one second, waits one, the name fades in over two, stays three, and both fade out over two.
        at(server, player, 40, () -> Cutscene.title(player, Component.translatable("scene.mistvale_rpg.somewhere_in"), region.displayName(), 20, 20, 40, 60));
        at(server, player, 220, () -> RiftBlock.spawn(level, riftPos, dir, RiftVariant.BLUE, null, 400, true));

        // The player comes out of the rift: visible again, a burst of light, and thirty ticks of walking, three steps.
        at(server, player, 266, () -> {
            player.removeEffect(MobEffects.INVISIBILITY);
            level.playSound(null, riftPos, ModSounds.WARP_ARRIVE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, 30, 0.6, 0.9, 0.6, 0.08);
        });
        for (int i = 0; i < 30; i++) {
            at(server, player, 266 + i, () -> walk(player, ahead, 0.11));   // one push every tick
        }
        at(server, player, 302, () -> {
            if (level.getBlockEntity(riftPos) instanceof RiftBlockEntity rift) {
                rift.close();
            }
        });

        // The player looks to the left, then to the right. While they look right, the Spirit appears ahead of them, farther from the rift.
        at(server, player, 315, () -> Cutscene.look(player, -50.0F, 16));
        at(server, player, 338, () -> Cutscene.look(player, 100.0F, 22));
        // Then they look back to the front, where the Spirit stands, and finish turning just before the cut.
        at(server, player, FILM_LENGTH - 26, () -> Cutscene.look(player, -50.0F, 20));
        AtomicReference<SpiritEntity> spirit = new AtomicReference<>();
        at(server, player, 348, () -> {
            Vec3 where = landing.add(ahead.scale(3.0));
            spirit.set(SpiritEntity.appearNear(level, BlockPos.containing(where.x, landing.y + 1.0, where.z)));
        });

        // The cut: the film camera is gone, the player's own view is back over the shoulder, and the Spirit welcomes them to their land.
        at(server, player, FILM_LENGTH - 4, () -> {
            Cutscene.endShots(player);
            Cutscene.bars(player, false);
            Cutscene.shoulderView(player, true);
            DialogueManager.start(player, DialogueScenes.WELCOME, "hello_" + region.id(), spirit.get());
            finishAfterTalk(player);
        });
    }

    // Waits until the welcome is over, then gives the player their own camera back.
    private static void finishAfterTalk(ServerPlayer player) {
        if (player.hasDisconnected()) {
            BUSY_UNTIL.remove(player.getUUID());
            return;
        }
        if (DialogueManager.isTalking(player)) {
            Later.after(player.server, 10, () -> finishAfterTalk(player));
            return;
        }
        Cutscene.keepShoulderView(player);   // the film ends in the shoulder view, and the player stays in it
        BUSY_UNTIL.remove(player.getUUID());
    }

    // ---- The helpers every scene uses ----

    // Gives the player a push along {@code direction}, as strong as {@code speed} blocks per tick. Their own game then
    // walks them, legs and all, because it thinks they are simply moving.
    private static void walk(ServerPlayer player, Vec3 direction, double speed) {
        player.setDeltaMovement(direction.x * speed, player.getDeltaMovement().y, direction.z * speed);
        player.hurtMarked = true;   // "tell the player's game about this push"
    }

    // Says "a scene is starting" and keeps a second one from starting on top of it. The player is made safe for as long as it runs.
    private static boolean begin(ServerPlayer player, int length) {
        long now = player.server.getTickCount();
        if (BUSY_UNTIL.getOrDefault(player.getUUID(), 0L) > now) {
            return false;
        }
        BUSY_UNTIL.put(player.getUUID(), now + length + 10);
        // Resistance level 5 (the number counts from 0) takes away all damage: nothing can hurt a player who cannot move.
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, length + 20, 4, false, false, false));
        return true;
    }

    // "At this moment, do this." Like Later.after, but it does nothing if the player has left the game in the meantime.
    private static void at(MinecraftServer server, ServerPlayer player, int tick, Runnable action) {
        Later.after(server, tick, () -> {
            if (!player.hasDisconnected()) {
                action.run();
            }
        });
    }

    private SpawnScene() {
    }
}
