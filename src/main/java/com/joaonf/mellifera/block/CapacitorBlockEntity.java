package com.joaonf.mellifera.block;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/// Holds Forge Energy between the making of it and the spending of it.
///
/// WHY IT EXISTS. An Engine burns at a flat 40 FE a tick and stops when its own 20,000 buffer is
/// full, so a hive that keeps producing through the night makes power nobody is asking for and the
/// engine simply idles. The Cable holds nothing by design. Without something that does, the mod's
/// power is use-it-or-lose-it, and a bench that is busy in bursts -- which is what a bench is --
/// wastes most of what it makes.
///
/// It has no window. There is nothing to put in it and nothing to take out: it is one number, and
/// that number is on the outside of the block, in the cells lit up its face, and on a comparator.
/// A screen would be a second way to read the same thing.
public class CapacitorBlockEntity extends BlockEntity {
    private final MachineBattery energy = new MachineBattery(this);

    public CapacitorBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.CAPACITOR.get(), pos, state);
    }

    public MachineBattery energy() {
        return energy;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CapacitorBlockEntity capacitor) {
        if (!MachineSignal.switchedOff(level, pos)) {
            capacitor.energy.pushToNeighbours(level, pos);
        }

        // Held off by redstone it still takes a charge; it only stops handing it on. A capacitor
        // that refused to fill would be a block that switches off the engine behind it, which is
        // not what a player wiring a lever to a battery is asking for.
        capacitor.showCharge(level, pos, state);
    }

    /// Keeps the lit cells on the block in step with what is in it.
    ///
    /// Five steps rather than a smooth bar, because this is a 16x16 face seen across a room: a
    /// gauge nobody can read at that size is decoration. The state only changes when the step does,
    /// so a capacitor filling steadily sends four block updates on the way up rather than one a
    /// tick.
    private void showCharge(Level level, BlockPos pos, BlockState state) {
        int step = CapacitorBlock.chargeStep(energy.stored());
        if (state.getValue(CapacitorBlock.CHARGE) != step) {
            level.setBlock(pos, state.setValue(CapacitorBlock.CHARGE, step), Block.UPDATE_ALL);
        }
    }

    /// What a comparator reads: how full it is, on the same 0 to 15 every other machine uses.
    public int comparatorSignal() {
        return MachineSignal.scaled(energy.stored(), MachineBattery.CAPACITY);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
    }
}
