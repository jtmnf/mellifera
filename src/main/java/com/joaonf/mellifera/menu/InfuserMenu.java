package com.joaonf.mellifera.menu;

import com.joaonf.mellifera.block.InfuserBlockEntity;
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

/// The isolator's layout mirrored: bee over pollen on the left, two rows of four serum slots
/// on the right -- one per chromosome, so a whole isolated genome goes straight back in.
///
/// Every coordinate here is baked into textures/gui/container/infuser.png by the generator
/// script, so the two must move together.
public class InfuserMenu extends AbstractContainerMenu {
    private static final int BEE_SLOT_X = 20;
    private static final int BEE_SLOT_Y = 28;
    private static final int POLLEN_SLOT_X = 20;
    private static final int POLLEN_SLOT_Y = 50;

    private static final int SERUM_GRID_X = 86;
    private static final int SERUM_COLUMNS = 4;
    private static final int[] SERUM_ROW_Y = {28, 50};

    private static final int SLOT_PITCH = 18;
    private static final int INVENTORY_Y = 94;
    private static final int HOTBAR_Y = 152;

    private static final int MACHINE_SLOTS = InfuserBlockEntity.TOTAL_SLOTS;
    private static final int PLAYER_INVENTORY_SLOTS = 27;
    private static final int HOTBAR_SLOTS = 9;

    private final Container infuser;
    private final ContainerData data;
    private final BlockPos infuserPos;

    public InfuserMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, new SimpleContainer(MACHINE_SLOTS),
            new SimpleContainerData(InfuserBlockEntity.NUM_DATA_VALUES), buffer.readBlockPos());
    }

    public InfuserMenu(int containerId, Inventory playerInventory, Container infuser, ContainerData data, BlockPos infuserPos) {
        super(MelliferaMenus.INFUSER.get(), containerId);

        checkContainerSize(infuser, MACHINE_SLOTS);
        this.infuser = infuser;
        this.data = data;
        this.infuserPos = infuserPos;

        addSlot(machineSlot(InfuserBlockEntity.SLOT_BEE, BEE_SLOT_X, BEE_SLOT_Y));
        addSlot(machineSlot(InfuserBlockEntity.SLOT_POLLEN, POLLEN_SLOT_X, POLLEN_SLOT_Y));

        for (int row = 0; row < SERUM_ROW_Y.length; row++) {
            for (int column = 0; column < SERUM_COLUMNS; column++) {
                addSlot(machineSlot(
                    InfuserBlockEntity.SLOT_SERUM_START + column + row * SERUM_COLUMNS,
                    SERUM_GRID_X + column * SLOT_PITCH,
                    SERUM_ROW_Y[row]));
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

    private Slot machineSlot(int slotIndex, int x, int y) {
        return new Slot(infuser, slotIndex, x, y) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return InfuserBlockEntity.isValidForSlot(slotIndex, itemStack);
            }
        };
    }

    public BlockPos infuserPos() {
        return infuserPos;
    }

    public int progress() {
        return data.get(InfuserBlockEntity.DATA_PROGRESS);
    }

    public int progressTotal() {
        return data.get(InfuserBlockEntity.DATA_PROGRESS_TOTAL);
    }

    public int energy() {
        return data.get(InfuserBlockEntity.DATA_ENERGY);
    }

    /// Infusions left in the pollen currently burning.
    public int charges() {
        return data.get(InfuserBlockEntity.DATA_CHARGES);
    }

    @Override
    public boolean stillValid(Player player) {
        return infuser.stillValid(player);
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
        } else if (!moveItemStackTo(stack, 0, inventoryStart, false)) {
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
