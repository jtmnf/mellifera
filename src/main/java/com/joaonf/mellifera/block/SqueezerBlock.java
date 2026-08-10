package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaFluids;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/// Presses honey drops into liquid honey. The same shape as CentrifugeBlock, down to WORKING carrying
/// whether the machine is running -- block states replicate to the client for free, a block entity's
/// progress counter does not, and that is what lets the model and the particles react without a
/// bespoke packet.
public class SqueezerBlock extends BaseEntityBlock {
    public static final MapCodec<SqueezerBlock> CODEC = simpleCodec(SqueezerBlock::new);

    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public SqueezerBlock(Properties properties) {
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
        return new SqueezerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, MelliferaBlockEntities.SQUEEZER.get(), SqueezerBlockEntity::serverTick);
    }

    /// A bucket in hand taps the tank instead of opening the window.
    ///
    /// The bucket slots inside are for automation -- a hopper can feed them, and that is what they are
    /// for. A player already holding a bucket should not have to open a screen, put it in a slot, wait,
    /// and take it out of another; every other tank in the modded world hands you honey for a click.
    ///
    /// Refuses rather than part-fills: under 1000 mB this falls through to opening the window, so a
    /// click on a tank with 700 in it shows you the 700 rather than silently doing nothing.
    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (!stack.is(Items.BUCKET)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (!(level.getBlockEntity(pos) instanceof SqueezerBlockEntity squeezer)
            || squeezer.tank().stored() < SqueezerBlockEntity.BUCKET_MB) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!squeezer.drawOffBucket()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        // createFilledResult, not a hand-rolled swap: it is what every vanilla bucket uses, and it is
        // the only thing that gets a creative player and a stack of buckets both right.
        player.setItemInHand(hand, ItemUtils.createFilledResult(
            stack, player, new ItemStack(MelliferaFluids.HONEY_BUCKET.get())));
        player.awardStat(Stats.ITEM_USED.get(Items.BUCKET));
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);

        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof SqueezerBlockEntity squeezer) {
            player.openMenu(squeezer, pos);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    /// A press, not a spin: a slow creak and honey beading under the plate rather than the Centrifuge's
    /// splash. Same cadence, which is a handful of calls a second across every machine in view.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) {
            return;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        if (random.nextDouble() < 0.25) {
            level.playLocalSound(x, y + 0.5, z, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.16F, 0.6F, false);
        }

        // Under the block rather than off its sides: what leaves a press goes downward.
        level.addParticle(ParticleTypes.DRIPPING_HONEY,
            x + random.nextDouble() * 0.6 - 0.3,
            y + 0.05,
            z + random.nextDouble() * 0.6 - 0.3,
            0.0, 0.0, 0.0);
    }
}
