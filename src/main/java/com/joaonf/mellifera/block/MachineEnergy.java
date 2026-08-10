package com.joaonf.mellifera.block;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/// The FE buffer every Mellifera machine runs on.
///
/// Forge Energy is the only power standard worth speaking here -- RF is the same thing under
/// its old name -- but the API for it moved in this Minecraft version: it is no longer
/// `IEnergyStorage` with plain int returns, it is `EnergyHandler` in
/// `neoforge.transfer.energy`, and every insert or extract happens inside a transaction that
/// can be rolled back. Most tutorials online still show the old one.
///
/// This class exists so the three machines share one answer to all of that, and so a machine
/// only has to say how big its buffer is and how much a tick of work costs.
public class MachineEnergy extends SimpleEnergyHandler {
    /// Enough to ride out a cable hiccup mid-operation without being a battery in its own
    /// right: a machine should stall when the grid dies, not run for another minute.
    public static final int CAPACITY = 20_000;

    /// Accepts far more per tick than it spends, so a cable can refill it between operations
    /// rather than dribbling. Extraction is zero: a machine is a sink, and letting power be
    /// pulled back out would make a row of them into an accidental battery bank.
    private static final int MAX_INSERT = 2_000;
    private static final int MAX_EXTRACT = 0;

    /// How many ticks of progress a powered tick is worth. An unpowered tick is worth one.
    ///
    /// Mellifera ships no generator of its own, so with no power mod installed every machine
    /// here would be a decoration. So power is a multiplier, not a switch: a dry machine runs
    /// at its stated PROCESS_TICKS, and FE makes it eight times faster.
    ///
    /// Deliberately this way round rather than penalising the unpowered machine by 8x -- the
    /// stated times are already the slow end of tolerable (the Isolator's 100 ticks is *per
    /// trait*, eight of them to a genome), and eight times that is a machine nobody waits for.
    public static final int POWERED_SPEED = 8;

    private final BlockEntity owner;

    public MachineEnergy(BlockEntity owner) {
        super(CAPACITY, MAX_INSERT, MAX_EXTRACT);
        this.owner = owner;
    }

    /// Marks the block entity dirty whenever the level moves, so the buffer survives a
    /// reload and the client's gauge is told to update.
    @Override
    protected void onEnergyChanged(int previousAmount) {
        owner.setChanged();
    }

    public int stored() {
        return getAmountAsInt();
    }

    /// Spends `amount` if it is all there, and reports whether it was.
    ///
    /// All or nothing on purpose: a machine that drew whatever was available would creep
    /// forward at a trickle on a starved grid, which looks identical to one that is simply
    /// slow. Stalling outright is legible -- the lamp goes out and the bar stops.
    ///
    /// Deliberately not routed through extract() and a transaction. maxExtract is zero, so
    /// that path would refuse, and it is zero on purpose: it is what stops *other* blocks
    /// pulling power back out and turning a row of machines into a battery bank. A machine
    /// spending its own buffer is not a transfer between handlers, it is consumption, and
    /// there is nothing to roll back -- so it is a direct write.
    /// How much progress this tick of work earns, spending power if there is power to spend.
    ///
    /// `costPerTick` is priced per tick *of progress*, so a powered tick pays for all eight of
    /// them at once: the energy a job costs end to end is the same whether it ran fast or slow,
    /// which is what stops "unpowered" being the cheap way to run the machine.
    ///
    /// Falls back to one rather than to zero. There is no such thing as a stalled Mellifera
    /// machine any more, so callers do not have a no-power branch to handle -- and the WORKING
    /// state stays put instead of flickering off on every tick that could not afford the boost.
    public int workStep(int costPerTick) {
        return consume(costPerTick * POWERED_SPEED) ? POWERED_SPEED : 1;
    }

    public boolean consume(int amount) {
        if (amount <= 0) {
            return true;
        }

        if (stored() < amount) {
            return false;
        }

        set(stored() - amount);
        return true;
    }
}
