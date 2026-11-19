package com.example.mistvalerpg.prologue;

import com.example.mistvalerpg.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything about where a player's private sea is and how to get there and back.
 */
public final class PrologueSea {
    /** The player's sea number. The first time it is asked, a free sea is taken, and the player keeps it from then on. */
    public static int slot(ServerPlayer player) {
        int slot = player.getData(ModAttachments.SEA_SLOT);
        if (slot < 0) {
            slot = SeaSlots.get(player.server).claim();
            player.setData(ModAttachments.SEA_SLOT, slot);
        }
        return slot;
    }

    /** Gives the player's sea back. Called when the prologue is over. */
    public static void release(ServerPlayer player) {
        int slot = player.getData(ModAttachments.SEA_SLOT);
        if (slot >= 0) {
            SeaSlots.get(player.server).release(slot);
            player.setData(ModAttachments.SEA_SLOT, -1);
        }
    }

    /** The sea dimension, or null if the game does not have it (which means the mod's data is broken). */
    public static ServerLevel level(ServerPlayer player) {
        return player.server.getLevel(PrologueRegistry.SEA);
    }

    public static boolean isInSea(ServerPlayer player) {
        return player.level().dimension().equals(PrologueRegistry.SEA);
    }

    /** How far to the east this player's copy of the sea is moved. */
    public static int shift(int slot) {
        return slot * PrologueLayout.PERIOD;
    }

    /** The beach of the home island, in this player's copy of the sea: where they wake up after the fight. */
    public static BlockPos beach(int slot) {
        int[] beach = PrologueLayout.beach();
        int x = beach[0] + shift(slot);
        return new BlockPos(x, PrologueLayout.col(beach[0], beach[1]).top() + 1, beach[1]);
    }

    /** Sends the player to their beach. Asking for the chunk first makes sure the ground exists before they arrive. */
    public static boolean toBeach(ServerPlayer player) {
        ServerLevel sea = level(player);
        if (sea == null) {
            return false;
        }
        BlockPos beach = beach(slot(player));
        sea.getChunk(beach.getX() >> 4, beach.getZ() >> 4);
        player.teleportTo(sea, beach.getX() + 0.5, beach.getY(), beach.getZ() + 0.5, 0.0F, 0.0F);
        return true;
    }

    private PrologueSea() {
    }
}