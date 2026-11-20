package com.example.mistvalerpg.prologue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.mistvalerpg.MistvaleRpg;

import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The hour of the day in the prologue sea, chosen per player: almost night on the ship, morning on the beach.
 *
 * A dimension's clock belongs to the overworld, and a "fixed_time" in the dimension file cannot be changed while the game runs.
 * So the sea has no fixed time, and instead this class tells the one player's game what time it is, with the game's own
 * "set the time" message. The message is sent often, because the game itself keeps sending the overworld's time.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID)
public final class PrologueTime {
    /** Almost night (the sun is just gone). */
    public static final long NIGHT = 12000L;
    /** A morning sun, low in the sky. */
    public static final long MORNING = 2000L;

    private static final int GRACE_TICKS = 100;   // a player who has just been moved is allowed this long to arrive in the sea

    private static final Map<UUID, long[]> TIMES = new HashMap<>();   // player -> { hour of the day, server tick when it was set }

    /** From now on this player sees this hour in the sea. */
    public static void set(ServerPlayer player, long dayTime) {
        TIMES.put(player.getUUID(), new long[] {dayTime, player.server.getTickCount()});
        send(player, dayTime);
    }

    /** The player sees the natural time again. */
    public static void clear(ServerPlayer player) {
        if (TIMES.remove(player.getUUID()) != null) {
            ServerLevel overworld = player.server.overworld();
            player.connection.send(new ClientboundSetTimePacket(overworld.getGameTime(), overworld.getDayTime(), true));
        }
    }

    // "false" at the end: this clock does not run by itself, it stays at the hour we give.
    private static void send(ServerPlayer player, long dayTime) {
        player.connection.send(new ClientboundSetTimePacket(player.level().getGameTime(), dayTime, false));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || TIMES.isEmpty()) {
            return;
        }
        long[] entry = TIMES.get(player.getUUID());
        if (entry == null) {
            return;
        }
        if (PrologueSea.isInSea(player)) {
            if (player.tickCount % 2 == 0) {
                send(player, entry[0]);
            }
        } else if (player.server.getTickCount() - entry[1] > GRACE_TICKS) {
            clear(player);   // the player left the sea (for example with a command): the natural time comes back
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TIMES.remove(event.getEntity().getUUID());
    }

    private PrologueTime() {
    }
}
