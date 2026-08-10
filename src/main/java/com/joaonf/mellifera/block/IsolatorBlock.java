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

/// Same shape as CentrifugeBlock -- a BaseEntityBlock whose only job is to open its block
/// entity's menu and tick it server-side.
///
/// WORKING mirrors CentrifugeBlock.WORKING, and matters more here: the isolator stalls
/// silently whenever the bottles run out or the outputs fill up, so without an outward sign
/// a stuck machine is indistinguishable from a slow one.
public class IsolatorBlock extends BaseEntityBlock {
    public static final MapCodec<IsolatorBlock> CODEC = simpleCodec(IsolatorBlock::new);

    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public IsolatorBlock(Properties properties) {
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
        return new IsolatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, MelliferaBlockEntities.ISOLATOR.get(), IsolatorBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof IsolatorBlockEntity isolator) {
            player.openMenu(isolator, pos);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    /// See CentrifugeBlock.animateTick for the cadence: SCRAPE rather than a spark because
    /// it is the one long-lived vanilla glow particle already tinted the isolator's own
    /// teal, so the motes look like they came out of the lens on top of the machine.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) {
            return;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.05;
        double z = pos.getZ() + 0.5;

        if (random.nextDouble() < 0.3) {
            level.playLocalSound(x, y, z, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.2F, 1.4F, false);
        }

        for (int i = 0; i < 2; i++) {
            // Sixteenths of a block: the lens is the middle six pixels of the top face.
            level.addParticle(ParticleTypes.SCRAPE,
                x + (random.nextDouble() - 0.5) * 0.35,
                y,
                z + (random.nextDouble() - 0.5) * 0.35,
                0.0, 3.0, 0.0);
        }
    }
}
