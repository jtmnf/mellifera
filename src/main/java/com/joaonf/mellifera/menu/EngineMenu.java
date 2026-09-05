package com.joaonf.mellifera.menu;

import com.joaonf.mellifera.block.EngineBlockEntity;
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

/// Peat on the left, honey down the side, and nothing coming out but power.
///
/// The fuel slot sits exactly where the Squeezer and the Centrifuge put their single input, so the
/// family windows keep reading the same way even though this is the one machine with no output
/// slot at all -- what it makes leaves through the sides of the block.
public class EngineMenu extends AbstractContainerMenu {
    /// One pixel inside the well painted into the background. tools/gen_machine_guis.py mirrors
    /// this in its layout table; move one and the other has to move with it.
    private static final int FUEL_SLOT_X = 25;
    private static final int FUEL_SLOT_Y = 40;
    private static final int SLOT_PITCH = 18;

    private static final int INVENTORY_Y = 94;
    private static final int HOTBAR_Y = 152;

    private static final int MACHINE_SLOTS = EngineBlockEntity.TOTAL_SLOTS;
    private static final int PLAYER_INVENTORY_SLOTS = 27;
    private static final int HOTBAR_SLOTS = 9;

    private final Container engine;
    private final ContainerData data;
    private final BlockPos enginePos;

    public EngineMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, new SimpleContainer(MACHINE_SLOTS),
            new SimpleContainerData(EngineBlockEntity.NUM_DATA_VALUES), buffer.readBlockPos());
    }

    public EngineMenu(int containerId, Inventory playerInventory, Container engine, ContainerData data, BlockPos enginePos) {
        super(MelliferaMenus.ENGINE.get(), containerId);

        checkContainerSize(engine, MACHINE_SLOTS);
        this.engine = engine;
        this.data = data;
        this.enginePos = enginePos;

        addSlot(new Slot(engine, EngineBlockEntity.SLOT_FUEL, FUEL_SLOT_X, FUEL_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EngineBlockEntity.isValidForSlot(EngineBlockEntity.SLOT_FUEL, stack);
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

    public BlockPos enginePos() {
        return enginePos;
    }

    /// Ticks of fuel left in the firebox, and what the last light was worth.
    public int burn() {
        return data.get(EngineBlockEntity.DATA_BURN);
    }

    public int burnTotal() {
        return data.get(EngineBlockEntity.DATA_BURN_TOTAL);
    }

    public int energy() {
        return data.get(EngineBlockEntity.DATA_ENERGY);
    }

    /// Millibuckets of honey left to burn.
    public int fluid() {
        return data.get(EngineBlockEntity.DATA_FLUID);
    }

    @Override
    public boolean stillValid(Player player) {
        return engine.stillValid(player);
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
        } else if (!moveItemStackTo(stack, 0, MACHINE_SLOTS, false)) {
            // Not fuel, so it moves between the player's own two rows instead -- the same fallback
            // every other machine window here uses.
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
