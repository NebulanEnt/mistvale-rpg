package com.example.mistvalerpg.story;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

/**
 * The four lands a new player can head for from the beach. The choice is remembered on the player
 * (see ModAttachments.REGION) so later chapters can send them to the right place.
 */
public enum Region {
    NORTH("north"),
    WEST("west"),
    SOUTH("south"),
    EAST("east");

    private final String id;

    Region(String id) {
        this.id = id;
    }

    /** The short name saved on the player: "north", "west", "south" or "east". */
    public String id() {
        return id;
    }

    /** "North Marr Lands", "The Wild West", ... in the player's language. */
    public Component displayName() {
        return Component.translatable("region.mistvale_rpg." + id);
    }

    /** Finds a region from its saved name, or returns null for anything else (including the empty "nothing chosen yet"). */
    @Nullable
    public static Region byId(String id) {
        return Arrays.stream(values()).filter(region -> region.id.equals(id)).findFirst().orElse(null);
    }
}
