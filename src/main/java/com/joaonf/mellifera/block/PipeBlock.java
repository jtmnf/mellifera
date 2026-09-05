package com.joaonf.mellifera.block;

import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaBlocks;
import com.joaonf.mellifera.registry.MelliferaFluids;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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

    /// What a pipe found on one side, and -- where it found a machine -- which way that joint
    /// works.
    ///
    /// The Cable needs no such thing: power only ever moves one way through it, from whatever made
    /// it to whatever spends it. Fluid does not. A Tank is both a place honey comes from and a place
    /// honey goes, and no rule about capabilities can tell which one a player meant. So the joint
    /// carries the answer, it starts at whichever direction the machine actually supports, and a
    /// right-click on that joint turns it round.
    public enum Connection implements StringRepresentable {
        NONE("none"),
        /// Another pipe: the run carries straight on, no collar.
        PIPE("pipe"),
        /// A machine the pipe draws out of. Brass collar.
        DRAW("draw"),
        /// A machine the pipe feeds into. Steel collar, so a glance down a run says which end is
        /// which.
        FEED("feed");

        private final String name;

        Connection(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /// Whether this is a joint with a machine at all, as opposed to nothing or more pipe.
        public boolean isMachine() {
            return this == DRAW || this == FEED;
        }

        public Connection opposite() {
            return this == DRAW ? FEED : DRAW;
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

    /// Every pipe ticks, and a pipe with nothing to draw from returns on its first line.
    ///
    /// This used to hand out a ticker only to pipes whose state said they touched a machine, which
    /// is cheaper and rests on Vanilla re-asking for the ticker whenever the state changes. It does
    /// -- LevelChunk.setBlockState calls updateBlockEntityTicker on the existing block entity -- but
    /// resting a whole feature on that while the feature does not work is how a bug hides behind an
    /// optimisation. The check moved into the tick, where it can be read.
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, MelliferaBlockEntities.PIPE.get(), PipeBlockEntity::serverTick);
    }

    /// Whether any joint on this pipe is one worth drawing from.
    public static boolean draws(BlockState state) {
        for (EnumProperty<Connection> property : BY_DIRECTION.values()) {
            if (state.getValue(property) == Connection.DRAW) {
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

    /// One neighbour changed, so one joint is reconsidered -- but a joint a player has turned round
    /// keeps the way they turned it.
    ///
    /// Without that last part the direction would be a suggestion rather than a setting: place a
    /// torch beside a pipe drawing from a Tank and the block update would quietly put it back to
    /// feeding, which is the kind of bug nobody reports because nobody believes it. The default is
    /// only ever chosen when there was no machine on that side a moment ago.
    @Override
    protected BlockState updateShape(
        BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
        Direction toNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random
    ) {
        Connection current = state.getValue(BY_DIRECTION.get(toNeighbour));
        Connection found = connection(level, pos, toNeighbour);

        if (current.isMachine() && found.isMachine()) {
            return state;
        }

        return state.setValue(BY_DIRECTION.get(toNeighbour), found);
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

        if (!(reader instanceof Level level)) {
            // Worldgen, or a structure being placed: there is no way to ask, and the joint is
            // corrected the first time a real block update reaches it.
            return Connection.FEED;
        }

        ResourceHandler<FluidResource> tank =
            level.getCapability(Capabilities.Fluid.BLOCK, neighbour, side.getOpposite());
        if (tank == null) {
            return Connection.NONE;
        }

        return accepts(tank) ? Connection.FEED : Connection.DRAW;
    }

    /// Whether this tank will take honey from outside, asked by offering it a millibucket inside a
    /// transaction nobody commits.
    ///
    /// It is the only honest way to ask. A ResourceHandler has no flag saying which way it faces --
    /// TankAccess is this mod's own wrapper and other mods have their own -- so the question that
    /// gets a true answer everywhere is the question the pipe would ask anyway, asked once with the
    /// result thrown away.
    ///
    /// Refusing to be filled is what makes a machine a source: the Squeezer takes nothing and so
    /// starts as DRAW, the Carpenter and the Engine take honey and so start as FEED, and a Tank
    /// takes it too, which is why it starts as FEED and is the one a player most often turns round.
    private static boolean accepts(ResourceHandler<FluidResource> tank) {
        FluidResource honey = FluidResource.of(MelliferaFluids.HONEY.get());

        try (Transaction probe = Transaction.open(null)) {
            return tank.insert(honey, 1, probe) > 0;
        }
    }

    /// Turns one joint round, if the machine on the other side supports being used the other way.
    ///
    /// The joint clicked is worked out from where the click landed rather than from the face it hit:
    /// an arm is a rod, and the face of it a player sees is at right angles to the direction that
    /// arm points. The axis the click is furthest along is the arm they were pointing at.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction side = armAt(state, pos, hit);
        if (side == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Connection wanted = state.getValue(BY_DIRECTION.get(side)).opposite();
        ResourceHandler<FluidResource> tank =
            level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(side), side.getOpposite());

        if (tank == null || (wanted == Connection.FEED && !accepts(tank))) {
            // Nothing to turn round: a machine that will not be filled cannot be fed, whatever the
            // joint says.
            say(player, "gui.mellifera.pipe.refused");
            return InteractionResult.CONSUME;
        }

        level.setBlock(pos, state.setValue(BY_DIRECTION.get(side), wanted), Block.UPDATE_ALL);
        say(player, wanted == Connection.DRAW ? "gui.mellifera.pipe.draw" : "gui.mellifera.pipe.feed");

        return InteractionResult.SUCCESS;
    }

    private static void say(Player player, String key) {
        if (player instanceof ServerPlayer server) {
            server.sendSystemMessage(Component.translatable(key), true);
        }
    }

    /// Which arm the click landed on, or null if it landed on the middle of the block or on an arm
    /// that goes nowhere.
    private static @Nullable Direction armAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(Vec3.atCenterOf(pos));

        Direction best = null;
        double reach = 0.0;

        for (Map.Entry<Direction, EnumProperty<Connection>> entry : BY_DIRECTION.entrySet()) {
            if (!state.getValue(entry.getValue()).isMachine()) {
                continue;
            }

            Direction side = entry.getKey();
            double along = local.x * side.getStepX() + local.y * side.getStepY() + local.z * side.getStepZ();
            if (along > reach) {
                reach = along;
                best = side;
            }
        }

        return best;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
