package com.joaonf.mellifera.registry;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.ApiaryBlockEntity;
import com.joaonf.mellifera.block.CarpenterBlockEntity;
import com.joaonf.mellifera.block.CentrifugeBlockEntity;
import com.joaonf.mellifera.block.SqueezerBlockEntity;
import com.joaonf.mellifera.block.InfuserBlockEntity;
import com.joaonf.mellifera.block.IsolatorBlockEntity;
import com.joaonf.mellifera.block.TankBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MelliferaBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mellifera.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ApiaryBlockEntity>> APIARY =
        TYPES.register("apiary", () -> new BlockEntityType<>(ApiaryBlockEntity::new, MelliferaBlocks.APIARY.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE =
        TYPES.register("centrifuge", () -> new BlockEntityType<>(CentrifugeBlockEntity::new, MelliferaBlocks.CENTRIFUGE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SqueezerBlockEntity>> SQUEEZER =
        TYPES.register("squeezer", () -> new BlockEntityType<>(SqueezerBlockEntity::new, MelliferaBlocks.SQUEEZER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TankBlockEntity>> TANK =
        TYPES.register("tank", () -> new BlockEntityType<>(TankBlockEntity::new, MelliferaBlocks.TANK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarpenterBlockEntity>> CARPENTER =
        TYPES.register("carpenter", () -> new BlockEntityType<>(CarpenterBlockEntity::new, MelliferaBlocks.CARPENTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IsolatorBlockEntity>> ISOLATOR =
        TYPES.register("isolator", () -> new BlockEntityType<>(IsolatorBlockEntity::new, MelliferaBlocks.ISOLATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfuserBlockEntity>> INFUSER =
        TYPES.register("infuser", () -> new BlockEntityType<>(InfuserBlockEntity::new, MelliferaBlocks.INFUSER.get()));

    private MelliferaBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
