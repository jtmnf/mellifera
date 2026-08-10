package com.joaonf.mellifera.menu;

import com.joaonf.mellifera.block.CentrifugeBlockEntity;
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

/// One comb in on the left, a 3x3 grid of results on the right -- Forestry's own centrifuge
/// layout, and the reason the machine has 9 output slots rather than an arbitrary number.
public class CentrifugeMenu extends AbstractContainerMenu {
    private static final int INPUT_SLOT_X = 25;
    private static final int INPUT_SLOT_Y = 40;
    private static final int OUTPUT_GRID_X = 99;
    private static final int OUTPUT_GRID_Y = 21;
    private static final int SLOT_PITCH = 18;

    private static final int INVENTORY_Y = 94;
    private static final int HOTBAR_Y = 152;

    private static final int MACHINE_SLOTS = CentrifugeBlockEntity.TOTAL_SLOTS;
    private static final int PLAYER_INVENTORY_SLOTS = 27;
    private static final int HOTBAR_SLOTS = 9;

    private final Container centrifuge;
    private final ContainerData data;
    private final BlockPos centrifugePos;

    public CentrifugeMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, new SimpleContainer(MACHINE_SLOTS),
            new SimpleContainerData(CentrifugeBlockEntity.NUM_DATA_VALUES), buffer.readBlockPos());
    }

    public CentrifugeMenu(int containerId, Inventory playerInventory, Container centrifuge, ContainerData data, BlockPos centrifugePos) {
        super(MelliferaMenus.CENTRIFUGE.get(), containerId);

        checkContainerSize(centrifuge, MACHINE_SLOTS);
        this.centrifuge = centrifuge;
        this.data = data;
        this.centrifugePos = centrifugePos;

        addSlot(new Slot(centrifuge, CentrifugeBlockEntity.SLOT_INPUT, INPUT_SLOT_X, INPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return CentrifugeBlockEntity.isValidForSlot(CentrifugeBlockEntity.SLOT_INPUT, itemStack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new OutputSlot(centrifuge,
                    CentrifugeBlockEntity.SLOT_OUTPUT_START + column + row * 3,
                    OUTPUT_GRID_X + column * SLOT_PITCH,
                    OUTPUT_GRID_Y + row * SLOT_PITCH));
            }
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

    /// Results can be taken but never put back -- otherwise the output grid doubles as a
    /// second, larger input slot and the machine happily voids whatever it can't process.
    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return false;
        }
    }

    public BlockPos centrifugePos() {
        return centrifugePos;
    }

    public int progress() {
        return data.get(CentrifugeBlockEntity.DATA_PROGRESS);
    }

    public int progressTotal() {
        return data.get(CentrifugeBlockEntity.DATA_PROGRESS_TOTAL);
    }

    public int energy() {
        return data.get(CentrifugeBlockEntity.DATA_ENERGY);
    }

    @Override
    public boolean stillValid(Player player) {
        return centrifuge.stillValid(player);
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
        } else if (!moveItemStackTo(stack, 0, CentrifugeBlockEntity.SLOT_OUTPUT_START, false)) {
            // Never shift-click into the output grid: only the input slot accepts items.
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
