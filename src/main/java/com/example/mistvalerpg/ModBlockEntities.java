package com.example.mistvalerpg;

import com.example.mistvalerpg.rift.RiftBlockEntity;
import com.example.mistvalerpg.soul.CagedSoulBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A block entity is the "brain" that a block can carry: it remembers things and can tick.
 * Each kind needs its own entry here, tied to the block(s) that use it.
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MistvaleRpg.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CagedSoulBlockEntity>> CAGED_SOUL = BLOCK_ENTITIES.register("caged_soul",
            () -> BlockEntityType.Builder.of(CagedSoulBlockEntity::new, MistvaleRpg.CAGED_SOUL_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftBlockEntity>> RIFT = BLOCK_ENTITIES.register("rift",
            () -> BlockEntityType.Builder.of(RiftBlockEntity::new, MistvaleRpg.RIFT_BLOCK.get()).build(null));

    private ModBlockEntities() {
    }
}