package com.joaonf.mellifera.menu;

import com.joaonf.mellifera.block.CarpenterBlockEntity;
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

/// A frame and an ingredient on the left, the finished frame on the right, honey down the side.
///
/// The two inputs sit one above the other where the Centrifuge and the Squeezer both put their
/// single input, so the family's windows keep reading the same way: work goes in on the left, comes
/// out on the right, and the tank is furniture drawn along the edge.
public class CarpenterMenu extends AbstractContainerMenu {
    /// One pixel inside the wells painted into the background -- a Vanilla slot is 16 pixels of item
    /// in an 18-pixel well. tools/gen_machine_guis.py mirrors these in its layout table; move one and
    /// the other has to move with it.
    private static final int FRAME_SLOT_X = 25;
    private static final int FRAME_SLOT_Y = 28;
    private static final int INGREDIENT_SLOT_X = 25;
    private static final int INGREDIENT_SLOT_Y = 50;
    private static final int OUTPUT_SLOT_X = 89;
    private static final int OUTPUT_SLOT_Y = 39;
    private static final int SLOT_PITCH = 18;

    private static final int INVENTORY_Y = 94;
    private static final int HOTBAR_Y = 152;

    private static final int MACHINE_SLOTS = CarpenterBlockEntity.TOTAL_SLOTS;
    private static final int PLAYER_INVENTORY_SLOTS = 27;
    private static final int HOTBAR_SLOTS = 9;

    private final Container carpenter;
    private final ContainerData data;
    private final BlockPos carpenterPos;

    public CarpenterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, new SimpleContainer(MACHINE_SLOTS),
            new SimpleContainerData(CarpenterBlockEntity.NUM_DATA_VALUES), buffer.readBlockPos());
    }

    public CarpenterMenu(int containerId, Inventory playerInventory, Container carpenter, ContainerData data, BlockPos carpenterPos) {
        super(MelliferaMenus.CARPENTER.get(), containerId);

        checkContainerSize(carpenter, MACHINE_SLOTS);
        this.carpenter = carpenter;
        this.data = data;
        this.carpenterPos = carpenterPos;

        addSlot(machineSlot(CarpenterBlockEntity.SLOT_FRAME, FRAME_SLOT_X, FRAME_SLOT_Y));
        addSlot(machineSlot(CarpenterBlockEntity.SLOT_INGREDIENT, INGREDIENT_SLOT_X, INGREDIENT_SLOT_Y));
        addSlot(new OutputSlot(carpenter, CarpenterBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y));

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

    private Slot machineSlot(int index, int x, int y) {
        return new Slot(carpenter, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CarpenterBlockEntity.isValidForSlot(index, stack);
            }
        };
    }

    /// Finished frames can be taken but never put back -- otherwise the output doubles as a second
    /// frame slot and the machine happily works on what it just made.
    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    public BlockPos carpenterPos() {
        return carpenterPos;
    }

    public int progress() {
        return data.get(CarpenterBlockEntity.DATA_PROGRESS);
    }

    public int progressTotal() {
        return data.get(CarpenterBlockEntity.DATA_PROGRESS_TOTAL);
    }

    public int energy() {
        return data.get(CarpenterBlockEntity.DATA_ENERGY);
    }

    /// Millibuckets of honey in the machine's own tank.
    public int fluid() {
        return data.get(CarpenterBlockEntity.DATA_FLUID);
    }

    @Override
    public boolean stillValid(Player player) {
        return carpenter.stillValid(player);
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
        } else if (!moveItemStackTo(stack, 0, CarpenterBlockEntity.SLOT_OUTPUT, false)) {
            // Never shift-click into the output: only the two input slots accept items, and each
            // refuses what the other takes.
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

        return original;
    }
}
