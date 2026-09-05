package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.CentrifugeRecipe;
import com.joaonf.mellifera.bee.ItemProduct;
import com.joaonf.mellifera.item.HoneyCombItem;
import com.joaonf.mellifera.menu.CentrifugeMenu;
import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaCentrifugeRecipes;
import com.joaonf.mellifera.registry.MelliferaCombTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.MenuProvider;
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

/// Spins combs down into their products: 1 input slot, 9 output slots.
///
/// The whole point of the machine is that it is chance-based, not deterministic -- every
/// output of a recipe is rolled independently (see CentrifugeRecipe), so the same comb can
/// give a different handful each time. A spin only starts if there is somewhere for the
/// results to go, and it consumes the comb only on completion, so pulling the input mid-spin
/// costs nothing.
public class CentrifugeBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT_START = 1;
    public static final int OUTPUT_SLOTS = 9;
    public static final int TOTAL_SLOTS = SLOT_OUTPUT_START + OUTPUT_SLOTS;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_PROGRESS_TOTAL = 1;
    /// The FE buffer, for the gauge. Fits in the sync packet's range because CAPACITY is
    /// well under a short -- worth remembering before anyone enlarges it.
    public static final int DATA_ENERGY = 2;
    public static final int NUM_DATA_VALUES = 3;

    /// Every slot is offered to automation; canTakeItemThroughFace is what actually keeps
    /// the unprocessed comb from being pulled back out.
    private static final int[] AUTOMATION_SLOTS = java.util.stream.IntStream.range(0, TOTAL_SLOTS).toArray();

    private NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    /// Forge Energy buffer. Priced per tick of progress: with power the machine buys eight of
    /// those a tick and so spends eight times this, and without power it earns one a tick for
    /// free -- see MachineEnergy.workStep, and MachineEnergy itself for why the machine spends
    /// its own buffer directly rather than through the transfer API's extract().
    public static final int FE_PER_TICK = 10;

    private final MachineEnergy energy = new MachineEnergy(this);

    public MachineEnergy energy() {
        return energy;
    }

    private int progress;
    private int progressTotal;

    /// See ApiaryBlockEntity.forceCycle -- honoured once by the next tick.
    private boolean forceSpin;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_PROGRESS -> progress;
                case DATA_ENERGY -> energy.stored();
                default -> progressTotal;
            };
        }

        @Override
        public void set(int id, int value) {
            if (id == DATA_PROGRESS) {
                progress = value;
            } else if (id == DATA_ENERGY) {
                energy.set(value);
            } else {
                progressTotal = value;
            }
        }

        @Override
        public int getCount() {
            return NUM_DATA_VALUES;
        }
    };

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.CENTRIFUGE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity centrifuge) {
        if (MachineSignal.switchedOff(level, pos)) {
            // Held off by redstone. Progress and fuel stay where they are; see MachineSignal.
            setWorking(level, pos, state, false);
            return;
        }

        CentrifugeRecipe recipe = centrifuge.currentRecipe();

        if (recipe == null) {
            setWorking(level, pos, state, false);
            if (centrifuge.progress != 0 || centrifuge.progressTotal != 0) {
                centrifuge.progress = 0;
                centrifuge.progressTotal = 0;
                centrifuge.setChanged();
            }
            return;
        }

        setWorking(level, pos, state, true);

        centrifuge.progressTotal = recipe.processTicks();
        // Power is speed, not permission -- see MachineEnergy.workStep. With nothing wired up
        // the spin still finishes, it just takes the recipe's full stated time.
        centrifuge.progress += centrifuge.energy.workStep(FE_PER_TICK);

        if (centrifuge.forceSpin || centrifuge.progress >= recipe.processTicks()) {
            centrifuge.forceSpin = false;
            centrifuge.progress = 0;
            centrifuge.spin(recipe, level.getRandom());
            centrifuge.items.get(SLOT_INPUT).shrink(1);
        }

        centrifuge.setChanged();
    }

    /// Drives the animated model. Guarded on the value actually changing: this runs every
    /// tick, and setBlock is a chunk write plus a client packet plus a neighbour update --
    /// affordable when a spin starts or stops, ruinous twenty times a second.
    ///
    /// Safe to do under the block entity's own feet: the block is unchanged, so the chunk
    /// keeps this block entity and merely hands it the new state (see LevelChunk).
    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (state.getValue(CentrifugeBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(CentrifugeBlock.WORKING, working), Block.UPDATE_ALL);
        }
    }

    private @Nullable CentrifugeRecipe currentRecipe() {
        return recipeFor(items.get(SLOT_INPUT));
    }

    /// Combs resolve through HoneyCombItem.combType() rather than reading COMB_TYPE raw, so
    /// a comb with no component still processes as a Honey comb -- exactly what its *name*
    /// already claims it is. Anything else falls through to the by-item table.
    private static @Nullable CentrifugeRecipe recipeFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        if (stack.getItem() instanceof HoneyCombItem) {
            Identifier combType = MelliferaCombTypes.REGISTRY.getKey(HoneyCombItem.combType(stack));
            return MelliferaCentrifugeRecipes.forComb(combType);
        }

        return MelliferaCentrifugeRecipes.forItem(stack.getItem());
    }

    /// Rolls every output independently. An output with nowhere to go is dropped on the
    /// floor rather than silently deleted -- the spin has already been paid for.
    private void spin(CentrifugeRecipe recipe, RandomSource random) {
        for (ItemProduct output : recipe.outputs()) {
            ItemStack rolled = output.roll(random);
            if (!rolled.isEmpty() && !insert(rolled)) {
                Containers.dropItemStack(
                    level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, rolled);
            }
        }
    }

    private boolean insert(ItemStack stack) {
        for (int slot = SLOT_OUTPUT_START; slot < TOTAL_SLOTS; slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                items.set(slot, stack);
                return true;
            }

            if (ItemStack.isSameItemSameComponents(existing, stack)
                && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                existing.grow(stack.getCount());
                return true;
            }
        }

        return false;
    }

    /// Completes the current spin on the next tick instead of waiting it out.
    public void debugSpin() {
        forceSpin = true;
        setChanged();
    }

    /// How full the output bay is: what the machine has spun down and nobody has collected.
    public int comparatorSignal() {
        return MachineSignal.fullness(this, SLOT_OUTPUT_START, TOTAL_SLOTS);
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

    /// The input slot takes anything the machine actually has a recipe for -- combs, and
    /// silky propolis -- rather than hardcoding "combs only", so adding a recipe is enough
    /// to make its input accepted. Nothing at all can be *placed* into an output slot.
    ///
    /// Static, so the *client* menu can enforce the same rule. The client builds its menu
    /// over a plain SimpleContainer, which accepts anything, so a rule that lived only on
    /// the block entity was invisible client-side: a non-processable item would visibly
    /// land in the input slot and only snap back once the server corrected it.
    public static boolean isValidForSlot(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && recipeFor(stack) != null;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isValidForSlot(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    /// Breaking the machine gives the contents back instead of deleting them, the same way
    /// the bee housings do. Without this, mining a working machine silently voided whatever
    /// was inside it.
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

    /// Hoppers and pipes see every slot, but only the output grid may be *taken* from.
    ///
    /// Without this a hopper under the centrifuge would pull the comb straight back out of
    /// the input slot before it was ever spun, so an automated feed would cycle combs
    /// through the machine forever without processing any of them.
    @Override
    public int[] getSlotsForFace(Direction direction) {
        return AUTOMATION_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot >= SLOT_OUTPUT_START && slot < TOTAL_SLOTS;
    }

    // -- persistence -------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("progress", 0);
        energy.deserialize(input);
        progressTotal = input.getIntOr("progress_total", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("progress", progress);
        energy.serialize(output);
        output.putInt("progress_total", progressTotal);
    }

    // -- MenuProvider ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mellifera.centrifuge");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CentrifugeMenu(containerId, playerInventory, this, data, worldPosition);
    }
}
