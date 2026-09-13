package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.menu.EngineMenu;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/// Burns what the bees make into Forge Energy: the one block in the mod that produces power rather
/// than spending it.
///
/// WHY IT EXISTS. Every other machine here runs eight times faster on FE and Mellifera shipped no
/// way to make any, so the whole mod was either slow or dependent on a tech mod. It also closes the
/// honey loop from the other end: the Squeezer made a fluid, the Carpenter spends a little of it,
/// and past a few frames a tank simply filled up and stayed full. An apiary that powers its own
/// Centrifuge is the answer to both.
///
/// TWO FUELS, ONE AT A TIME. Liquid honey out of the tank, or peat in the slot -- the two things
/// bees produce that a player otherwise stockpiles. It never burns both at once: the output is a
/// flat FE_PER_TICK whatever is in the firebox, so a hopper feeding peat into an engine already
/// drinking honey does not double anything. Honey goes first, because it is the one that arrives by
/// pipe and the one there is too much of.
///
/// It will not burn into a full buffer. A tick with nowhere to put the power is a tick that spends
/// no fuel, which is what stops an idle engine quietly eating a tank of honey overnight.
public class EngineBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_FUEL = 0;
    public static final int TOTAL_SLOTS = 1;

    public static final int DATA_BURN = 0;
    public static final int DATA_BURN_TOTAL = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_FLUID = 3;
    public static final int NUM_DATA_VALUES = 4;

    /// Four buckets, the Squeezer and the Carpenter figure.
    public static final int TANK_CAPACITY = 4_000;
    public static final int BUCKET_MB = 1_000;

    /// Forge Energy a burning tick is worth.
    ///
    /// Twice what a machine draws flat out. MachineEnergy prices a tick of *progress* at 5 and a
    /// powered machine buys eight of those a game tick, so a machine at full speed costs 40 -- and
    /// one engine keeps two of them there, or one of them there while a second buffer fills for the
    /// next job. Any less and the first thing a player would learn is that the engine they built
    /// cannot run the machine they built it for.
    public static final int FE_PER_TICK = 80;

    /// A draught of honey: 100 mB bought for 100 ticks of burning, so honey is worth 80 FE the
    /// millibucket and a bucket is 80,000 FE.
    ///
    /// Drawn in draughts rather than a millibucket a tick because a tank that ticks down by one is
    /// a gauge that never visibly moves, and because a part-spent millibucket is a thing the tank
    /// cannot hold.
    public static final int HONEY_DRAUGHT_MB = 100;
    public static final int HONEY_DRAUGHT_TICKS = 100;

    /// Peat: 400 ticks, so 32,000 FE a lump. Boggy bees drop it at 0.08 a pulse, roughly one lump
    /// every two minutes of a hive working, and it burns for rather longer than that -- peat is the
    /// fuel carried home, honey is the one plumbed in.
    public static final int PEAT_TICKS = 400;

    private NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    private final MachineGenerator energy = new MachineGenerator(this);
    private final MachineTank tank = new MachineTank(this, TANK_CAPACITY);

    /// Ticks of fuel left in the firebox, and what the last light was worth. The second is only for
    /// the gauge: without it a peat and a draught of honey would fill the same bar at different
    /// rates and neither would mean anything.
    private int burn;
    private int burnTotal = PEAT_TICKS;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_BURN -> burn;
                case DATA_BURN_TOTAL -> burnTotal;
                case DATA_ENERGY -> energy.stored();
                default -> tank.stored();
            };
        }

        @Override
        public void set(int id, int value) {
            if (id == DATA_BURN) {
                burn = value;
            } else if (id == DATA_BURN_TOTAL) {
                burnTotal = value;
            } else if (id == DATA_ENERGY) {
                energy.set(value);
            }
            // The tank level is read-only on the client, as it is on every other tank in the mod.
        }

        @Override
        public int getCount() {
            return NUM_DATA_VALUES;
        }
    };

    public EngineBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.ENGINE.get(), pos, state);
    }

    public MachineGenerator energy() {
        return energy;
    }

    public MachineTank tank() {
        return tank;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EngineBlockEntity engine) {
        if (MachineSignal.switchedOff(level, pos)) {
            // Held off by redstone. Whatever is in the firebox stays in it, and the buffer stays
            // where it is rather than emptying into the neighbours -- an engine switched off is off,
            // not a battery being drained. See MachineSignal.
            setWorking(level, pos, state, false);
            return;
        }

        // Pushing first, and unconditionally: a full buffer is what stops the engine burning, so
        // emptying it before deciding is the difference between an engine that pauses for a tick
        // and one that pauses until something else happens to it.
        boolean pushed = engine.energy.pushToNeighbours(level, pos);

        boolean burning = engine.tickFirebox();
        setWorking(level, pos, state, burning);

        if (burning || pushed) {
            engine.setChanged();
        }
    }

    /// One tick of the fire: light it if it is out, spend it if there is room for what it makes.
    ///
    /// Returns whether the engine is actually burning, which is what the block state shows.
    private boolean tickFirebox() {
        if (energy.space() < FE_PER_TICK) {
            // Nowhere to put it. The fire keeps whatever is left in it -- banking the tick rather
            // than losing it is what makes a fuel worth a fixed number of FE however it is used.
            return false;
        }

        if (burn <= 0 && !light()) {
            return false;
        }

        burn--;
        energy.generate(FE_PER_TICK);
        return true;
    }

    /// Puts the next fuel in the firebox, honey first. False if there is nothing to burn.
    private boolean light() {
        if (tank.stored() >= HONEY_DRAUGHT_MB && tank.drain(HONEY_DRAUGHT_MB)) {
            burn = HONEY_DRAUGHT_TICKS;
            burnTotal = HONEY_DRAUGHT_TICKS;
            return true;
        }

        if (items.get(SLOT_FUEL).is(MelliferaItems.PEAT.get())) {
            items.get(SLOT_FUEL).shrink(1);
            burn = PEAT_TICKS;
            burnTotal = PEAT_TICKS;
            return true;
        }

        return false;
    }

    /// See CentrifugeBlockEntity.setWorking -- same contract, same reason for the guard.
    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (state.getValue(EngineBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(EngineBlock.WORKING, working), Block.UPDATE_ALL);
        }
    }

    /// Takes a bucket's worth of honey in, for a player who has one in hand. See EngineBlock.
    public boolean acceptBucket() {
        if (!tank.fill(FluidResource.of(MelliferaFluids.HONEY.get()), BUCKET_MB)) {
            return false;
        }

        setChanged();
        return true;
    }

    /// The power in the buffer: what the engine has made and nothing has taken yet. A full
    /// reading means the machines it feeds are not asking for anything.
    public int comparatorSignal() {
        return MachineSignal.scaled(energy.stored(), MachineGenerator.CAPACITY);
    }

    // -- Container ---------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

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

    /// Peat and nothing else. Static for the same reason the other machines' rule is: the client
    /// builds its menu over a plain SimpleContainer, which accepts anything.
    ///
    /// Deliberately not every furnace fuel. An engine that took coal would be a generator with
    /// nothing to do with bees, and the point of this block is that an apiary powers itself.
    public static boolean isValidForSlot(int slot, ItemStack stack) {
        return slot == SLOT_FUEL && stack.is(MelliferaItems.PEAT.get());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isValidForSlot(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    /// Breaking it gives the peat back. The honey in the tank is lost, the same bargain the
    /// Squeezer and the Carpenter make.
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

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return new int[] {SLOT_FUEL};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(slot, stack);
    }

    /// Fuel goes in and stays in: a hopper under an engine would otherwise pull the peat straight
    /// back out from under it.
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return false;
    }

    // -- persistence -------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        burn = input.getIntOr("burn", 0);
        burnTotal = input.getIntOr("burn_total", PEAT_TICKS);
        energy.deserialize(input);
        tank.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("burn", burn);
        output.putInt("burn_total", burnTotal);
        energy.serialize(output);
        tank.serialize(output);
    }

    // -- MenuProvider ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mellifera.engine");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new EngineMenu(containerId, playerInventory, this, data, worldPosition);
    }
}
