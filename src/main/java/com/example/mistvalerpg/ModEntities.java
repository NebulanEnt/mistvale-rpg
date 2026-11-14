package com.example.mistvalerpg;

import com.example.mistvalerpg.spirit.SpiritEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every living thing the mod adds.
 */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MistvaleRpg.MODID);

    /** The Spirit of Mistvale, the guide who walks you through the story. */
    public static final DeferredHolder<EntityType<?>, EntityType<SpiritEntity>> SPIRIT = ENTITIES.register("spirit",
            () -> EntityType.Builder.of(SpiritEntity::new, MobCategory.MISC)
                    .sized(0.9F, 1.9F)
                    .eyeHeight(1.5F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .fireImmune()
                    .build("spirit"));

    // Every mob needs a list of "attributes" (health, speed, ...) before it can exist in the world.
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SPIRIT.get(), Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).build());
    }

    private ModEntities() {
    }
}