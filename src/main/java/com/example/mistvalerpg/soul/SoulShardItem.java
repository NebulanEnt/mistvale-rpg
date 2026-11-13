package com.example.mistvalerpg.soul;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A splinter of a restless soul. Three of them break the cage of a Caged Soul Block. */
public class SoulShardItem extends Item {

    public SoulShardItem(Properties properties) {
        super(properties);
    }

    // Extra grey lines under the item's name when you hover over it.
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mistvale_rpg.soul_shard").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.mistvale_rpg.soul_shard.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}