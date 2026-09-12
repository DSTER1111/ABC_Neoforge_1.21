package com.a_c_e.bettercircuits.block.entity;

import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.block.BCBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BCBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BetterCircuits.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneCableBlockEntity>> REDSTONE_CABLE =
            BLOCK_ENTITY_TYPES.register("redstone_cable", () -> BlockEntityType.Builder.of(
                    RedstoneCableBlockEntity::new, BCBlocks.REDSTONE_CABLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TimerBlockEntity>> TIMER =
            BLOCK_ENTITY_TYPES.register("timer", () -> BlockEntityType.Builder.of(
                    TimerBlockEntity::new, BCBlocks.TIMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightweightTimerBlockEntity>> LIGHTWEIGHT_TIMER =
            BLOCK_ENTITY_TYPES.register("lightweight_timer", () -> BlockEntityType.Builder.of(
                    LightweightTimerBlockEntity::new, BCBlocks.LIGHTWEIGHT_TIMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightweightComparatorBlockEntity>> LIGHTWEIGHT_COMPARATOR =
            BLOCK_ENTITY_TYPES.register("lightweight_comparator", () -> BlockEntityType.Builder.of(
                    LightweightComparatorBlockEntity::new, BCBlocks.LIGHTWEIGHT_COMPARATOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RainDetectorBlockEntity>> RAIN_DETECTOR =
            BLOCK_ENTITY_TYPES.register("rain_detector", () -> BlockEntityType.Builder.of(
                    RainDetectorBlockEntity::new, BCBlocks.RAIN_DETECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeatDetectorBlockEntity>> HEAT_DETECTOR =
            BLOCK_ENTITY_TYPES.register("heat_detector", () -> BlockEntityType.Builder.of(
                    HeatDetectorBlockEntity::new, BCBlocks.HEAT_DETECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlowerBlockEntity>> BLOWER =
            BLOCK_ENTITY_TYPES.register("blower", () -> BlockEntityType.Builder.of(
                    BlowerBlockEntity::new, BCBlocks.BLOWER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VacuumBlockEntity>> VACUUM =
            BLOCK_ENTITY_TYPES.register("vacuum", () -> BlockEntityType.Builder.of(
                    VacuumBlockEntity::new, BCBlocks.VACUUM.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneThresholdBlockEntity>> REDSTONE_THRESHOLD =
            BLOCK_ENTITY_TYPES.register("redstone_threshold", () -> BlockEntityType.Builder.of(
                    RedstoneThresholdBlockEntity::new, BCBlocks.REDSTONE_THRESHOLD.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightweightRedstoneThresholdBlockEntity>> LIGHTWEIGHT_REDSTONE_THRESHOLD =
            BLOCK_ENTITY_TYPES.register("lightweight_redstone_threshold", () -> BlockEntityType.Builder.of(
                    LightweightRedstoneThresholdBlockEntity::new, BCBlocks.LIGHTWEIGHT_REDSTONE_THRESHOLD.get()).build(null));

    //Bug fix: a dedicated FILTERED_HOPPER type here would be a "phantom" registration nothing in the running
    //game ever actually matches against - HopperBlockEntity's own 2-arg constructor hardcodes vanilla's real
    //BlockEntityType.HOPPER internally, with no protected/3-arg constructor a subclass could use to supply a
    //different one. Every FilteredHopperBlockEntity instance's own getType() therefore always reports
    //BlockEntityType.HOPPER regardless of what's registered here - see FilteredHopperBlock.getTicker and
    //FilteredHopperBlockEntityRenderer's own comments, both keyed off BlockEntityType.HOPPER directly instead.
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
