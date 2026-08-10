package com.joaonf.mellifera.block;

import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.BeeTrait;
import com.joaonf.mellifera.bee.SerumData;
import com.joaonf.mellifera.item.DroneBeeItem;
import com.joaonf.mellifera.item.PrincessBeeItem;
import com.joaonf.mellifera.item.SerumItem;
import com.joaonf.mellifera.menu.InfuserMenu;
import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
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

/// The Isolator run backwards: a bee, a rack of serums, and the genes go in rather than out.
///
/// Three things about how it works are choices rather than mechanics, and each is here for a
/// reason:
///
/// - **The bee is modified in place.** It sits in its slot and gains a trait per cycle, so a
///   player can watch the tooltip change and stop whenever they have what they wanted. An
///   output slot would have meant committing to the whole rack before seeing anything.
/// - **Serums overwrite the expressed allele only** (see BeeTrait.applyTo). The old allele
///   is still carried and can resurface in breeding, so an infused bee is one with a new
///   face and its ancestry intact -- not a manufactured one.
/// - **Pollen is fuel, not an ingredient.** One pollen carries four infusions, in the way a
///   piece of coal carries several smelts, which keeps the cost real without making the
///   player feed the machine between every serum.
public class InfuserBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_BEE = 0;
    public static final int SLOT_POLLEN = 1;
    public static final int SLOT_SERUM_START = 2;

    /// One per chromosome, so a whole isolated bee's worth of serums can be queued at once
    /// and the two machines are each other's mirror image.
    public static final int SERUM_SLOTS = BeeTrait.ALL.length;
    public static final int TOTAL_SLOTS = SLOT_SERUM_START + SERUM_SLOTS;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_PROGRESS_TOTAL = 1;
    public static final int DATA_CHARGES = 2;
    /// The FE buffer, for the gauge. Fits in the sync packet's range because CAPACITY is
    /// well under a short -- worth remembering before anyone enlarges it.
    public static final int DATA_ENERGY = 3;
    public static final int NUM_DATA_VALUES = 4;

    /// Ticks of *progress*, which is the unpowered time: 32 seconds an infusion by hand, 4 on FE
    /// (see MachineEnergy.POWERED_SPEED).
    public static final int PROCESS_TICKS = 640;

    /// Infusions one pollen is worth.
    public static final int CHARGES_PER_POLLEN = 4;

    private static final int[] AUTOMATION_SLOTS = IntStream.range(0, TOTAL_SLOTS).toArray();

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

    /// Infusions left in the pollen already burnt. Survives a reload like a furnace's
    /// remaining burn time, and for the same reason: a player who fed the machine and walked
    /// away should not come back to have paid for nothing.
    private int charges;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_PROGRESS -> progress;
                case DATA_PROGRESS_TOTAL -> PROCESS_TICKS;
                case DATA_ENERGY -> energy.stored();
                default -> charges;
            };
        }

        @Override
        public void set(int id, int value) {
            switch (id) {
                case DATA_PROGRESS -> progress = value;
                case DATA_CHARGES -> charges = value;
                case DATA_ENERGY -> energy.set(value);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return NUM_DATA_VALUES;
        }
    };

    public InfuserBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.INFUSER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InfuserBlockEntity infuser) {
        // Fuel first, and regardless of whether there is anything to infuse: see topUp.
        infuser.topUp();

        BeeGenome genome = genomeOf(infuser.items.get(SLOT_BEE));
        int serumSlot = infuser.firstSerum();

        if (genome == null || serumSlot < 0) {
            setWorking(level, pos, state, false);
            infuser.resetProgress();
            return;
        }

        if (infuser.charges <= 0) {
            // Out of pollen with work waiting: stall rather than reset, so feeding it more
            // resumes the trait already half infused instead of starting it over.
            setWorking(level, pos, state, false);
            return;
        }

        setWorking(level, pos, state, true);
        // Power is speed, not permission -- see MachineEnergy.workStep.
        infuser.progress += infuser.energy.workStep(FE_PER_TICK);

        if (infuser.progress >= PROCESS_TICKS) {
            infuser.progress = 0;
            infuser.charges--;
            infuser.infuse(genome, serumSlot);
        }

        infuser.setChanged();
    }

    /// Burns a pollen the moment the last one is spent, whether or not there is anything to infuse.
    ///
    /// Eagerly rather than on demand, which is a deliberate change: the pips under the slot are the
    /// only sign of how much fuel the machine holds, and a pollen sitting in the slot beside four dark
    /// pips reads as a machine that is not fuelled at all. Burning on arrival makes the readout mean
    /// what it looks like it means.
    ///
    /// The cost, stated plainly: the pollen is spent as soon as it is dropped in. Charges are saved
    /// with the block, so nothing is lost to a reload, but they are no longer an item -- breaking the
    /// Infuser loses whatever is left in the buffer.
    private void topUp() {
        if (charges > 0) {
            return;
        }

        ItemStack pollen = items.get(SLOT_POLLEN);
        if (!pollen.is(MelliferaItems.POLLEN.get())) {
            return;
        }

        pollen.shrink(1);
        charges = CHARGES_PER_POLLEN;

        // Marked here rather than at the end of the tick: a tick that burns a pollen with no bee in
        // the slot returns long before reaching that, and an unsaved consumed pollen comes back.
        setChanged();
    }

    /// Writes one serum's gene onto the bee and consumes the serum.
    private void infuse(BeeGenome genome, int serumSlot) {
        ItemStack serum = items.get(serumSlot);
        SerumData serumData = SerumItem.data(serum);
        BeeTrait trait = serumData == null ? null : serumData.resolvedTrait();

        // A serum whose trait no longer exists is consumed anyway rather than jamming the
        // rack forever. It cost a charge and taught the player nothing, but a machine that
        // silently stops on one bad item is worse than one that clears it.
        if (trait != null) {
            items.get(SLOT_BEE).set(MelliferaDataComponents.BEE_GENOME.get(), trait.applyTo(genome, serumData.value()));
        }

        serum.shrink(1);
        setChanged();
    }

    /// The lowest occupied serum slot, so the rack is worked through left to right and the
    /// player can predict which gene lands next.
    private int firstSerum() {
        for (int slot = SLOT_SERUM_START; slot < TOTAL_SLOTS; slot++) {
            if (!items.get(slot).isEmpty()) {
                return slot;
            }
        }

        return -1;
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    /// See CentrifugeBlockEntity.setWorking -- same contract, same reason for the guard.
    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (state.getValue(InfuserBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(InfuserBlock.WORKING, working), Block.UPDATE_ALL);
        }
    }

    /// Princess and Drone only. A mated Queen carries her own genome and her mate's, and
    /// rewriting half of a record of who she bred with is not something this machine has any
    /// sensible answer for -- so she is simply not accepted.
    private static @Nullable BeeGenome genomeOf(ItemStack stack) {
        if (stack.getItem() instanceof PrincessBeeItem || stack.getItem() instanceof DroneBeeItem) {
            return stack.getOrDefault(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.defaultGenome());
        }

        return null;
    }

    public static boolean isValidForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_BEE -> genomeOf(stack) != null;
            case SLOT_POLLEN -> stack.is(MelliferaItems.POLLEN.get());
            default -> stack.getItem() instanceof SerumItem;
        };
    }

    public ContainerData containerData() {
        return data;
    }

    // -- container ----------------------------------------------------------------------------

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
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }

        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isValidForSlot(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return AUTOMATION_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return isValidForSlot(slot, stack);
    }

    /// Only the finished bee comes back out. Pulling serums or pollen out again would let a
    /// hopper loop cycle the rack without ever infusing anything.
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_BEE;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        Containers.dropContents(level, pos, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mellifera.infuser");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new InfuserMenu(containerId, inventory, this, data, worldPosition);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("progress", 0);
        energy.deserialize(input);
        charges = input.getIntOr("charges", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("progress", progress);
        energy.serialize(output);
        output.putInt("charges", charges);
    }
}
