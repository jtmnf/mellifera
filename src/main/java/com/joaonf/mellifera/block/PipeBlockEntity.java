package com.joaonf.mellifera.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/// A length of pipe: the Cable's twin, for fluid.
///
/// SAME SHAPE AS THE CABLE, and deliberately -- a player who has run one should be able to run the
/// other without learning anything new. It holds nothing; what is pushed into any part of a run is
/// handed straight on to every tank that run reaches, breadth-first, inside the caller's own
/// transaction. Its three connection states are the same, and it wears the same brass collar where
/// it clamps onto a machine.
///
/// ONE REAL DIFFERENCE: IT HAS TO PULL. The Engine pushes power at its neighbours, so a cable can be
/// a pure conductor and never tick. Nothing in the mod pushes fluid -- the Squeezer fills its own
/// tank and waits -- so a pipe that only forwarded would sit there while the machine beside it
/// backed up. So a pipe that touches a machine ticks, and on each tick it tries to draw from what it
/// is touching into the rest of its run. A pipe touching only other pipes never ticks at all: see
/// PipeBlock.getTicker, which decides that from the block state.
///
/// WHAT YOU SEE. Still nothing stored -- but the glass shows what is *moving*, which is the honest
/// thing for a pipe to show and the useful one: a run with liquid in it is a run that is working.
/// The last fluid to pass through is kept for its colour, and FLOWING on the block state is what
/// puts it on screen; both are cleared by the same tick that finds nothing left to move.
public class PipeBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    /// How far a single hop is allowed to travel looking for somewhere to put what it is carrying.
    /// The Cable's own figure, and for the same reason -- see CableBlockEntity.
    private static final int MAX_NODES = 256;

    /// Millibuckets a ticking pipe draws per tick. A bucket every second, which keeps up with a
    /// Squeezer running on full power and is slow enough that a tank empties visibly.
    public static final int TRANSFER_MB = 50;

    private final Conduit conduit = new Conduit();

    /// The last fluid to pass through, kept only so the glass has something to be the colour of.
    /// Never an amount: a pipe stores nothing, and a pipe that stored one millibucket would be a
    /// tank with a bad shape.
    private FluidResource carried = FluidResource.EMPTY;

    /// The tank a draw is currently coming out of, so the search does not offer that tank its own
    /// fluid back. Without it a pipe on a full tank moves a bucket from the tank into the tank
    /// every tick, reports that it moved something, and shows as flowing forever.
    private @Nullable BlockPos drawingFrom;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.PIPE.get(), pos, state);
    }

    public ResourceHandler<FluidResource> conduit() {
        return conduit;
    }

    /// What is going through, for the tint on the glass. Empty when nothing is.
    public FluidResource carried() {
        return carried;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PipeBlockEntity pipe) {
        if (MachineSignal.switchedOff(level, pos)) {
            pipe.stopFlowing(level);
            return;
        }

        ResourceStack<FluidResource> moved = pipe.draw(level, pos, state);
        if (moved == null) {
            pipe.stopFlowing(level);
            return;
        }

        pipe.showFlowing(level, moved.resource());
    }

    /// Draws from each machine this pipe is clamped to on a DRAW joint, into the rest of the run.
    ///
    /// Stops at the first side that gives something. A pipe emptying two tanks at once would be
    /// twice as fast as one emptying either, which makes throughput depend on how a bench is laid
    /// out rather than on how much pipe was built.
    private @Nullable ResourceStack<FluidResource> draw(Level level, BlockPos pos, BlockState state) {
        for (Direction side : Direction.values()) {
            if (state.getValue(PipeBlock.propertyFor(side)) != PipeBlock.Connection.DRAW) {
                continue;
            }

            BlockPos neighbour = pos.relative(side);
            ResourceHandler<FluidResource> source =
                level.getCapability(Capabilities.Fluid.BLOCK, neighbour, side.getOpposite());
            if (source == null) {
                continue;
            }

            drawingFrom = neighbour;
            try {
                ResourceStack<FluidResource> moved =
                    ResourceHandlerUtil.moveFirst(source, conduit, resource -> true, TRANSFER_MB, null);
                if (moved != null && moved.amount() > 0) {
                    return moved;
                }
            } finally {
                drawingFrom = null;
            }
        }

        return null;
    }

    /// Every tank reachable from this pipe, in the order the search found them.
    ///
    /// Breadth-first over pipes only: a pipe is a step on the way and never a destination, which is
    /// what keeps a hop from handing fluid back to the pipe that offered it. The tank currently
    /// being drawn from is skipped for the same reason.
    private List<ResourceHandler<FluidResource>> reachableTanks() {
        if (level == null) {
            return List.of();
        }

        List<ResourceHandler<FluidResource>> tanks = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        seen.add(worldPosition);
        queue.add(worldPosition);

        while (!queue.isEmpty() && seen.size() <= MAX_NODES) {
            BlockPos pipe = queue.removeFirst();

            for (Direction side : Direction.values()) {
                BlockPos neighbour = pipe.relative(side);
                if (!seen.add(neighbour)) {
                    continue;
                }

                if (level.getBlockEntity(neighbour) instanceof PipeBlockEntity) {
                    queue.addLast(neighbour);
                    continue;
                }

                if (neighbour.equals(drawingFrom)) {
                    continue;
                }

                // Only the joints a player has pointed at this machine. A pipe that delivered into
                // everything it touched would empty a Tank into the Squeezer it was draining, and
                // no arrangement of blocks could stop it.
                BlockState state = level.getBlockState(pipe);
                if (!state.is(getBlockState().getBlock())
                    || state.getValue(PipeBlock.propertyFor(side)) != PipeBlock.Connection.FEED) {
                    continue;
                }

                ResourceHandler<FluidResource> tank =
                    level.getCapability(Capabilities.Fluid.BLOCK, neighbour, side.getOpposite());
                if (tank != null) {
                    tanks.add(tank);
                }
            }
        }

        return tanks;
    }

    /// Every pipe in this run, so what is flowing can be shown along the whole of it.
    private List<BlockPos> run() {
        if (level == null) {
            return List.of();
        }

        List<BlockPos> pipes = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        seen.add(worldPosition);
        queue.add(worldPosition);
        pipes.add(worldPosition);

        while (!queue.isEmpty() && pipes.size() <= MAX_NODES) {
            BlockPos pipe = queue.removeFirst();

            for (Direction side : Direction.values()) {
                BlockPos neighbour = pipe.relative(side);
                if (seen.add(neighbour) && level.getBlockEntity(neighbour) instanceof PipeBlockEntity) {
                    queue.addLast(neighbour);
                    pipes.add(neighbour);
                }
            }
        }

        return pipes;
    }

    /// Lights the whole run with what is going through it.
    ///
    /// The state is only written where it differs, so a pipe that has been carrying honey for an
    /// hour is not sending a block update a tick -- the walk is cheap, the writes are what cost, and
    /// there are none while nothing changes.
    private void showFlowing(Level level, FluidResource resource) {
        for (BlockPos pos : run()) {
            if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                pipe.setFlow(level, resource, true);
            }
        }
    }

    private void stopFlowing(Level level) {
        if (!getBlockState().getValue(PipeBlock.FLOWING)) {
            // Already dry, so there is nothing to walk: this is the branch a pipe on an empty tank
            // takes every tick, and it has to cost nothing.
            return;
        }

        for (BlockPos pos : run()) {
            if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                pipe.setFlow(level, FluidResource.EMPTY, false);
            }
        }
    }

    private void setFlow(Level level, FluidResource resource, boolean flowing) {
        if (!carried.equals(resource)) {
            carried = resource;
            setChanged();
            // The colour lives in the block entity, so it needs the packet the block state gets for
            // free. Without this a pipe would start flowing in the right place in the wrong colour.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }

        BlockState state = getBlockState();
        if (state.getValue(PipeBlock.FLOWING) != flowing) {
            level.setBlock(worldPosition, state.setValue(PipeBlock.FLOWING, flowing), Block.UPDATE_ALL);
        }
    }

    /// The handler the pipe exposes on all six faces: a conduit, exactly as the Cable's is.
    ///
    /// One slot holding nothing, of no capacity. What is inserted is passed on to the tanks behind
    /// this pipe and never kept, and nothing can be extracted, because there is nothing here to
    /// extract -- what a pipe has is always somebody else's.
    private class Conduit implements ResourceHandler<FluidResource> {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0L;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return 0L;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return true;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return insert(resource, amount, transaction);
        }

        /// Hands the whole offer on to the tanks behind this pipe.
        ///
        /// Two passes, the Cable's own: an equal share first, so a run feeding four tanks fills them
        /// evenly rather than filling the nearest, then the remainder to whoever can still take it.
        /// The sub-inserts join the caller's transaction, which is what makes a hop atomic -- a
        /// rolled back push takes every tank it touched back with it.
        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            if (amount <= 0 || resource.isEmpty()) {
                return 0;
            }

            List<ResourceHandler<FluidResource>> tanks = reachableTanks();
            if (tanks.isEmpty()) {
                return 0;
            }

            int moved = 0;

            int share = amount / tanks.size();
            if (share > 0) {
                for (ResourceHandler<FluidResource> tank : tanks) {
                    moved += tank.insert(resource, share, transaction);
                }
            }

            for (ResourceHandler<FluidResource> tank : tanks) {
                if (moved >= amount) {
                    break;
                }

                moved += tank.insert(resource, amount - moved, transaction);
            }

            return moved;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public String toString() {
            return "Mellifera pipe at " + worldPosition;
        }
    }

    // -- persistence and sync ----------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        carried = FluidResource.of(input.read("carried", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("carried", FluidStack.OPTIONAL_CODEC, carried.toStack(1));
    }

    /// The colour of what is going through has to reach the client, and a block entity says nothing
    /// on its own. Same pair of overrides the Tank uses: one for the change, one for the block
    /// coming into view.
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(output);
            return output.buildResult();
        }
    }
}
