package com.example.mistvalerpg.soul;

import com.example.mistvalerpg.ModBlockEntities;
import com.example.mistvalerpg.MistvaleRpg;
import com.mojang.serialization.MapCodec;

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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A boss's soul locked in a cage. It cannot be broken or moved.
 * Each Soul Shard fed to it weakens the cage; the third one breaks it open
 * (the cage plays its breaking animation) and the block turns into a Charged Soul Block.
 *
 * The cage is drawn by GeckoLib as an animated 3D model, so it needs a block entity
 * (CagedSoulBlockEntity) to play the animations.
 */
public class CagedSoulBlock extends BaseEntityBlock {
    public static final MapCodec<CagedSoulBlock> CODEC = simpleCodec(CagedSoulBlock::new);
    public static final int SHARDS_NEEDED = 3;
    /** How many shards the cage has eaten so far: 0, 1 or 2. The third shard frees the soul. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, SHARDS_NEEDED - 1);

    // The area you can point at and bump into: a little slimmer than a full block.
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

    public CagedSoulBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SoulId.PROPERTY, SoulId.HELVAR).setValue(CHARGE, 0));
    }

    // Every block with a block entity must say how it is saved. This one is built from its properties.
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    // Tells Minecraft which properties this block has.
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SoulId.PROPERTY, CHARGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // "ENTITYBLOCK_ANIMATED" means: don't draw a normal block model, the block entity's renderer draws it.
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CagedSoulBlockEntity(pos, state);
    }

    // Only the server needs to count down the breaking animation.
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.CAGED_SOUL.get(), CagedSoulBlockEntity::serverTick);
    }

    // Right-click with an item in hand.
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(MistvaleRpg.SOUL_SHARD.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel serverLevel && feed(serverLevel, pos, state, player) && !player.getAbilities().instabuild) {
            stack.shrink(1); // creative mode keeps the shard
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

    /** One shard goes in. Server side only. Returns true if the shard was used up. */
    public boolean feed(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (!(level.getBlockEntity(pos) instanceof CagedSoulBlockEntity cage) || cage.isBreaking()) {
            return false; // the cage is already breaking, keep the shard
        }
        int charge = state.getValue(CHARGE) + 1;

        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.35, 0.35, 0.35, 0.02);

        if (charge < SHARDS_NEEDED) {
            level.setBlock(pos, state.setValue(CHARGE, charge), Block.UPDATE_ALL);
            cage.triggerAnim("main", charge == 1 ? "charge_1" : "charge_2"); // plays once, then settles into the new stage
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 0.8F + 0.2F * charge);
            player.displayClientMessage(Component.translatable("message.mistvale_rpg.caged_soul.fed", charge, SHARDS_NEEDED), true);
        } else {
            cage.startBreaking(); // the block entity finishes the job once the animation is over
        }
        return true;
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