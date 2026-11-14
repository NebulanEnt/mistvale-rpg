package com.example.mistvalerpg.soul;

import java.util.List;

import com.example.mistvalerpg.ModEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The Soul Crystal in your hand. Use it on a block and a floating crystal appears in the empty cell next to it.
 * Put it in the crafting grid to turn it back into a Charged Soul Block (see the recipe files).
 * The soul travels with the item in the same "block_state" note the soul blocks use.
 */
public class SoulCrystalItem extends Item {

    public SoulCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(pos).canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        // One crystal per cell.
        if (!level.getEntitiesOfClass(SoulCrystalEntity.class, new AABB(pos).deflate(0.01)).isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            ItemStack stack = context.getItemInHand();
            SoulCrystalEntity crystal = ModEntities.SOUL_CRYSTAL.get().create(serverLevel);
            if (crystal == null) {
                return InteractionResult.FAIL;
            }
            crystal.setSoul(SoulBlockItem.soulOf(stack));
            crystal.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            serverLevel.addFreshEntity(crystal);
            serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                stack.shrink(1);
            }
            // Like a Charged Soul Block, a new crystal may complete the nine-soul platform.
            SoulPlatform.onChargedPlaced(serverLevel, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mistvale_rpg.soul_of", SoulBlockItem.soulOf(stack).displayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.mistvale_rpg.soul_crystal.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
