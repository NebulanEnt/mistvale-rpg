package com.example.mistvalerpg.soul;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A freed soul. Anything breaks it, and it drops itself still holding its soul.
 * Nine different ones laid as a 3x3 platform will open the Corrupted Rift.
 */
public class ChargedSoulBlock extends Block {

    public ChargedSoulBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SoulId.PROPERTY, SoulId.HELVAR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SoulId.PROPERTY);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.displayClientMessage(
                    Component.translatable("message.mistvale_rpg.charged_soul.waiting", state.getValue(SoulId.PROPERTY).displayName()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // Motes in the soul's own colour drift up from the block.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            Vec3 rgb = Vec3.fromRGB24(state.getValue(SoulId.PROPERTY).color());
            level.addParticle(new DustParticleOptions(rgb.toVector3f(), 0.9F),
                    pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 1.05, pos.getZ() + 0.15 + random.nextDouble() * 0.7,
                    0.0, 0.03, 0.0);
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return SoulBlockItem.withSoul(asItem(), state.getValue(SoulId.PROPERTY));
    }
}