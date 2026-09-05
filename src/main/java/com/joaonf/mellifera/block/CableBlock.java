package com.joaonf.mellifera.block;

import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBlocks;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/// Carries Forge Energy from an Engine to the machines that spend it -- see CableBlockEntity for
/// what actually moves, which is nothing that this block is holding.
///
/// The six booleans are Vanilla's own pipe properties, so the model is a multipart of one core and
/// six arms in exactly the way a fence or a chorus plant is. A cable reaches for a neighbour when
/// that neighbour will take power: another cable, a Mellifera machine, or any other mod's block that
/// answers to the energy capability. It does not reach for a wall, which is the whole of what the
/// shape has to say.
public class CableBlock extends BaseEntityBlock {
    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final Map<Direction, BooleanProperty> BY_DIRECTION = Map.of(
        Direction.NORTH, NORTH,
        Direction.EAST, EAST,
        Direction.SOUTH, SOUTH,
        Direction.WEST, WEST,
        Direction.UP, UP,
        Direction.DOWN, DOWN);

    /// Six pixels thick, which is the same core the model is drawn at. Thinner reads as a tripwire
    /// and thicker stops looking like a wire at all.
    private static final float THICKNESS = 6.0F;

    private final Function<BlockState, VoxelShape> shapes;

    public CableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(NORTH, false)
            .setValue(EAST, false)
            .setValue(SOUTH, false)
            .setValue(WEST, false)
            .setValue(UP, false)
            .setValue(DOWN, false));
        this.shapes = makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        VoxelShape core = Block.cube(THICKNESS);
        Map<Direction, VoxelShape> arms = Shapes.rotateAll(Block.boxZ(THICKNESS, 0.0, 8.0));

        return getShapeForEachState(state -> {
            VoxelShape shape = core;
            for (Map.Entry<Direction, BooleanProperty> entry : BY_DIRECTION.entrySet()) {
                if (state.getValue(entry.getValue())) {
                    shape = Shapes.or(shape, arms.get(entry.getKey()));
                }
            }

            return shape;
        });
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> entry : BY_DIRECTION.entrySet()) {
            state = state.setValue(entry.getValue(),
                connects(context.getLevel(), context.getClickedPos(), entry.getKey()));
        }

        return state;
    }

    /// One neighbour changed, so one arm is reconsidered. The other five are left alone, which is
    /// what makes this cheap enough to run on every block update around a long run of cable.
    @Override
    protected BlockState updateShape(
        BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
        Direction toNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random
    ) {
        return state.setValue(BY_DIRECTION.get(toNeighbour), connects(level, pos, toNeighbour));
    }

    /// Whether there is anything that side worth reaching for.
    ///
    /// Asking the capability is the honest test, and it is the one that gets another mod's machine
    /// right without this mod knowing anything about it. It needs a Level, though, and a shape
    /// update can arrive with only a LevelReader -- during worldgen, or inside a structure being
    /// placed. There the fallback is "does it have a block entity at all": too generous by a chest,
    /// and the arm is corrected the first time a real block update reaches it.
    private static boolean connects(LevelReader reader, BlockPos pos, Direction side) {
        BlockPos neighbour = pos.relative(side);
        BlockState state = reader.getBlockState(neighbour);

        if (state.is(MelliferaBlocks.CABLE.get())) {
            return true;
        }

        if (!state.hasBlockEntity()) {
            return false;
        }

        if (reader instanceof Level level) {
            return level.getCapability(Capabilities.Energy.BLOCK, neighbour, side.getOpposite()) != null;
        }

        return true;
    }

    /// A cable is scenery with a job: nothing about it should stop light or suffocate anything
    /// standing in it.
    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    /// Nothing ticks. See CableBlockEntity: a conductor has no state to advance, which is what makes
    /// a wall of these free. BaseEntityBlock already returns null here, and this says so on purpose --
    /// every other block in this package overrides it, and a reader should not have to check whether
    /// this one forgot.
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }
}
