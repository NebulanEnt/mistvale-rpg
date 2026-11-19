package com.example.mistvalerpg.story;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * The four lands a new player can head for from the beach. The choice is remembered on the player
 * (see ModAttachments.REGION) so later chapters can send them to the right place.
 * Every land lies in one direction from the world spawn: that direction is also the way a player walks out of the rift.
 */
public enum Region {
    NORTH("north", Direction.NORTH),
    WEST("west", Direction.WEST),
    SOUTH("south", Direction.SOUTH),
    EAST("east", Direction.EAST);

    private final String id;
    private final Direction direction;

    Region(String id, Direction direction) {
        this.id = id;
        this.direction = direction;
    }

    /** Which way from the world spawn this land lies. */
    public Direction direction() {
        return direction;
    }

    /** The short name saved on the player: "north", "west", "south" or "east". */
    public String id() {
        return id;
    }

    /** "North Marr Lands", "The Wild West", "South Side Shores" or "The Hollows", in the player's language. */
    public Component displayName() {
        return Component.translatable("region.mistvale_rpg." + id);
    }

    /** Finds a region from its saved name, or returns null for anything else (including the empty "nothing chosen yet"). */
    @Nullable
    public static Region byId(String id) {
        return Arrays.stream(values()).filter(region -> region.id.equals(id)).findFirst().orElse(null);
    }
}
