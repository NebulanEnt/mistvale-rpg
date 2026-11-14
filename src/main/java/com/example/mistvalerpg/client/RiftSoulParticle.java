package com.example.mistvalerpg.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A tiny soul wisp: drifts upward, sways side to side, fades in and out, and cycles through four pictures.
 */
public class RiftSoulParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float swayPhase;
    private final float swaySpeed;

    protected RiftSoulParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.lifetime = 50 + random.nextInt(40);
        this.quadSize = 0.07F + random.nextFloat() * 0.07F;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.friction = 0.97F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.swayPhase = random.nextFloat() * Mth.TWO_PI;
        this.swaySpeed = 0.12F + random.nextFloat() * 0.1F;
        this.alpha = 0.0F;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float wave = age * swaySpeed + swayPhase;
        xd += Mth.sin(wave) * 0.0016;
        zd += Mth.cos(wave) * 0.0016;
        yd += 0.0006;
        float life = (float) age / lifetime;
        alpha = Mth.clamp(Mth.sin(life * Mth.PI) * 1.6F, 0.0F, 1.0F); // fade in, hold, fade out
        setSpriteFromAge(sprites);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT; // glows
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Minecraft asks this factory to make one whenever the game spawns a rift_soul particle. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new RiftSoulParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}