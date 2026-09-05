package com.joaonf.mellifera.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/// The two things every Mellifera machine says to redstone, in one place.
///
/// WHAT GOES OUT. A comparator on a machine reads what that machine has made and not yet given away
/// -- the combs in a hive's bay, the serums on the Isolator's rack, the honey in the Squeezer's
/// tank, the power in the Engine's buffer. Not its progress: progress is a bar that fills and
/// empties on its own, so a comparator watching it would be a clock rather than a report, and it
/// would say nothing about whether the machine needs emptying. The Infuser is the one exception and
/// says why at its own method: it makes nothing, so it reports what it still has to write.
///
/// WHAT COMES IN. A redstone signal switches a machine off, which is Vanilla's own convention for a
/// hopper and the one a player will guess first. Off means the simulation does not advance; it does
/// not mean anything is lost. A half-finished job keeps its progress, a lit engine keeps what is in
/// its firebox, and a queen in a switched-off hive stops working rather than starts dying.
public final class MachineSignal {
    private MachineSignal() {}

    /// Whether a redstone signal is holding this block off.
    ///
    /// Read every tick rather than cached in the block state. Six neighbour lookups is cheap beside
    /// what these machines already do each tick -- the Apiary alone surveys its territory for
    /// flowers -- and a cached flag is a second copy of the truth that has to be invalidated by
    /// every path that can change it, which is where hoppers historically went wrong.
    public static boolean switchedOff(Level level, BlockPos pos) {
        return level.hasNeighborSignal(pos);
    }

    /// A level as a comparator strength: 0 when empty, then 1 to 15 across the rest of the range.
    ///
    /// The step off zero is deliberate and is what every Vanilla container does -- a tank holding a
    /// single millibucket must not read the same as an empty one, or a comparator cannot be used to
    /// tell a machine that its output has somewhere to go.
    public static int scaled(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }

        return 1 + Math.min(amount, capacity) * 14 / capacity;
    }

    /// The same, for a run of slots: how full they are, counted the way Vanilla counts a chest --
    /// each stack as a fraction of what that item may stack to, so sixteen ender pearls in a slot
    /// read as a full slot and not as a quarter of one.
    public static int fullness(Container container, int from, int to) {
        float filled = 0.0F;
        int slots = 0;

        for (int slot = from; slot < to; slot++) {
            ItemStack stack = container.getItem(slot);
            slots++;

            if (!stack.isEmpty()) {
                filled += stack.getCount() / (float) Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
            }
        }

        if (slots <= 0 || filled <= 0.0F) {
            return 0;
        }

        return 1 + Math.round(filled / slots * 14.0F);
    }
}
