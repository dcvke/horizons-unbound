package com.horizonsunbound.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** 1.21 white smoke uses the same motion and sprites as 1.20 smoke, with a fixed tint. */
public final class ThawSmokeProvider implements ParticleProvider<SimpleParticleType> {
    private final SmokeParticle.Provider smoke;
    public ThawSmokeProvider(SpriteSet sprites) { smoke = new SmokeParticle.Provider(sprites); }
    public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        Particle particle = smoke.createParticle(type, level, x, y, z, vx, vy, vz);
        if (particle != null) particle.setColor(0.7294118F, 0.69411767F, 0.7607843F);
        return particle;
    }
}
