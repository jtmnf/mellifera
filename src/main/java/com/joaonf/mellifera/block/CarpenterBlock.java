package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaFluids;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
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

/// Makes frames out of honey, wax and time -- see CarpenterBlockEntity.
///
/// The same shape as SqueezerBlock, down to WORKING carrying whether the machine is running: block
/// states replicate to the client for free and a block entity's progress counter does not, which is
/// what lets the model and the particles react without a bespoke packet.
public class CarpenterBlock extends BaseEntityBlock {
    public static final MapCodec<CarpenterBlock> CODEC = simpleCodec(CarpenterBlock::new);

    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public CarpenterBlock(Properties properties) {
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
        return new CarpenterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, MelliferaBlockEntities.CARPENTER.get(), CarpenterBlockEntity::serverTick);
    }

    /// A honey bucket in hand pours into the tank.
    ///
    /// The Squeezer's bucket tap, run backwards, and for the same reason: a player already holding
    /// the fluid should not have to build a pipe to move one bucket of it. Anything else in hand
    /// falls through to opening the window.
    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (!stack.is(MelliferaFluids.HONEY_BUCKET.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof CarpenterBlockEntity carpenter) || !carpenter.acceptBucket()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        // createFilledResult, not a hand-rolled swap: it is what every vanilla bucket uses, and the
        // only thing that gets a creative player and a stack of buckets both right.
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
        player.awardStat(Stats.ITEM_USED.get(MelliferaFluids.HONEY_BUCKET.get()));
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);

        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof CarpenterBlockEntity carpenter) {
            player.openMenu(carpenter, pos);
        }

        return InteractionResult.CONSUME;
    }

    /// A comparator on this machine reads what it has made and not yet given away. See
    /// MachineSignal.
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CarpenterBlockEntity carpenter ? carpenter.comparatorSignal() : 0;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    /// Woodwork, not machinery: a saw stroke and sawdust off the front. Same cadence as the other
    /// machines, which is a handful of calls a second across every one in view.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) {
            return;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        if (random.nextDouble() < 0.2) {
            level.playLocalSound(x, y + 0.5, z, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.2F, 1.4F, false);
        }

        level.addParticle(ParticleTypes.FALLING_HONEY,
            x + random.nextDouble() * 0.6 - 0.3,
            y + 0.9,
            z + random.nextDouble() * 0.6 - 0.3,
            0.0, 0.0, 0.0);
    }
}
