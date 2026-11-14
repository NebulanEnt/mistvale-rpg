package com.example.mistvalerpg.rift;

import org.jetbrains.annotations.Nullable;

import com.example.mistvalerpg.ModBlockEntities;
import com.example.mistvalerpg.MistvaleRpg;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rift: a tall tear in the air you can walk through. It has no hitbox at all, so it is the
 * block entity (RiftBlockEntity) that notices you crossing it, and a GeckoLib renderer that draws it.
 * It only has one property: which way it faces.
 */
public class RiftBlock extends BaseEntityBlock {
    public static final MapCodec<RiftBlock> CODEC = simpleCodec(RiftBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public RiftBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    // Nothing to point at and nothing to bump into: you walk straight through.
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiftBlockEntity(pos, state);
    }

    // The server runs the open/close timers and spots people crossing; the client only spawns particles.
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.RIFT.get(),
                level.isClientSide() ? RiftBlockEntity::clientTick : RiftBlockEntity::serverTick);
    }

    /**
     * Opens a rift at a position.
     *
     * @param destination where it leads (null for rifts that decide for themselves)
     * @param autoCloseTicks closes by itself after this many ticks (0 = stays until closed)
     * @param removeOnClose the block disappears once it has closed
     */
    @Nullable
    public static RiftBlockEntity spawn(ServerLevel level, BlockPos pos, Direction facing, RiftVariant variant,
                                        @Nullable GlobalPos destination, int autoCloseTicks, boolean removeOnClose) {
        level.setBlock(pos, MistvaleRpg.RIFT_BLOCK.get().defaultBlockState().setValue(FACING, facing), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof RiftBlockEntity rift) {
            rift.configure(variant, destination, autoCloseTicks, removeOnClose);
            rift.open();
            return rift;
        }
        return null;
    }
}