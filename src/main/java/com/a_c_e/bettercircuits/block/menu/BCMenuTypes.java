package com.a_c_e.bettercircuits.block.menu;

import com.a_c_e.bettercircuits.BetterCircuits;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BCMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, BetterCircuits.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<TimerMenu>> TIMER =
            MENU_TYPES.register("timer", () -> new MenuType<>(TimerMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<RedstoneThresholdMenu>> REDSTONE_THRESHOLD =
            MENU_TYPES.register("redstone_threshold", () -> new MenuType<>(RedstoneThresholdMenu::new, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
