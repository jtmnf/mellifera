package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/// Turns combs into their products. Deliberately the same shape as ApiaryBlock -- a
/// BaseEntityBlock whose only job is to open its block entity's menu and tick it
/// server-side.
///
/// WORKING is the furnace's LIT: an idle and a spinning centrifuge used to be
/// indistinguishable from the outside, so whether a comb is being processed lives in the
/// block state rather than only in the block entity. That is what lets the model swap to the
/// animated variant and the client run particles without a bespoke sync packet -- block
/// states are already replicated, the block entity's progress counter is not.
public class CentrifugeBlock extends BaseEntityBlock {
    public static final MapCodec<CentrifugeBlock> CODEC = simpleCodec(CentrifugeBlock::new);

    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public CentrifugeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WORKING, false));
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WORKING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CentrifugeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, MelliferaBlockEntities.CENTRIFUGE.get(), CentrifugeBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof CentrifugeBlockEntity centrifuge) {
            player.openMenu(centrifuge, pos);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    /// Honey flung off the drum, and the drum itself.
    ///
    /// The client only calls this for a handful of random positions a tick, so one call is
    /// roughly every couple of seconds per machine -- deliberately not topped up with a
    /// client-side ticker. The continuous part of the animation is the texture; this is the
    /// occasional splash that says the texture is not just a differently-coloured block.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) {
            return;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        if (random.nextDouble() < 0.3) {
            // Pitched down and well below full volume: a row of these should read as a
            // workshop in the next room, not as a riot.
            level.playLocalSound(x, y + 0.5, z, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.18F, 0.55F, false);
        }

        // Just outside a side face, so the drip runs down the casing and lands on the floor
        // rather than immediately hitting the block's own top and vanishing.
        boolean alongX = random.nextBoolean();
        double near = random.nextBoolean() ? 0.52 : -0.52;
        double along = random.nextDouble() * 0.6 - 0.3;
        level.addParticle(ParticleTypes.FALLING_HONEY,
            x + (alongX ? near : along),
            y + 0.4 + random.nextDouble() * 0.4,
            z + (alongX ? along : near),
            0.0, 0.0, 0.0);
    }
}
