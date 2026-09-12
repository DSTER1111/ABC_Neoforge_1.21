package com.a_c_e.bettercircuits.entity;

import com.a_c_e.bettercircuits.BetterCircuits;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BCEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BetterCircuits.MOD_ID);

    //Filtered Hopper Minecart (see FilteredHopperMinecartEntity) - same size/tracking range vanilla's own
    //EntityType.HOPPER_MINECART uses (every minecart type shares this footprint).
    public static final DeferredHolder<EntityType<?>, EntityType<FilteredHopperMinecartEntity>> FILTERED_HOPPER_MINECART =
            ENTITY_TYPES.register("filtered_hopper_minecart",
                    () -> EntityType.Builder.<FilteredHopperMinecartEntity>of(FilteredHopperMinecartEntity::new, MobCategory.MISC)
                            .sized(0.98f, 0.7f)
                            .clientTrackingRange(8)
                            .build("filtered_hopper_minecart"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
