package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/// Banks Forge Energy so a bench that works in bursts can spend what a hive made overnight -- see
/// CapacitorBlockEntity.
///
/// CHARGE is on the block state rather than only in the block entity, because it is what the model
/// shows: five steps of lit cells up the face. States replicate to every client in render distance
/// for free, and a block entity's field does not, so this is the difference between a bank of
/// capacitors a player can read across a room and one they have to click on.
public class CapacitorBlock extends BaseEntityBlock {
    public static final MapCodec<CapacitorBlock> CODEC = simpleCodec(CapacitorBlock::new);

    /// How full it looks: 0 for empty, then four steps up to full.
    public static final int MAX_CHARGE = 4;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, MAX_CHARGE);

    public CapacitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    /// Which of the five steps a given charge sits on.
    ///
    /// Anything at all shows one lit cell, the same step off zero a comparator makes: a capacitor
    /// holding a thousand FE must not look identical to an empty one, or the block cannot be used
    /// to tell at a glance whether an engine behind it ever ran.
    public static int chargeStep(int stored) {
        if (stored <= 0) {
            return 0;
        }

        return Math.max(1, Math.min(MAX_CHARGE, stored * MAX_CHARGE / MachineBattery.CAPACITY));
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CapacitorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, MelliferaBlockEntities.CAPACITOR.get(), CapacitorBlockEntity::serverTick);
    }

    /// No window, so a right-click says the one thing there is to say.
    ///
    /// The alternative was a screen with a single bar on it, which is a screen that exists to be
    /// closed again. The number is in the action bar, where a player can read it without losing
    /// sight of the machines it is feeding.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof CapacitorBlockEntity capacitor && player instanceof ServerPlayer server) {
            // The action bar rather than chat: it is a reading, not a conversation, and it should
            // fade the way a reading does.
            server.sendSystemMessage(Component.translatable("gui.mellifera.energy",
                capacitor.energy().stored(), MachineBattery.CAPACITY), true);
        }

        return InteractionResult.CONSUME;
    }

    /// A comparator reads how full it is -- the one number this block has. See MachineSignal.
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CapacitorBlockEntity capacitor ? capacitor.comparatorSignal() : 0;
    }
}
