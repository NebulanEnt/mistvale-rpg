package com.example.mistvalerpg.soul;

import com.example.mistvalerpg.MistvaleRpg;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A boss's soul locked in a cage. It cannot be broken or moved.
 * Each Soul Shard fed to it weakens the cage; the third one frees the soul
 * and the block turns into a Charged Soul Block.
 */
public class CagedSoulBlock extends Block {
    public static final int SHARDS_NEEDED = 3;
    /** How many shards the cage has eaten so far: 0, 1 or 2. The third shard frees the soul. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, SHARDS_NEEDED - 1);

    public CagedSoulBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SoulId.PROPERTY, SoulId.HELVAR).setValue(CHARGE, 0));
    }

    // Tells Minecraft which properties this block has.
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SoulId.PROPERTY, CHARGE);
    }

    // Right-click with an item in hand.
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(MistvaleRpg.SOUL_SHARD.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel serverLevel) {
            feed(serverLevel, pos, state, player);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1); // creative mode keeps the shard
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    // Right-click with anything else, or an empty hand.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("message.mistvale_rpg.caged_soul.needs_shard"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** One shard goes in. Server side only. */
    public void feed(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        SoulId soul = state.getValue(SoulId.PROPERTY);
        int charge = state.getValue(CHARGE) + 1;

        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.35, 0.35, 0.35, 0.02);

        if (charge < SHARDS_NEEDED) {
            level.setBlock(pos, state.setValue(CHARGE, charge), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 0.8F + 0.2F * charge);
            player.displayClientMessage(Component.translatable("message.mistvale_rpg.caged_soul.fed", charge, SHARDS_NEEDED), true);
        } else {
            BlockState freed = MistvaleRpg.CHARGED_SOUL_BLOCK.get().defaultBlockState().setValue(SoulId.PROPERTY, soul);
            level.setBlock(pos, freed, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.5F, 1.0F);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.03);
            player.displayClientMessage(Component.translatable("message.mistvale_rpg.caged_soul.freed", soul.displayName()), false);
        }
    }

    // Runs on the client a few times a second for blocks near the player: a soul wisp now and then.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6 - state.getValue(CHARGE) * 2) == 0) {
            level.addParticle(ParticleTypes.SOUL,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.0, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    0.0, 0.03, 0.0);
        }
    }

    // Middle-click in creative: the picked item remembers the soul and the charge.
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY
                .with(SoulId.PROPERTY, state.getValue(SoulId.PROPERTY))
                .with(CHARGE, state.getValue(CHARGE)));
        return stack;
    }
}