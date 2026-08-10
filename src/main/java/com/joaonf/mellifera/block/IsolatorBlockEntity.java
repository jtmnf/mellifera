package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.BeeTrait;
import com.joaonf.mellifera.bee.QueenGenomeData;
import com.joaonf.mellifera.item.DroneBeeItem;
import com.joaonf.mellifera.item.PrincessBeeItem;
import com.joaonf.mellifera.item.QueenBeeItem;
import com.joaonf.mellifera.item.SerumItem;
import com.joaonf.mellifera.menu.IsolatorMenu;
import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaDataComponents;

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

/// Binnie's Isolator: reads a bee's genome out one chromosome at a time, bottling each into
/// a serum. A bee in, a stack of glass bottles in, eight serums out.
///
/// The "one at a time" part is the whole machine. A cursor walks BeeTrait.ALL in order and
/// each completed cycle bottles exactly the trait it points at, costs one glass bottle, and
/// steps forward; only once all eight are out is the bee itself consumed. That makes a full
/// genome a visible, interruptible eight-stage job rather than a single opaque conversion,
/// and it means a player who only wants the species serum can pull the bee back out after
/// the first cycle with the bee intact.
///
/// Nothing is consumed unless the serum actually lands in an output slot, so a full output
/// grid stalls the machine instead of voiding bees and bottles.
public class IsolatorBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_BEE = 0;
    public static final int SLOT_BOTTLE = 1;
    public static final int SLOT_OUTPUT_START = 2;

    /// One output slot per chromosome, so a whole bee can be isolated without a hopper
    /// attached: two serums never stack (their components differ), so eight traits need
    /// eight slots.
    public static final int OUTPUT_SLOTS = BeeTrait.ALL.length;
    public static final int TOTAL_SLOTS = SLOT_OUTPUT_START + OUTPUT_SLOTS;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_PROGRESS_TOTAL = 1;
    public static final int DATA_CURSOR = 2;
    /// The FE buffer, for the gauge. Fits in the sync packet's range because CAPACITY is
    /// well under a short -- worth remembering before anyone enlarges it.
    public static final int DATA_ENERGY = 3;
    public static final int NUM_DATA_VALUES = 4;

    /// Ticks of *progress*, so this is the unpowered time: 40 seconds a trait, and eight traits
    /// to a genome, which is a little over five minutes by hand and 40 seconds on FE (see
    /// MachineEnergy.POWERED_SPEED). Slower than a centrifuge spin either way, because a serum
    /// is worth far more than a handful of wax.
    ///
    /// This is the machine the multiplier matters most on, and deliberately so: the unpowered
    /// run is usable for the one genome you need, and unbearable as a habit.
    public static final int PROCESS_TICKS = 800;

    private static final int[] AUTOMATION_SLOTS = java.util.stream.IntStream.range(0, TOTAL_SLOTS).toArray();

    private NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    /// Forge Energy buffer. Priced per tick of progress, so a powered tick spends eight times
    /// this and buys eight ticks of work -- see MachineEnergy.workStep, and MachineEnergy
    /// itself for why the machine spends its own buffer directly rather than extract().
    public static final int FE_PER_TICK = 10;

    private final MachineEnergy energy = new MachineEnergy(this);

    public MachineEnergy energy() {
        return energy;
    }

    private int progress;
    private int cursor;

    /// The genome the cursor belongs to. Compared against the bee in the slot every tick;
    /// the moment they differ the cursor restarts.
    ///
    /// Stored as a genome rather than a copy of the bee stack on purpose: the cursor tracks
    /// *which genes have already been bottled*, and that is a property of the genome, not of
    /// the item carrying it. Swapping a princess for a drone of the identical genome
    /// therefore continues where it left off -- the remaining serums would be identical
    /// either way -- while any genuinely different bee resets to trait 0 and cannot inherit
    /// a stale cursor and skip its own genes.
    private @Nullable BeeGenome trackedGenome;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_PROGRESS -> progress;
                case DATA_PROGRESS_TOTAL -> PROCESS_TICKS;
                case DATA_ENERGY -> energy.stored();
                default -> cursor;
            };
        }

        @Override
        public void set(int id, int value) {
            if (id == DATA_PROGRESS) {
                progress = value;
            } else if (id == DATA_CURSOR) {
                cursor = value;
            } else if (id == DATA_ENERGY) {
                energy.set(value);
            }
        }

        @Override
        public int getCount() {
            return NUM_DATA_VALUES;
        }
    };

    public IsolatorBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.ISOLATOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IsolatorBlockEntity isolator) {
        // A run already under way keeps going from the stored genome, with the bee long
        // gone; otherwise a fresh bee in the slot starts one.
        if (isolator.trackedGenome == null) {
            BeeGenome waiting = genomeOf(isolator.items.get(SLOT_BEE));
            if (waiting == null) {
                setWorking(level, pos, state, false);
                isolator.reset();
                return;
            }

            // Banked and spent in the same breath. These two used to be a trait apart: the genome
            // was taken here and the slot emptied on the first trait's completion, which left a
            // window where the run was already committed and the specimen was still sitting there
            // to be pulled back out -- the bee kept, and all eight serums produced from it anyway.
            isolator.trackedGenome = waiting;
            isolator.items.set(SLOT_BEE, ItemStack.EMPTY);
            isolator.cursor = 0;
            isolator.progress = 0;
            isolator.setChanged();
        }

        BeeGenome genome = isolator.trackedGenome;
        BeeTrait trait = BeeTrait.ALL[isolator.cursor];
        ItemStack serum = SerumItem.create(trait, genome);

        // Stall rather than reset: pulling the bottles or filling the outputs pauses the
        // run, it does not throw away the ticks already spent on this trait.
        if (!isolator.items.get(SLOT_BOTTLE).is(Items.GLASS_BOTTLE) || !isolator.canInsert(serum)) {
            // A stall is exactly the case the outward sign exists for -- the machine goes
            // dark rather than pretending to run with a half-finished trait banked.
            setWorking(level, pos, state, false);
            return;
        }

        setWorking(level, pos, state, true);
        // Power is speed, not permission -- see MachineEnergy.workStep. PROCESS_TICKS is per
        // trait and there are eight of them, so the unpowered run is the long one by design.
        isolator.progress += isolator.energy.workStep(FE_PER_TICK);

        if (isolator.progress >= PROCESS_TICKS) {
            isolator.progress = 0;

            // Nothing touches the bee slot here. It did, and because this block runs once per
            // trait rather than once per run, a bee dropped in while the machine was on trait 3
            // was deleted at the end of trait 4 and gave nothing back. The specimen is spent where
            // the run starts, which is also why the machine has to hold the genome itself: there
            // is nothing left in the slot to read for genes 2..8.
            isolator.insert(serum);
            isolator.items.get(SLOT_BOTTLE).shrink(1);
            isolator.advance();
        }

        isolator.setChanged();
    }

    /// See CentrifugeBlockEntity.setWorking -- same contract, same reason for the guard.
    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (state.getValue(IsolatorBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(IsolatorBlock.WORKING, working), Block.UPDATE_ALL);
        }
    }

    /// One gene bottled. After the eighth the genome is exhausted and the machine goes
    /// idle, ready for the next bee -- one bee yields all eight serums.
    private void advance() {
        cursor++;
        if (cursor >= BeeTrait.ALL.length) {
            cursor = 0;
            trackedGenome = null;
        }
    }

    private void reset() {
        if (progress != 0 || cursor != 0 || trackedGenome != null) {
            progress = 0;
            cursor = 0;
            trackedGenome = null;
            setChanged();
        }
    }

    /// Princess and Drone carry BEE_GENOME; a Queen carries QUEEN_GENOME and only her own
    /// half is hers to isolate -- the mate's genome is a record of who she bred with, not a
    /// gene the machine can pull out of her.
    private static @Nullable BeeGenome genomeOf(ItemStack stack) {
        if (stack.getItem() instanceof PrincessBeeItem || stack.getItem() instanceof DroneBeeItem) {
            return stack.getOrDefault(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.defaultGenome());
        }

        if (stack.getItem() instanceof QueenBeeItem) {
            return stack.getOrDefault(MelliferaDataComponents.QUEEN_GENOME.get(), QueenGenomeData.defaultGenome()).own();
        }

        return null;
    }

    private boolean canInsert(ItemStack stack) {
        for (int slot = SLOT_OUTPUT_START; slot < TOTAL_SLOTS; slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()
                || (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() + stack.getCount() <= existing.getMaxStackSize())) {
                return true;
            }
        }

        return false;
    }

    private void insert(ItemStack stack) {
        for (int slot = SLOT_OUTPUT_START; slot < TOTAL_SLOTS; slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                items.set(slot, stack);
                return;
            }

            if (ItemStack.isSameItemSameComponents(existing, stack)
                && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                existing.grow(stack.getCount());
                return;
            }
        }
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

    /// Static for the same reason CentrifugeBlockEntity.isValidForSlot is: the client builds
    /// its menu over a plain SimpleContainer, so a rule that lived only here would be
    /// invisible client-side and a wrong item would visibly land in a slot before the server
    /// snapped it back.
    public static boolean isValidForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_BEE -> genomeOf(stack) != null;
            case SLOT_BOTTLE -> stack.is(Items.GLASS_BOTTLE);
            default -> false;
        };
    }

    /// The static rule, plus the one thing it cannot know: no second bee while a run is under way.
    ///
    /// A bee accepted mid-run would sit in the slot doing nothing until the eighth serum, since the
    /// machine reads the genome it banked and not the slot. Refusing it is the honest answer, and
    /// it is what stops a hopper feeding a queue of bees into a machine that cannot take them --
    /// the hopper holds them instead, and resumes on its own when the run ends.
    ///
    /// Bottles are unaffected: those are consumed one per trait and are meant to be topped up
    /// while the machine works.
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_BEE && trackedGenome != null) {
            return false;
        }

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

    /// Hoppers may feed bees and bottles but only ever pull serums back out: without this a
    /// hopper under the machine would drain the input slots and no bee would ever be
    /// isolated.
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
        cursor = Math.floorMod(input.getIntOr("cursor", 0), BeeTrait.ALL.length);
        trackedGenome = input.read("tracked_genome", BeeGenome.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("progress", progress);
        energy.serialize(output);
        output.putInt("cursor", cursor);
        output.storeNullable("tracked_genome", BeeGenome.CODEC, trackedGenome);
    }

    // -- MenuProvider ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mellifera.isolator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new IsolatorMenu(containerId, playerInventory, this, data, worldPosition);
    }
}
