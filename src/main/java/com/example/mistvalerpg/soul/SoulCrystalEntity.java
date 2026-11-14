package com.example.mistvalerpg.soul;

import com.example.mistvalerpg.MistvaleRpg;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Soul Crystal: a Charged Soul Block that floats in the air and turns, like an end crystal.
 * It stands for one cell of the world (its feet are at the bottom of that cell), so the soul platform
 * counts it exactly like a Charged Soul Block in the same cell. Hit it and it breaks into its item.
 */
public class SoulCrystalEntity extends Entity {
    // The soul is kept as a number (its place in the SoulId list) so the server can tell every player's game which one it is.
    private static final EntityDataAccessor<Integer> SOUL = SynchedEntityData.defineId(SoulCrystalEntity.class, EntityDataSerializers.INT);

    public SoulCrystalEntity(EntityType<? extends SoulCrystalEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SOUL, 0);
    }

    public SoulId soul() {
        SoulId[] all = SoulId.values();
        return all[Mth.clamp(this.entityData.get(SOUL), 0, all.length - 1)];
    }

    public void setSoul(SoulId soul) {
        this.entityData.set(SOUL, soul.ordinal());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("soul", soul().getSerializedName());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        String saved = tag.getString("soul");
        for (SoulId soul : SoulId.values()) {
            if (soul.getSerializedName().equals(saved)) {
                setSoul(soul);
            }
        }
    }

    /** The item this crystal turns back into, holding the same soul. */
    public ItemStack asItem() {
        return SoulBlockItem.withSoul(MistvaleRpg.SOUL_CRYSTAL_ITEM.get(), soul());
    }

    @Override
    public ItemStack getPickResult() {
        return asItem();
    }

    @Override
    public boolean isPickable() {
        return true; // something you can aim at and hit
    }

    @Override
    public boolean isNoGravity() {
        return true; // it floats
    }

    // Any hit breaks it into its item (creative players break it without a drop).
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (!level().isClientSide() && !isRemoved()) {
            boolean creative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
            if (!creative) {
                spawnAtLocation(asItem(), 0.5F);
            }
            level().playSound(null, getX(), getY() + 0.5, getZ(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.3F);
            if (level() instanceof ServerLevel serverLevel) {
                Vec3 rgb = Vec3.fromRGB24(soul().color());
                serverLevel.sendParticles(new DustParticleOptions(rgb.toVector3f(), 1.4F), getX(), getY() + 0.6, getZ(), 24, 0.3, 0.3, 0.3, 0.05);
            }
            discard();
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        // A few motes of the soul's colour drift up from it.
        if (level().isClientSide() && this.random.nextInt(6) == 0) {
            Vec3 rgb = Vec3.fromRGB24(soul().color());
            level().addParticle(new DustParticleOptions(rgb.toVector3f(), 0.8F),
                    getX() + (this.random.nextDouble() - 0.5) * 0.5, getY() + 0.9, getZ() + (this.random.nextDouble() - 0.5) * 0.5, 0.0, 0.03, 0.0);
        }
    }
}
