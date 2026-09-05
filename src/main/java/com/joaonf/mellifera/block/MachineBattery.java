package com.joaonf.mellifera.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/// The third kind of FE buffer in the mod, and the only one that both takes and gives.
///
/// MachineEnergy is a sink with extraction closed, MachineGenerator is a source with insertion
/// closed, and each of those is closed on purpose -- a machine that could be drained would make a
/// row of them into a battery bank, and a generator that could be charged would be a battery with a
/// fire in it. This one is the battery, so it is open at both ends, and being open at both ends is
/// exactly what makes it need a rule the other two do not have.
///
/// THE RULE: a capacitor never pushes into another capacitor. Without it two of them side by side
/// hand the same power back and forth forever, each one satisfied that it did some work, and a bank
/// of four is a machine for turning ticks into nothing. Power moves from a capacitor outwards to
/// things that spend it; between capacitors it only moves when a cable or another mod's pipe pulls
/// it, which is a decision somebody made rather than an accident of adjacency.
public class MachineBattery extends SimpleEnergyHandler {
    /// Ten engine buffers, which is about five buckets of honey burned.
    ///
    /// Sized against what it is for: holding a night's burning until the morning's work. Big enough
    /// that a bank of machines starting up together does not empty it in a second, small enough
    /// that it is a buffer between an engine and a bench rather than a way to never build a second
    /// engine.
    public static final int CAPACITY = 200_000;

    /// In and out at the same rate, and both well above what a machine spends: the point of the
    /// block is to absorb a burst and pay one out, not to meter anything.
    private static final int MAX_TRANSFER = 4_000;

    private final BlockEntity owner;

    public MachineBattery(BlockEntity owner) {
        super(CAPACITY, MAX_TRANSFER, MAX_TRANSFER);
        this.owner = owner;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        owner.setChanged();
    }

    public int stored() {
        return getAmountAsInt();
    }

    /// Offers what it holds to each neighbour that is not another capacitor. See the class note for
    /// why that exception is the whole design.
    public boolean pushToNeighbours(Level level, BlockPos pos) {
        if (stored() <= 0) {
            return false;
        }

        boolean moved = false;

        for (Direction side : Direction.values()) {
            if (stored() <= 0) {
                break;
            }

            BlockPos neighbour = pos.relative(side);
            if (level.getBlockEntity(neighbour) instanceof CapacitorBlockEntity) {
                continue;
            }

            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, neighbour, side.getOpposite());
            if (target == null) {
                continue;
            }

            moved |= EnergyHandlerUtil.move(this, target, Math.min(stored(), MAX_TRANSFER), null) > 0;
        }

        return moved;
    }
}
