package com.example.mistvalerpg.soul;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/**
 * The item form of both soul blocks. The soul (and the charge) travel with the item
 * in Minecraft's own "block_state" note, so placing it puts the same soul back in the world.
 */
public class SoulBlockItem extends BlockItem {

    public SoulBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** A stack of this soul block holding the given soul. */
    public static ItemStack withSoul(ItemLike item, SoulId soul) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(SoulId.PROPERTY, soul));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlockItemStateProperties saved = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);

        SoulId soul = saved.get(SoulId.PROPERTY);
        if (soul == null) {
            soul = SoulId.HELVAR; // a plain /give has no note: it places the default soul
        }
        tooltip.add(Component.translatable("tooltip.mistvale_rpg.soul_of", soul.displayName()).withStyle(ChatFormatting.GRAY));

        if (getBlock() instanceof CagedSoulBlock) {
            Integer charge = saved.get(CagedSoulBlock.CHARGE);
            tooltip.add(Component.translatable("tooltip.mistvale_rpg.caged_soul.charge", charge == null ? 0 : charge, CagedSoulBlock.SHARDS_NEEDED)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}