package com.a_c_e.bettercircuits.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

//Identical to vanilla's FlameParticle.Provider, just enlarged - the same pattern vanilla itself uses to shrink
//the flame for candles (FlameParticle.SmallFlameProvider), just scaling up instead of down. Needed because the
//aluminum flame sprite renders smaller than vanilla's flame at the same quad size.
public class AluminumFlameParticleProvider implements ParticleProvider<SimpleParticleType> {
    private static final float SCALE = 1.5f;

    private final SpriteSet sprites;

    public AluminumFlameParticleProvider(SpriteSet sprites) {
        this.sprites = sprites;
    }

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        AluminumFlameParticle particle = new AluminumFlameParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
        particle.pickSprite(sprites);
        particle.scale(SCALE);
        return particle;
    }

    private static class AluminumFlameParticle extends FlameParticle {
        protected AluminumFlameParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
