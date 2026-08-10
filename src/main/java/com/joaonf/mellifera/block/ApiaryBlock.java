package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/// One apiary, or several stacked into one hive.
///
/// The column is discovered rather than declared: there is no "form the multiblock" step and
/// no controller item. Put an apiary on top of another and they are one hive; break the top
/// one and the rest carry on. A structure a player can build by accident and take apart
/// without ceremony is worth more here than one that has to be assembled correctly, because
/// the thing being built is a stack of boxes and everyone already knows how those work.
public class ApiaryBlock extends BaseEntityBlock {
    public static final MapCodec<ApiaryBlock> CODEC = simpleCodec(ApiaryBlock::new);

    /// Where this block sits in its column, so the texture can close the seams.
    public enum Part implements StringRepresentable {
        SINGLE("single"),
        BOTTOM("bottom"),
        MIDDLE("middle"),
        TOP("top");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public ApiaryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.SINGLE));
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    /// The variant is decided at placement, not a tick later.
    ///
    /// updateShape only fires on a block when a *neighbour* changes, so a newly placed one is
    /// never told about itself: dropping an apiary on top of another placed it as SINGLE, with
    /// a lid and a base of its own, and it stayed that way until something else in the column
    /// happened to change. Reading the column here means the seam closes the instant the block
    /// lands.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(PART, partAt(context.getLevel(), context.getClickedPos()));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ApiaryBlockEntity(pos, state);
    }

    // Breeding/production is simulated entirely server-side, and there is nothing left for a
    // client ticker to do: the ambient bees are driven by their own renderer, on the render
    // thread, from the synced activity flag (see HiveBeeRenderer).
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, MelliferaBlockEntities.APIARY.get(), ApiaryBlockEntity::serverTick);
    }

    /// True for the block that owns the hive: the bottom of its column.
    public static boolean isController(BlockGetter level, BlockPos pos) {
        return !isApiary(level, pos.below());
    }

    /// Walks down to the controller from anywhere in the column.
    public static BlockPos controllerOf(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        while (isApiary(level, cursor.below())) {
            cursor.move(Direction.DOWN);
        }

        return cursor.immutable();
    }

    private static boolean isApiary(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ApiaryBlock;
    }

    /// Any block of the column opens the hive, and the hive is always the controller's.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos controller = controllerOf(level, pos);
        if (level.getBlockEntity(controller) instanceof ApiaryBlockEntity apiary) {
            // The height rides along with the position: the menu lays its slots out in its
            // constructor, before any ContainerData has synced, so it has to arrive here.
            player.openMenu(apiary, buffer -> {
                buffer.writeBlockPos(controller);
                buffer.writeByte(apiary.levels());
            });
        }

        return InteractionResult.CONSUME;
    }

    /// Recomputes the whole column whenever any part of it changes.
    ///
    /// Driven off neighbour updates rather than a tick, because a column only ever changes
    /// when a block is placed or broken, and both of those already send one. Walking the
    /// column is at most three block lookups.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        // Only vertical neighbours can change what part of a column this is.
        if (directionToNeighbour.getAxis() != Direction.Axis.Y) {
            return state;
        }

        return state.setValue(PART, partAt(level, pos));
    }

    /// Recomputes the column's height when a block joins it.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshColumn(level, pos);
    }

    /// Which piece of the column this position is.
    private static Part partAt(BlockGetter level, BlockPos pos) {
        boolean below = isApiary(level, pos.below());
        boolean above = isApiary(level, pos.above());

        if (below && above) {
            return Part.MIDDLE;
        }
        if (below) {
            return Part.TOP;
        }
        if (above) {
            return Part.BOTTOM;
        }

        return Part.SINGLE;
    }

    /// Tells the controller how tall it is now. Called after any change to the column.
    public static void refreshColumn(LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof Level level)) {
            return;
        }

        BlockPos controller = controllerOf(level, pos);
        if (!(level.getBlockEntity(controller) instanceof ApiaryBlockEntity apiary)) {
            return;
        }

        int levels = 1;
        BlockPos.MutableBlockPos cursor = controller.mutable().move(Direction.UP);
        while (levels < ApiaryBlockEntity.MAX_LEVELS && isApiary(level, cursor)) {
            levels++;
            cursor.move(Direction.UP);
        }

        apiary.setLevels(level, levels);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
        // The block below just became the top of a shorter tower, or a lone apiary again.
        refreshColumn(level, pos.below());
    }
}
