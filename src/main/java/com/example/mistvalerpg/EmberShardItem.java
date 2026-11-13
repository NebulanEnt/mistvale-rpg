package com.example.mistvalerpg;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class EmberShardItem extends Item {

    public EmberShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Changing things is the server's job, so only do it when NOT on the client.
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("message.mistvale_rpg.shard_warm"), true);
            player.getCooldowns().addCooldown(this, 20); // 20 ticks = 1 second
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}