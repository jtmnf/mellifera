package com.joaonf.mellifera.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/// A length of cable: a conductor, not a container.
///
/// WHAT IT DOES. It holds no power at all. Something pushes Forge Energy into one end -- the Engine
/// pushes into whatever is beside it, and so does most other mods' generators -- and the cable hands
/// that push straight on to every machine reachable through the run of cable it belongs to, inside
/// the same transaction. What no machine takes is never accepted in the first place, so a full bank
/// of machines stalls the engine rather than filling the wire.
///
/// WHY NOT A BUFFER. The obvious cable carries a small buffer and shuffles it along one block a
/// tick. That gives a wire a length-dependent delay, a visible "charge" a player can lose by
/// breaking it, and two cables that push into each other for as long as they exist. A conductor has
/// none of that: energy is where it was or where it ended up, and a wire is never a place power sits.
/// It also means a cable needs no ticking block entity, which is the difference between a hundred
/// cables costing nothing and costing a hundred tick calls.
///
/// WHAT IT IS NOT. It does not pull. A generator that waits to be drained rather than pushing -- some
/// mods' do -- will not empty itself into this, and a machine wired to one of those still needs that
/// mod's own cable. Mellifera's own Engine pushes, so its own bench works.
public class CableBlockEntity extends BlockEntity {
    /// How far a single push is allowed to travel looking for somewhere to land.
    ///
    /// The search runs on every insert, which is once a tick per engine, so it is bounded on
    /// purpose. Two hundred and fifty-six cables is a bench, a room and the corridor between them;
    /// past that a player has built a grid, and a grid wants a mod that models one.
    private static final int MAX_NODES = 256;

    private final Conductor conductor = new Conductor();

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.CABLE.get(), pos, state);
    }

    public EnergyHandler conductor() {
        return conductor;
    }

    /// Every machine reachable from this cable, in the order the search found them.
    ///
    /// Breadth-first over cables only: a cable is a step on the way and never a destination, which
    /// is also what keeps the search from handing power back to the cable that offered it.
    private List<EnergyHandler> reachableSinks() {
        if (level == null) {
            return List.of();
        }

        List<EnergyHandler> sinks = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        seen.add(worldPosition);
        queue.add(worldPosition);

        while (!queue.isEmpty() && seen.size() <= MAX_NODES) {
            BlockPos cable = queue.removeFirst();

            for (Direction side : Direction.values()) {
                BlockPos neighbour = cable.relative(side);
                if (!seen.add(neighbour)) {
                    continue;
                }

                if (level.getBlockEntity(neighbour) instanceof CableBlockEntity) {
                    queue.addLast(neighbour);
                    continue;
                }

                EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, neighbour, side.getOpposite());
                if (handler != null) {
                    sinks.add(handler);
                }
            }
        }

        return sinks;
    }

    /// The handler the cable exposes on all six faces.
    ///
    /// Zero stored and zero capacity, which is the honest description of a wire and is also what a
    /// well-behaved pusher reads before it offers anything. A pusher that refuses to offer power to
    /// a handler whose capacity is zero will not use this cable; that is a trade for never having a
    /// wire that holds a charge, and Mellifera's own Engine (see MachineGenerator) simply offers.
    private class Conductor implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            return 0L;
        }

        @Override
        public long getCapacityAsLong() {
            return 0L;
        }

        /// Hands the whole offer on to the machines behind this cable.
        ///
        /// Two passes. The first gives every machine an equal share, so a bank of four fills evenly
        /// instead of the nearest one taking everything; the second offers what is left over to each
        /// in turn, so a share nobody could use is not thrown away. The sub-inserts join the caller's
        /// own transaction, which is what makes the whole hop atomic: if the push is rolled back,
        /// every machine it touched rolls back with it.
        @Override
        public int insert(int amount, TransactionContext transaction) {
            if (amount <= 0) {
                return 0;
            }

            List<EnergyHandler> sinks = reachableSinks();
            if (sinks.isEmpty()) {
                return 0;
            }

            int moved = 0;

            int share = amount / sinks.size();
            if (share > 0) {
                for (EnergyHandler sink : sinks) {
                    moved += sink.insert(share, transaction);
                }
            }

            for (EnergyHandler sink : sinks) {
                if (moved >= amount) {
                    break;
                }

                moved += sink.insert(amount - moved, transaction);
            }

            return moved;
        }

        /// Nothing to take: a conductor is empty by definition, and a cable that could be drained
        /// would be a battery with a shape.
        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public String toString() {
            return "Mellifera cable at " + worldPosition;
        }
    }
}
