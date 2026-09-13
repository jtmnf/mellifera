package com.joaonf.mellifera.menu;

import com.joaonf.mellifera.block.IsolatorBlockEntity;
import com.joaonf.mellifera.registry.MelliferaMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/// Bee over bottles on the left, two rows of four serum slots on the right -- one output
/// slot per chromosome, so a whole genome fits without a hopper.
///
/// Every coordinate here is baked into textures/gui/container/isolator.png by the generator
/// script, so the two must move together.
public class IsolatorMenu extends AbstractContainerMenu {
    private static final int BEE_SLOT_X = 20;
    private static final int BEE_SLOT_Y = 28;
    private static final int BOTTLE_SLOT_X = 20;
    private static final int BOTTLE_SLOT_Y = 50;

    /// The grid the chromosomes come out into: four across, one cell per trait, so its depth is
    /// however many chromosomes a bee has rather than a number written here. Twelve cells are
    /// painted (see tools/gen_machine_guis.py) and eleven are used; the loop below stops at the
    /// slots that exist rather than filling the row, so the spare cell stays a painted well with
    /// no slot behind it instead of a slot pointing past the end of the container.
    private static final int OUTPUT_GRID_X = 86;
    private static final int OUTPUT_GRID_Y = 20;
    private static final int OUTPUT_COLUMNS = 4;

    private static final int SLOT_PITCH = 18;
    private static final int INVENTORY_Y = 94;
    private static final int HOTBAR_Y = 152;

    private static final int MACHINE_SLOTS = IsolatorBlockEntity.TOTAL_SLOTS;
    private static final int PLAYER_INVENTORY_SLOTS = 27;
    private static final int HOTBAR_SLOTS = 9;

    private final Container isolator;
    private final ContainerData data;
    private final BlockPos isolatorPos;

    public IsolatorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, new SimpleContainer(MACHINE_SLOTS),
            new SimpleContainerData(IsolatorBlockEntity.NUM_DATA_VALUES), buffer.readBlockPos());
    }

    public IsolatorMenu(int containerId, Inventory playerInventory, Container isolator, ContainerData data, BlockPos isolatorPos) {
        super(MelliferaMenus.ISOLATOR.get(), containerId);

        checkContainerSize(isolator, MACHINE_SLOTS);
        this.isolator = isolator;
        this.data = data;
        this.isolatorPos = isolatorPos;

        addSlot(new InputSlot(isolator, IsolatorBlockEntity.SLOT_BEE, BEE_SLOT_X, BEE_SLOT_Y));
        addSlot(new InputSlot(isolator, IsolatorBlockEntity.SLOT_BOTTLE, BOTTLE_SLOT_X, BOTTLE_SLOT_Y));

        for (int cell = 0; cell < IsolatorBlockEntity.OUTPUT_SLOTS; cell++) {
            addSlot(new OutputSlot(isolator,
                IsolatorBlockEntity.SLOT_OUTPUT_START + cell,
                OUTPUT_GRID_X + (cell % OUTPUT_COLUMNS) * SLOT_PITCH,
                OUTPUT_GRID_Y + (cell / OUTPUT_COLUMNS) * SLOT_PITCH));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * SLOT_PITCH, INVENTORY_Y + row * SLOT_PITCH));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * SLOT_PITCH, HOTBAR_Y));
        }

        addDataSlots(data);
    }

    /// Defers to the block entity's static rule so the client rejects a wrong item at the
    /// same instant the server would -- see IsolatorBlockEntity.isValidForSlot.
    private static class InputSlot extends Slot {
        InputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return IsolatorBlockEntity.isValidForSlot(getContainerSlot(), itemStack);
        }
    }

    /// Serums come out and never go back in; otherwise the output grid doubles as a bin.
    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return false;
        }
    }

    public BlockPos isolatorPos() {
        return isolatorPos;
    }

    public int progress() {
        return data.get(IsolatorBlockEntity.DATA_PROGRESS);
    }

    public int progressTotal() {
        return data.get(IsolatorBlockEntity.DATA_PROGRESS_TOTAL);
    }

    public int energy() {
        return data.get(IsolatorBlockEntity.DATA_ENERGY);
    }

    /// How many of the bee's chromosomes have already been bottled: 0..7.
    public int cursor() {
        return data.get(IsolatorBlockEntity.DATA_CURSOR);
    }

    @Override
    public boolean stillValid(Player player) {
        return isolator.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int inventoryStart = MACHINE_SLOTS;
        int hotbarStart = inventoryStart + PLAYER_INVENTORY_SLOTS;
        int end = hotbarStart + HOTBAR_SLOTS;

        if (index < inventoryStart) {
            if (!moveItemStackTo(stack, inventoryStart, end, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, IsolatorBlockEntity.SLOT_OUTPUT_START, false)) {
            // Never shift-click into the output grid: only the two input slots accept items.
            if (index < hotbarStart) {
                if (!moveItemStackTo(stack, hotbarStart, end, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, inventoryStart, hotbarStart, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return original;
    }
}
