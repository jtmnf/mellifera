package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.menu.SqueezerMenu;
import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaFluids;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/// Presses honey drops and honeydew into liquid honey: one input slot, one tank, no output slots.
///
/// The machine that makes this mod's honey usable by anything else. Drops are an item and items only
/// travel by hopper; a tank of honey can be piped, stored, and drunk by any machine that wants a fluid
/// (see MelliferaFluids for why that matters). Nothing else in the mod turns a Mellifera product into
/// something the rest of a modpack can plumb.
///
/// Four drops to the bucket, deliberately. A bucket is 1000 mB and a drop is 250, so the number a
/// player has to hold in their head is four, not seventeen -- and a comb spun in the Centrifuge yields
/// about one drop, which makes a bucket of honey roughly four combs' worth of work.
///
/// It fills only if the whole 250 fits. A press that could half-fill would leave the machine holding a
/// drop it has already consumed and cannot finish, and the tank's own contents would then depend on
/// the order things happened in rather than on what went in.
public class SqueezerBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_INPUT = 0;

    /// The bucket bay, which lives outside the window. Two slots rather than one, because a machine that
    /// filled a bucket in place would leave a player unable to tell a full one from an empty one without
    /// picking it up.
    public static final int SLOT_BUCKET_IN = 1;
    public static final int SLOT_BUCKET_OUT = 2;
    public static final int TOTAL_SLOTS = 3;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_PROGRESS_TOTAL = 1;
    public static final int DATA_ENERGY = 2;
    /// The tank level, for the gauge. In millibuckets, so CAPACITY has to stay well under a short --
    /// the container data channel is 16-bit, which is the same limit the FE buffer lives under.
    public static final int DATA_FLUID = 3;
    public static final int NUM_DATA_VALUES = 4;

    /// Millibuckets per drop, and how much the tank holds. Four buckets: enough that a hopper feeding
    /// it can run unattended for a while, not enough to be a substitute for a tank.
    public static final int MB_PER_DROP = 250;
    public static final int TANK_CAPACITY = 4_000;

    /// Millibuckets per honeydew, which is half a drop.
    ///
    /// Honeydew was a centrifuge output with nothing to be for: every ordinary comb yields some and no
    /// recipe in the mod took it back. It presses because it is the one other sweet liquid the bees
    /// make, and it presses for half because it is the thin one -- eight to a bucket against a drop's
    /// four, so a player pressing honeydew is using up a by-product rather than finding a better
    /// source of honey than the honey.
    public static final int MB_PER_HONEYDEW = 125;

    /// A bucket, in the same units. Vanilla's own figure, and the reason a drop is 250: four to fill one.
    public static final int BUCKET_MB = 1_000;

    /// Ticks of *progress*: 16 seconds a drop unpowered, 2 seconds on FE.
    public static final int PROCESS_TICKS = 320;
    public static final int FE_PER_TICK = 10;

    private static final int[] AUTOMATION_SLOTS = {SLOT_INPUT, SLOT_BUCKET_IN, SLOT_BUCKET_OUT};

    private NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    private final MachineEnergy energy = new MachineEnergy(this);
    private final MachineTank tank = new MachineTank(this, TANK_CAPACITY);

    private int progress;

    /// See ApiaryBlockEntity.forceCycle -- honoured once by the next tick.
    private boolean forcePress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_PROGRESS -> progress;
                case DATA_ENERGY -> energy.stored();
                case DATA_FLUID -> tank.stored();
                default -> PROCESS_TICKS;
            };
        }

        @Override
        public void set(int id, int value) {
            if (id == DATA_PROGRESS) {
                progress = value;
            } else if (id == DATA_ENERGY) {
                energy.set(value);
            }
            // The tank level and the total are read-only on the client: it is told them, it never
            // decides them, and a setter that wrote into the tank would let a desynced client
            // invent honey.
        }

        @Override
        public int getCount() {
            return NUM_DATA_VALUES;
        }
    };

    public SqueezerBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.SQUEEZER.get(), pos, state);
    }

    public MachineEnergy energy() {
        return energy;
    }

    public MachineTank tank() {
        return tank;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SqueezerBlockEntity squeezer) {
        if (MachineSignal.switchedOff(level, pos)) {
            // Held off by redstone. Progress and fuel stay where they are; see MachineSignal.
            setWorking(level, pos, state, false);
            return;
        }

        // Bottling first, and unconditionally: it costs no power and no progress, so a full tank with a
        // bucket waiting should empty into it even while the press itself is stalled.
        squeezer.bottle();

        if (!squeezer.pressable()) {
            setWorking(level, pos, state, false);
            if (squeezer.progress != 0) {
                squeezer.progress = 0;
                squeezer.setChanged();
            }
            return;
        }

        setWorking(level, pos, state, true);
        // Power is speed, not permission -- see MachineEnergy.workStep.
        squeezer.progress += squeezer.energy.workStep(FE_PER_TICK);

        if (squeezer.forcePress || squeezer.progress >= PROCESS_TICKS) {
            squeezer.forcePress = false;
            squeezer.progress = 0;
            // Filled before the drop is spent, and only if it all fits: pressable() has already said
            // there is room, but the order is what guarantees a drop is never consumed for nothing.
            int mb = yieldOf(squeezer.items.get(SLOT_INPUT));
            if (mb > 0 && squeezer.tank.fill(FluidResource.of(MelliferaFluids.HONEY.get()), mb)) {
                squeezer.items.get(SLOT_INPUT).shrink(1);
            }
        }

        squeezer.setChanged();
    }

    /// Fills a bucket from the tank, if there is a bucket to fill, a whole bucket to fill it with, and
    /// somewhere for it to go.
    ///
    /// WHY THIS EXISTS. Until it did, the only way honey left this machine was a pipe from another mod --
    /// so on its own, the Squeezer was a machine that produced something the player could not touch. A
    /// bucket bay is the smallest thing that makes it whole, and it belongs outside the window: it is not
    /// part of pressing, it is the tap on the side of the tank.
    private void bottle() {
        if (tank.stored() < BUCKET_MB) {
            return;
        }

        if (!items.get(SLOT_BUCKET_IN).is(Items.BUCKET)) {
            return;
        }

        ItemStack filled = new ItemStack(MelliferaFluids.HONEY_BUCKET.get());
        ItemStack out = items.get(SLOT_BUCKET_OUT);
        boolean room = out.isEmpty()
            || (ItemStack.isSameItemSameComponents(out, filled) && out.getCount() < out.getMaxStackSize());
        if (!room) {
            return;
        }

        // Drained before the bucket appears, and only if the whole 1000 is there: the tank is the one
        // thing here that must never pay for something twice.
        if (!tank.drain(BUCKET_MB)) {
            return;
        }

        items.get(SLOT_BUCKET_IN).shrink(1);
        if (out.isEmpty()) {
            items.set(SLOT_BUCKET_OUT, filled);
        } else {
            out.grow(1);
        }

        setChanged();
    }

    /// Takes a whole bucket's worth out of the tank, or nothing at all.
    ///
    /// The tap for a player holding a bucket, as bottle() is the tap for a hopper feeding the bucket
    /// slots. Both go through tank.drain, and both refuse a partial bucket for the same reason: a
    /// bucket is 1000 or it is not a bucket, and a tank that pays out 700 has lost 700.
    public boolean drawOffBucket() {
        if (tank.stored() < BUCKET_MB || !tank.drain(BUCKET_MB)) {
            return false;
        }

        setChanged();
        return true;
    }

    /// What one of these is worth in the tank, or 0 for anything the machine does not press.
    ///
    /// The single place that answers it: the slot filter, the press and the JEI page all read this, so
    /// a machine that accepts an item it cannot price, or a page that quotes a rate the machine does
    /// not run, would take a deliberate effort to write.
    public static int yieldOf(ItemStack stack) {
        if (stack.is(MelliferaItems.HONEY_DROP.get())) {
            return MB_PER_DROP;
        }

        if (stack.is(MelliferaItems.HONEYDEW.get())) {
            return MB_PER_HONEYDEW;
        }

        return 0;
    }

    /// Something pressable in the slot and room in the tank for all of what it is worth. Checked before
    /// any progress is spent, so a full tank stalls a press rather than losing it.
    private boolean pressable() {
        int mb = yieldOf(items.get(SLOT_INPUT));
        return mb > 0 && tank.space() >= mb;
    }

    /// See CentrifugeBlockEntity.setWorking -- same contract, same reason for the guard.
    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (state.getValue(SqueezerBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(SqueezerBlock.WORKING, working), Block.UPDATE_ALL);
        }
    }

    /// Completes the current press on the next tick instead of waiting it out.
    public void debugPress() {
        forcePress = true;
        setChanged();
    }

    /// The honey in the tank, which is the whole of what this machine makes.
    public int comparatorSignal() {
        return MachineSignal.scaled(tank.stored(), TANK_CAPACITY);
    }

    // -- Container ---------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

    /// See BeeHousingBlockEntity.isEmpty for why this is a loop.
    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    /// What the press has a price for, and nothing else. Static so the *client* menu enforces the same rule -- the client
    /// builds its menu over a plain SimpleContainer, which accepts anything, so a rule that lived only
    /// here would let an item visibly land in the slot and snap back a tick later.
    public static boolean isValidForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_INPUT -> yieldOf(stack) > 0;
            case SLOT_BUCKET_IN -> stack.is(Items.BUCKET);
            // Nothing may be *placed* in the out slot; the machine is the only thing that fills it.
            default -> false;
        };
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isValidForSlot(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    /// Breaking the machine gives the drops back. The honey in the tank is lost, which is the same
    /// bargain every fluid machine makes: a fluid is not an item and there is nothing to drop.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            Containers.dropContents(level, pos, this);
        }
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // -- automation --------------------------------------------------------------------

    /// Drops and empty buckets go in, filled buckets come out. The honey can also leave through the
    /// fluid capability, which is the route a pipe takes.
    @Override
    public int[] getSlotsForFace(Direction direction) {
        return AUTOMATION_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(slot, stack);
    }

    /// The filled bucket may be pulled out; the drop and the empty bucket may not, or a hopper would
    /// cycle them through the machine forever without either being used.
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot == SLOT_BUCKET_OUT;
    }

    // -- persistence -------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("progress", 0);
        energy.deserialize(input);
        tank.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("progress", progress);
        energy.serialize(output);
        tank.serialize(output);
    }

    // -- MenuProvider ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mellifera.squeezer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SqueezerMenu(containerId, playerInventory, this, data, worldPosition);
    }
}
