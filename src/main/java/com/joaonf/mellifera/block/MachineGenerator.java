package com.joaonf.mellifera.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/// The FE buffer a Mellifera *generator* runs on, and the other half of MachineEnergy.
///
/// Same size and same gauge as a machine's buffer, so one EnergyColumn draws both, but the two
/// transfer limits are the mirror image: nothing may be pushed in, and anything may be pulled out.
/// A generator that accepted power would be a battery, and a row of them wired together would be a
/// battery bank that also produced -- which is how "my engines drained each other" bug reports
/// start.
///
/// It also pushes. A cable mod would pull on its own, but Mellifera's own machines are sinks that
/// never pull, so an engine that only waited would light nothing on a wireless bench. Once a tick it
/// offers what it has to each of the six neighbours in turn; whatever they take is gone, and
/// whatever nobody takes stays in the buffer and stops the engine burning more (see
/// EngineBlockEntity.serverTick).
public class MachineGenerator extends SimpleEnergyHandler {
    /// The same buffer a machine carries. Not a battery: a full engine holds half a peat's worth,
    /// which is enough to ride out a machine finishing a job and no more.
    public static final int CAPACITY = MachineEnergy.CAPACITY;

    private static final int MAX_INSERT = 0;
    private static final int MAX_EXTRACT = 2_000;

    private final BlockEntity owner;

    public MachineGenerator(BlockEntity owner) {
        super(CAPACITY, MAX_INSERT, MAX_EXTRACT);
        this.owner = owner;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        owner.setChanged();
    }

    public int stored() {
        return getAmountAsInt();
    }

    public int space() {
        return CAPACITY - stored();
    }

    /// Puts newly made power in the buffer, keeping whatever will not fit out of existence.
    ///
    /// Direct rather than through insert(): maxInsert is zero, and it is zero on purpose -- see the
    /// class note. Generation is not a transfer between handlers, so there is nothing to roll back.
    public void generate(int amount) {
        if (amount <= 0) {
            return;
        }

        set(Math.min(CAPACITY, stored() + amount));
    }

    /// Offers the buffer to each neighbour in turn, and reports whether any of it moved.
    ///
    /// One pass, six sides, no memory of who took what last tick. A round-robin would spread the
    /// load more evenly across a bank of machines, and it would also mean an engine's behaviour
    /// depended on state that is invisible in-game; six offers in a fixed order is the version a
    /// player can reason about.
    public boolean pushToNeighbours(Level level, BlockPos pos) {
        if (stored() <= 0) {
            // An empty engine asks its neighbours nothing. Six capability lookups a tick is not much,
            // but it is six for nothing on every engine standing idle in a loaded chunk.
            return false;
        }

        boolean moved = false;

        for (Direction side : Direction.values()) {
            if (stored() <= 0) {
                break;
            }

            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(side), side.getOpposite());
            if (target == null) {
                continue;
            }

            moved |= EnergyHandlerUtil.move(this, target, Math.min(stored(), MAX_EXTRACT), null) > 0;
        }

        return moved;
    }
}
