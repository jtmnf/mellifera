package com.joaonf.mellifera.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/// A glass-walled tank. The machines' chassis with the panel taken out of it -- see the block
/// textures in tools/gen_tank_textures.py, and TankRenderer, which draws what is behind the glass.
///
/// The block itself does almost nothing: the fluid lives in TankBlockEntity, pipes reach it through
/// the capability registered in MelliferaCapabilities, and everything here is the two things a
/// player does by hand.
public class TankBlock extends BaseEntityBlock {
    public static final MapCodec<TankBlock> CODEC = simpleCodec(TankBlock::new);

    public TankBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankBlockEntity(pos, state);
    }

    /// A container in hand fills the tank or empties it, whichever way round it can go.
    ///
    /// FluidUtil does the whole of it, and deliberately: it is the same call every tank in the modded
    /// world makes, so a bucket, a bottle-like container from another mod and a creative player all
    /// behave here exactly as they do everywhere else. Doing it by hand is how a tank ends up
    /// accepting only vanilla buckets -- which is what the Squeezer's own bucket tap does, and it can,
    /// because it only ever holds this mod's honey.
    ///
    /// Nothing is decided on the client. The interaction moves fluid and swaps the held item, and both
    /// are the server's to do; SUCCESS is only there so the arm swings without a round trip.
    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (stack.isEmpty()) {
            // Falls through to the empty-hand path, which reports what is in the tank.
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection(), null)
            ? InteractionResult.SUCCESS
            : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /// An empty hand reads the gauge. The glass says roughly how full it is; this says exactly, and
    /// names the fluid -- which matters for the ones no resource pack can tell apart at a glance.
    ///
    /// Above the hotbar rather than in the chat log: it is a glance at a tank, not a message.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof TankBlockEntity tank) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        FluidStack contents = tank.contents();
        // The overlay flag: above the hotbar rather than in the chat log.
        serverPlayer.sendSystemMessage(contents.isEmpty()
            ? Component.translatable("block.mellifera.tank.empty")
            : Component.translatable("block.mellifera.tank.contents",
                contents.getHoverName(), contents.getAmount(), TankBlockEntity.CAPACITY),
            true);

        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof TankBlockEntity tank ? tank.comparatorSignal() : 0;
    }
}
