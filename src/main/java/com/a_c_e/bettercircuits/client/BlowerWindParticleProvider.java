package com.a_c_e.bettercircuits.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.GustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

//Vanilla's own GustParticle (used for ParticleTypes.GUST/SMALL_GUST) is a dead end for a moving particle in TWO
//separate ways, both confirmed by reading its actual decompiled source: its constructor never touches the
//xSpeed/ySpeed/zSpeed a spawner passes in, AND - the deeper problem - its own tick() override completely
//replaces Particle's base tick() (age/sprite bookkeeping only, no call to super.tick() at all), so it never once
//calls Particle.move(xd, yd, zd) regardless of what velocity ends up set on it. Calling setParticleSpeed alone
//(this class's first attempt) does nothing for exactly that second reason.
//
//This subclass therefore re-overrides tick() itself, reimplementing the same
//xo/yo/zo-snapshot -> move -> friction-decay sequence Particle.tick() itself does (verified against its source),
//alongside GustParticle's own age/lifetime/sprite bookkeeping - it just cannot chain to Particle.tick() directly
//since Java has no "grandparent super" call, and GustParticle's own `sprites` field is private to it. Otherwise
//identical to GustParticle.SmallProvider (same sprite, lifetime, render type, 0.15 scale). Registered under our
//own BCParticleTypes.BLOWER_WIND rather than overriding vanilla's own GUST/SMALL_GUST provider, so wind
//charges/breeze mobs elsewhere in the game are completely unaffected.
public class BlowerWindParticleProvider implements ParticleProvider<SimpleParticleType> {
    private final SpriteSet sprites;

    public BlowerWindParticleProvider(SpriteSet sprites) {
        this.sprites = sprites;
    }

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        Particle particle = new MovingGustParticle(level, x, y, z, sprites);
        particle.scale(0.15F);
        particle.setParticleSpeed(xSpeed, ySpeed, zSpeed);
        return particle;
    }

    private static class MovingGustParticle extends GustParticle {
        //Sprite reel plays through at 2x the particle's own lifetime - setSpriteFromAge (SpriteSet.get(age,
        //lifetime)) maps age/lifetime linearly onto the 7 small_gust frames, so multiplying the age fed into that
        //lookup (clamped to lifetime so it can't run past the last frame) advances the animation faster without
        //touching how long the particle actually lives or how far it travels.
        private static final float ANIMATION_SPEED = 2.0F;

        private final SpriteSet sprites;

        protected MovingGustParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            this.sprites = sprites;
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                this.remove();
            } else {
                this.move(this.xd, this.yd, this.zd);
                this.xd *= this.friction;
                this.yd *= this.friction;
                this.zd *= this.friction;
                int animationAge = Math.min((int) (this.age * ANIMATION_SPEED), this.lifetime);
                this.setSprite(this.sprites.get(animationAge, this.lifetime));
            }
        }
    }
}
