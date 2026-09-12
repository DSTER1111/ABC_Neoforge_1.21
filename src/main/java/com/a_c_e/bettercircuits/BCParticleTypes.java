package com.a_c_e.bettercircuits;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

//PARTICLE_TYPE registers after BLOCK, so a lazily-bound DeferredHolder isn't ready in time for block
//properties (TorchBlock needs the SimpleParticleType instance immediately at construction). Building the
//instance directly and registering that pre-built object avoids the ordering dependency entirely.
public class BCParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, BetterCircuits.MOD_ID);

    public static final SimpleParticleType ALUMINUM_FLAME = new SimpleParticleType(false);
    public static final SimpleParticleType BLOWER_WIND = new SimpleParticleType(false);

    static {
        PARTICLE_TYPES.register("aluminum_flame", () -> ALUMINUM_FLAME);
        PARTICLE_TYPES.register("blower_wind", () -> BLOWER_WIND);
    }

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}
