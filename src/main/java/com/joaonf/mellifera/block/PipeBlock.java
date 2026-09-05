package com.joaonf.mellifera.block;

import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaBlocks;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/// Carries liquid honey between the machines that make it and the ones that spend it -- see
/// PipeBlockEntity for what actually moves, which is nothing this block is holding.
///
/// Built like the Cable, down to the three connection states and the brass collar, because they are
/// the same object for two different things and a player should not have to learn each of them. Two
/// differences, and both are on this class:
///
/// FLOWING, which is what puts the liquid on screen. The glass is empty when nothing is going
/// through and carries the colour of what is when something is; that is a block state because it has
/// to reach every client in render distance, and the colour beside it is a block entity field
/// because there are more fluids than a state may hold.
///
/// A TICKER, but only sometimes. A pipe has to pull -- nothing in the mod pushes fluid -- and the
/// only pipes that can pull are the ones clamped to a machine. getTicker is handed the block state,
/// so a pipe in the middle of a run is given no ticker at all and costs exactly what the Cable
/// costs, which is nothing.
public class PipeBlock extends BaseEntityBlock {
    public static final MapCodec<PipeBlock> CODEC = simpleCodec(PipeBlock::new);

    /// What a pipe found on one side. The Cable's own three, for the same reasons -- see
    /// CableBlock.Connection.
    public enum Connection implements StringRepresentable {
        NONE("none"),
        /// Another pipe: the run carries straight on, no collar.
        PIPE("pipe"),
        /// A tank or a machine that holds fluid: the collar goes on, and this is a side worth
        /// drawing from.
        PLUG("plug");

        private final String name;

        Connection(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Connection> NORTH = EnumProperty.create("north", Connection.class);
    public static final EnumProperty<Connection> EAST = EnumProperty.create("east", Connection.class);
    public static final EnumProperty<Connection> SOUTH = EnumProperty.create("south", Connection.class);
    public static final EnumProperty<Connection> WEST = EnumProperty.create("west", Connection.class);
    public static final EnumProperty<Connection> UP = EnumProperty.create("up", Connection.class);
    public static final EnumProperty<Connection> DOWN = EnumProperty.create("down", Connection.class);

    /// Whether anything is going through right now. See the class note.
    public static final BooleanProperty FLOWING = BooleanProperty.create("flowing");

    private static final Map<Direction, EnumProperty<Connection>> BY_DIRECTION = Map.of(
        Direction.NORTH, NORTH,
        Direction.EAST, EAST,
        Direction.SOUTH, SOUTH,
        Direction.WEST, WEST,
        Direction.UP, UP,
        Direction.DOWN, DOWN);

    /// Six pixels, the Cable's own thickness: two runs side by side have to look like a pair.
    private static final float THICKNESS = 6.0F;

    private final Function<BlockState, VoxelShape> shapes;

    public PipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(NORTH, Connection.NONE)
            .setValue(EAST, Connection.NONE)
            .setValue(SOUTH, Connection.NONE)
            .setValue(WEST, Connection.NONE)
            .setValue(UP, Connection.NONE)
            .setValue(DOWN, Connection.NONE)
            .setValue(FLOWING, false));
        this.shapes = makeShapes();
    }

    public static EnumProperty<Connection> propertyFor(Direction side) {
        return BY_DIRECTION.get(side);
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        VoxelShape core = Block.cube(THICKNESS);
        Map<Direction, VoxelShape> arms = Shapes.rotateAll(Block.boxZ(THICKNESS, 0.0, 8.0));

        return getShapeForEachState(state -> {
            VoxelShape shape = core;
            for (Map.Entry<Direction, EnumProperty<Connection>> entry : BY_DIRECTION.entrySet()) {
                if (state.getValue(entry.getValue()) != Connection.NONE) {
                    shape = Shapes.or(shape, arms.get(entry.getKey()));
                }
            }

            return shape;
        }, FLOWING);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, FLOWING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    /// Only the pipes that touch something worth drawing from tick, which the block state already
    /// knows. A long run costs one ticking block entity at each end and nothing in between.
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !touchesMachine(state)) {
            return null;
        }

        return createTickerHelper(type, MelliferaBlockEntities.PIPE.get(), PipeBlockEntity::serverTick);
    }

    private static boolean touchesMachine(BlockState state) {
        for (EnumProperty<Connection> property : BY_DIRECTION.values()) {
            if (state.getValue(property) == Connection.PLUG) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, EnumProperty<Connection>> entry : BY_DIRECTION.entrySet()) {
            state = state.setValue(entry.getValue(),
                connection(context.getLevel(), context.getClickedPos(), entry.getKey()));
        }

        return state;
    }

    @Override
    protected BlockState updateShape(
        BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
        Direction toNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random
    ) {
        return state.setValue(BY_DIRECTION.get(toNeighbour), connection(level, pos, toNeighbour));
    }

    /// What there is that side, and so what the arm should look like and whether it is worth
    /// drawing from. The fluid capability is the test, exactly as the Cable asks for the energy one
    /// -- and with the same fallback where only a LevelReader is available. See CableBlock.connects.
    private static Connection connection(LevelReader reader, BlockPos pos, Direction side) {
        BlockPos neighbour = pos.relative(side);
        BlockState state = reader.getBlockState(neighbour);

        if (state.is(MelliferaBlocks.PIPE.get())) {
            return Connection.PIPE;
        }

        if (!state.hasBlockEntity()) {
            return Connection.NONE;
        }

        if (reader instanceof Level level
            && level.getCapability(Capabilities.Fluid.BLOCK, neighbour, side.getOpposite()) == null) {
            return Connection.NONE;
        }

        return Connection.PLUG;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
