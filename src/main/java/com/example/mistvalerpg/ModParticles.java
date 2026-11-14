package com.example.mistvalerpg;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Our own particle types. How they look is described in assets/mistvale_rpg/particles/.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MistvaleRpg.MODID);

    /** A small soul wisp that drifts up out of an open rift. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_SOUL =
            PARTICLES.register("rift_soul", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}