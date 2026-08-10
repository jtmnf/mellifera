package com.joaonf.mellifera.menu;

import com.joaonf.mellifera.block.SqueezerBlockEntity;
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

/// One drop in on the left, a tank on the right, and a bucket bay hanging off the window's edge.
///
/// The interior is the press: drop, progress, tank. The bay is not part of that and is deliberately not
/// in it -- it is the tap, the way honey leaves for a player with no pipes, and putting it outside is
/// what keeps the press's own window uncluttered while making the machine complete on its own.
public class SqueezerMenu extends AbstractContainerMenu {
    /// The drop sits exactly where the Centrifuge's input does, because this window's background *is*
    /// the Centrifuge's with the output bay painted out -- the two machines are siblings and the shared
    /// art says so. The tank is drawn rather than slotted, so it has no coordinates here; see
    /// SqueezerScreen.
    private static final int INPUT_SLOT_X = 25;
    private static final int INPUT_SLOT_Y = 40;
    private static final int SLOT_PITCH = 18;

    /// The bucket bay, past the window's right edge. Outside on purpose: it is not part of pressing, it
    /// is the tap on the side of the tank, and the interior has no room to spare. Coordinates are in the
    /// window's own space, so anything at x >= 176 is drawn beyond it -- see SqueezerScreen, which draws
    /// the plate these two sit in.
    public static final int BAY_X = 181;
    public static final int BAY_IN_Y = 26;
    public static final int BAY_OUT_Y = 48;

    private static final int INVENTORY_Y = 94;
    private static final int HOTBAR_Y = 152;

    private static final int MACHINE_SLOTS = SqueezerBlockEntity.TOTAL_SLOTS;
    private static final int PLAYER_INVENTORY_SLOTS = 27;
    private static final int HOTBAR_SLOTS = 9;

    private final Container squeezer;
    private final ContainerData data;
    private final BlockPos squeezerPos;

    public SqueezerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, new SimpleContainer(MACHINE_SLOTS),
            new SimpleContainerData(SqueezerBlockEntity.NUM_DATA_VALUES), buffer.readBlockPos());
    }

    public SqueezerMenu(int containerId, Inventory playerInventory, Container squeezer, ContainerData data, BlockPos squeezerPos) {
        super(MelliferaMenus.SQUEEZER.get(), containerId);

        checkContainerSize(squeezer, MACHINE_SLOTS);
        this.squeezer = squeezer;
        this.data = data;
        this.squeezerPos = squeezerPos;

        addSlot(new Slot(squeezer, SqueezerBlockEntity.SLOT_INPUT, INPUT_SLOT_X, INPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return SqueezerBlockEntity.isValidForSlot(SqueezerBlockEntity.SLOT_INPUT, itemStack);
            }
        });

        addSlot(new Slot(squeezer, SqueezerBlockEntity.SLOT_BUCKET_IN, BAY_X, BAY_IN_Y) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return SqueezerBlockEntity.isValidForSlot(SqueezerBlockEntity.SLOT_BUCKET_IN, itemStack);
            }
        });

        // Filled buckets come out and nothing goes in: otherwise the bay doubles as a second input and
        // the machine cheerfully holds items it will never touch.
        addSlot(new Slot(squeezer, SqueezerBlockEntity.SLOT_BUCKET_OUT, BAY_X, BAY_OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return false;
            }
        });

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

    public BlockPos squeezerPos() {
        return squeezerPos;
    }

    public int progress() {
        return data.get(SqueezerBlockEntity.DATA_PROGRESS);
    }

    public int progressTotal() {
        return data.get(SqueezerBlockEntity.DATA_PROGRESS_TOTAL);
    }

    public int energy() {
        return data.get(SqueezerBlockEntity.DATA_ENERGY);
    }

    /// The tank level in millibuckets, for the gauge.
    public int fluid() {
        return data.get(SqueezerBlockEntity.DATA_FLUID);
    }

    @Override
    public boolean stillValid(Player player) {
        return squeezer.stillValid(player);
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
