package com.example.mistvalerpg.prologue;

import java.util.Set;
import java.util.TreeSet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The list of private seas that are taken. Every new player gets a sea of their own, so that two friends who start at the
 * same time never fight on the same ship. A sea is simply a number (0, 1, 2...), and number N is the same sea as number 0,
 * moved N x 4096 blocks to the east.
 *
 * "SavedData" is a small file the game keeps with the world (in the "data" folder). It is loaded when the world opens and
 * written again when the world is saved.
 */
public final class SeaSlots extends SavedData {
    /** How many private seas there can be. The sea is 4096 blocks wide, so a thousand of them fit well inside the world. */
    public static final int MAX = 1000;

    private static final String NAME = "mistvale_rpg_sea_slots";
    private static final String KEY = "taken";

    private final Set<Integer> taken = new TreeSet<>();

    /** The list of the world: loaded if the file exists, created empty if not. */
    public static SeaSlots get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SeaSlots::new, SeaSlots::load, null), NAME);
    }

    private static SeaSlots load(CompoundTag tag, HolderLookup.Provider registries) {
        SeaSlots slots = new SeaSlots();
        for (int number : tag.getIntArray(KEY)) {
            slots.taken.add(number);
        }
        return slots;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY, new IntArrayTag(taken.stream().mapToInt(Integer::intValue).toArray()));
        return tag;
    }

    /** Takes the lowest free sea. If all of them are taken, the first one is shared. */
    public int claim() {
        for (int number = 0; number < MAX; number++) {
            if (!taken.contains(number)) {
                taken.add(number);
                setDirty();   // "this changed: write it down the next time the world is saved"
                return number;
            }
        }
        return 0;
    }

    /** Gives a sea back, so the next new player can have it. */
    public void release(int number) {
        if (taken.remove(number)) {
            setDirty();
        }
    }
}