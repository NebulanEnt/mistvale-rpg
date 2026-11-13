package com.example.mistvalerpg;

import java.util.function.UnaryOperator;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/**
 * The six MistvaleRPG rarities and the colour each one gives an item's name.
 * They are added to Minecraft's own Rarity list through META-INF/enumextensions.json,
 * so every mod that reads an item's rarity sees them.
 */
public final class ModRarities {
    public static final EnumProxy<Rarity> COMMON = rarity("common", style -> style.withColor(0xA8A8A8));
    public static final EnumProxy<Rarity> UNCOMMON = rarity("uncommon", style -> style.withColor(0x2ECC71));
    public static final EnumProxy<Rarity> RARE = rarity("rare", style -> style.withColor(0x4AA3FF));
    public static final EnumProxy<Rarity> EPIC = rarity("epic", style -> style.withColor(0xB06BFF));
    public static final EnumProxy<Rarity> LEGENDARY = rarity("legendary", style -> style.withColor(0xFFC533));
    // Mythic gets extra flair: red and bold.
    public static final EnumProxy<Rarity> MYTHIC = rarity("mythic", style -> style.withColor(0xFF3B3B).applyFormat(ChatFormatting.BOLD));

    private ModRarities() {
    }

    // -1 means "give it the next free number". The name is what shows up in data files.
    private static EnumProxy<Rarity> rarity(String name, UnaryOperator<Style> look) {
        return new EnumProxy<>(Rarity.class, -1, MistvaleRpg.MODID + ":" + name, look);
    }
}