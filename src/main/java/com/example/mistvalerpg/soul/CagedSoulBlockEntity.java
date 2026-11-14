package com.example.mistvalerpg.soul;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The brain of a Caged Soul Block. The soul and the charge already live in the block's state,
 * so this only has to do two things: play the right animation, and run the breaking sequence
 * (the cage shatters over 28 ticks, then the block becomes a Charged Soul Block).
 */
public class CagedSoulBlockEntity extends BlockEntity implements GeoBlockEntity {
    /** How long the "break free" animation lasts, in ticks (20 ticks = 1 second). */
    public static final int BREAK_TICKS = 28;

    // GeckoLib keeps its animation bookkeeping in this cache.
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean breaking = false;
    private int breakTimer = 0;

    public CagedSoulBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAGED_SOUL.get(), pos, state);
    }

    public int charge() {
        return getBlockState().getValue(CagedSoulBlock.CHARGE);
    }

    public SoulId soul() {
        return getBlockState().getValue(SoulId.PROPERTY);
    }

    public boolean isBreaking() {
        return breaking;
    }

    /** Called by the block when the third shard goes in. */
    public void startBreaking() {
        if (level == null || level.isClientSide() || breaking) {
            return;
        }
        breaking = true;
        breakTimer = BREAK_TICKS;
        triggerAnim("main", "break_free");
        level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.2F, 1.25F);
        level.playSound(null, worldPosition, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
        sync();
    }

    // Runs every tick on the server.
    public static void serverTick(Level level, BlockPos pos, BlockState state, CagedSoulBlockEntity cage) {
        if (!cage.breaking || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (cage.breakTimer == 20) {
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
        }
        if (cage.breakTimer % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.5, 0.5, 0.5, 0.05);
        }
        if (--cage.breakTimer > 0) {
            return;
        }

        // The animation is over: swap the cage for the freed soul.
        SoulId soul = cage.soul();
        BlockState freed = MistvaleRpg.CHARGED_SOUL_BLOCK.get().defaultBlockState().setValue(SoulId.PROPERTY, soul);
        serverLevel.setBlock(pos, freed, Block.UPDATE_ALL);
        serverLevel.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.08);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4F, 0.6F);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.5F, 1.0F);
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(8.0))) {
            player.displayClientMessage(Component.translatable("message.mistvale_rpg.caged_soul.freed", soul.displayName()), false);
        }
    }

    // Send the new state to every player who can see the block.
    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ---- Animation ----
    // One "controller" named "main" decides which animation plays. The animation names
    // (stage_0, charge_1, break_free, ...) are the ones inside caged_soul.animation.json.
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (breaking) {
                return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("break_free"));
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop("stage_" + charge()));
        })
                .triggerableAnim("charge_1", RawAnimation.begin().thenPlay("charge_1"))
                .triggerableAnim("charge_2", RawAnimation.begin().thenPlay("charge_2"))
                .triggerableAnim("break_free", RawAnimation.begin().thenPlayAndHold("break_free")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ---- Saving, and telling the client ----
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Breaking", breaking);
        tag.putInt("BreakTimer", breakTimer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        breaking = tag.getBoolean("Breaking");
        breakTimer = tag.getInt("BreakTimer");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}