package com.example.mistvalerpg.soul;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The nine souls of Mistvale. Each belongs to one boss and has its own colour.
 * A soul block remembers which of the nine it holds.
 */
public enum SoulId implements StringRepresentable {
    HELVAR("helvar", 0x8E6CF0, "block_factorys_bosses:underworld_knight"),
    IGNIS("ignis", 0xFF8A2A, "cataclysm:ignis"),
    SCYLLA("scylla", 0x34D3C0, "cataclysm:scylla"),
    NERAKYSS("nerakyss", 0x2F6FE8, "block_factorys_bosses:kraken"),
    SKOR("skor", 0xBFE8FF, "block_factorys_bosses:yeti"),
    ASHLORD("ashlord", 0xE8452E, "block_factorys_bosses:infernal_dragon"),
    SIROK("sirok", 0xE8C25A, "block_factorys_bosses:sandworm"),
    ENDER_DRAGON("ender_dragon", 0xD05CF0, "minecraft:ender_dragon"),
    APOSTLE("apostle", 0x8CE05A, "goety:apostle");

    /** The block-state property both soul blocks use: soul=helvar, soul=ignis, ... */
    public static final EnumProperty<SoulId> PROPERTY = EnumProperty.create("soul", SoulId.class);

    private final String id;
    private final int color;
    private final String bossEntity;

    SoulId(String id, int color, String bossEntity) {
        this.id = id;
        this.color = color;
        this.bossEntity = bossEntity;
    }

    /** The lowercase name used in block states and data files. */
    @Override
    public String getSerializedName() {
        return id;
    }

    /** The soul's colour as 0xRRGGBB. */
    public int color() {
        return color;
    }

    /** The id of the boss that leaves this soul behind, for example "cataclysm:ignis". */
    public String bossEntity() {
        return bossEntity;
    }

    /** The soul's name in the player's language, in the soul's colour. */
    public Component displayName() {
        return Component.translatable("soul.mistvale_rpg." + id).withColor(color);
    }
}