package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.CarpenterRecipe;
import com.joaonf.mellifera.item.FrameItem;
import com.joaonf.mellifera.menu.CarpenterMenu;
import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaCarpenterRecipes;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaFluids;

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

/// Works liquid honey into a frame: the machine that makes every frame except the plain one.
///
/// WHAT IT IS FOR. Two problems met here. A hive stops dead for three reasons a player cannot touch
/// -- the temperature, the dark and the rain -- and the frames that lift those stops were shapeless
/// bench crafts costing one item each. Meanwhile the Squeezer produced a fluid with no use inside
/// this mod at all. So the frames now cost honey and time, and the honey now has somewhere to go:
/// combs to the Centrifuge, drops to the Squeezer, honey to a Tank, and a pipe from the Tank to
/// here. See MelliferaCarpenterRecipes for the costs and why they are what they are.
///
/// The tank is an *input*, which is the one thing that makes this machine's plumbing different from
/// the Squeezer's. It is filled by a pipe or by hand from a honey bucket, and drained by working.
///
/// It also repairs. A frame that has worn down goes in the frame slot with nothing beside it and
/// comes out fresh for a flat REPAIR_MB -- the reason a spent Insulation frame is worth carrying
/// home rather than dropping.
public class CarpenterBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_FRAME = 0;
    public static final int SLOT_INGREDIENT = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int TOTAL_SLOTS = 3;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_PROGRESS_TOTAL = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_FLUID = 3;
    public static final int NUM_DATA_VALUES = 4;

    /// Four buckets, the Squeezer's own figure. One Squeezer fills this to the brim, and the brim is
    /// four of the most expensive frame in the table -- enough to work unattended for a while,
    /// nowhere near enough to be a substitute for the Tank.
    public static final int TANK_CAPACITY = 4_000;
    public static final int BUCKET_MB = 1_000;

    public static final int FE_PER_TICK = 10;

    private NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    private final MachineEnergy energy = new MachineEnergy(this);
    private final MachineTank tank = new MachineTank(this, TANK_CAPACITY);

    private int progress;
    private int progressTotal = MelliferaCarpenterRecipes.REPAIR_TICKS;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_PROGRESS -> progress;
                case DATA_PROGRESS_TOTAL -> progressTotal;
                case DATA_ENERGY -> energy.stored();
                default -> tank.stored();
            };
        }

        @Override
        public void set(int id, int value) {
            if (id == DATA_PROGRESS) {
                progress = value;
            } else if (id == DATA_PROGRESS_TOTAL) {
                progressTotal = value;
            } else if (id == DATA_ENERGY) {
                energy.set(value);
            }
            // The tank level is read-only on the client, as it is on the Squeezer: it is told the
            // level, it never decides it.
        }

        @Override
        public int getCount() {
            return NUM_DATA_VALUES;
        }
    };

    public CarpenterBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.CARPENTER.get(), pos, state);
    }

    public MachineEnergy energy() {
        return energy;
    }

    public MachineTank tank() {
        return tank;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CarpenterBlockEntity carpenter) {
        Job job = carpenter.job();

        if (job == null) {
            setWorking(level, pos, state, false);
            if (carpenter.progress != 0) {
                carpenter.progress = 0;
                carpenter.setChanged();
            }
            return;
        }

        setWorking(level, pos, state, true);
        carpenter.progressTotal = job.ticks();
        // Power is speed, not permission -- see MachineEnergy.workStep.
        carpenter.progress += carpenter.energy.workStep(FE_PER_TICK);

        if (carpenter.progress >= job.ticks()) {
            carpenter.progress = 0;
            carpenter.finish(job);
        }

        carpenter.setChanged();
    }

    /// What the two input slots and the tank currently add up to, or null for nothing to do.
    ///
    /// Checked from scratch every tick rather than latched when a job starts. A machine that
    /// remembered what it was making would go on making it after the player pulled the ingredient
    /// out, and the progress bar is the only state worth keeping between ticks.
    private @Nullable Job job() {
        ItemStack frame = items.get(SLOT_FRAME);
        if (!(frame.getItem() instanceof FrameItem)) {
            return null;
        }

        ItemStack ingredient = items.get(SLOT_INGREDIENT);

        if (ingredient.isEmpty()) {
            // Repair: a worn frame and nothing to work into it. A fresh one is not a job, or the
            // machine would sit there drinking honey to no effect.
            if (wear(frame) >= FrameItem.FRESH_WEAR || FrameItem.neverWears(frame)) {
                return null;
            }

            return offerable(new Job(MelliferaCarpenterRecipes.REPAIR_MB, MelliferaCarpenterRecipes.REPAIR_TICKS,
                repaired(frame)));
        }

        CarpenterRecipe recipe = MelliferaCarpenterRecipes.find(frame, ingredient);
        if (recipe == null) {
            return null;
        }

        return offerable(new Job(recipe.honeyMb(), recipe.ticks(), new ItemStack(recipe.result().get())));
    }

    /// The same job, or null if it cannot be paid for or the result has nowhere to go.
    ///
    /// Both are checked before a single tick of progress is spent, so a machine short of honey
    /// stalls with its bar where it was rather than working for nothing and discovering the problem
    /// at the end.
    private @Nullable Job offerable(Job job) {
        if (tank.stored() < job.honeyMb()) {
            return null;
        }

        ItemStack out = items.get(SLOT_OUTPUT);
        boolean room = out.isEmpty()
            || (ItemStack.isSameItemSameComponents(out, job.result()) && out.getCount() < out.getMaxStackSize());
        return room ? job : null;
    }

    /// Spends the honey and the inputs, and puts the result out. Ordered so nothing is consumed
    /// unless the honey was really there.
    private void finish(Job job) {
        if (!tank.drain(job.honeyMb())) {
            return;
        }

        items.get(SLOT_FRAME).shrink(1);
        if (!items.get(SLOT_INGREDIENT).isEmpty()) {
            items.get(SLOT_INGREDIENT).shrink(1);
        }

        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) {
            items.set(SLOT_OUTPUT, job.result());
        } else {
            out.grow(1);
        }
    }

    /// A copy of the frame with its wear made good, keeping everything else on the stack -- an
    /// anvil-forged permanent frame that somehow got here stays permanent, and a renamed one keeps
    /// its name.
    private static ItemStack repaired(ItemStack frame) {
        ItemStack fresh = frame.copyWithCount(1);
        fresh.set(MelliferaDataComponents.FRAME_WEAR.get(), FrameItem.FRESH_WEAR);
        return fresh;
    }

    private static float wear(ItemStack frame) {
        return frame.getOrDefault(MelliferaDataComponents.FRAME_WEAR.get(), FrameItem.FRESH_WEAR);
    }

    /// Takes a bucket's worth of honey in, for a player who has one in hand. See CarpenterBlock.
    public boolean acceptBucket() {
        if (!tank.fill(FluidResource.of(MelliferaFluids.HONEY.get()), BUCKET_MB)) {
            return false;
        }

        setChanged();
        return true;
    }

    /// See CentrifugeBlockEntity.setWorking -- same contract, same reason for the guard.
    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (state.getValue(CarpenterBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(CarpenterBlock.WORKING, working), Block.UPDATE_ALL);
        }
    }

    /// One thing to make: what it costs, how long it takes, and what comes out.
    private record Job(int honeyMb, int ticks, ItemStack result) {}

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

    /// Static so the *client* menu enforces the same rule -- the client builds its menu over a plain
    /// SimpleContainer, which accepts anything, so a rule that lived only here would let an item
    /// visibly land in a slot and snap back a tick later. Same reason SqueezerBlockEntity's is.
    public static boolean isValidForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_FRAME -> MelliferaCarpenterRecipes.isFrame(stack);
            case SLOT_INGREDIENT -> MelliferaCarpenterRecipes.isIngredient(stack);
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
        return new int[] {SLOT_FRAME, SLOT_INGREDIENT, SLOT_OUTPUT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(slot, stack);
    }

    /// Finished frames may be pulled out; the inputs may not, or a hopper would cycle them through
    /// the machine forever without either being used.
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot == SLOT_OUTPUT;
    }

    // -- persistence -------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("progress", 0);
        progressTotal = input.getIntOr("progress_total", MelliferaCarpenterRecipes.REPAIR_TICKS);
        energy.deserialize(input);
        tank.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("progress", progress);
        output.putInt("progress_total", progressTotal);
        energy.serialize(output);
        tank.serialize(output);
    }

    // -- MenuProvider ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mellifera.carpenter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CarpenterMenu(containerId, playerInventory, this, data, worldPosition);
    }
}
