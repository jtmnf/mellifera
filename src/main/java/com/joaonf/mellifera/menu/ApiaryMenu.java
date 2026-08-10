package com.joaonf.mellifera.menu;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeStacks;
import com.joaonf.mellifera.block.ApiaryBlockEntity;
import com.joaonf.mellifera.registry.MelliferaMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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

// Slot positions are measured off Forestry's real Apiary GUI (LGPL v3, ForestryMC/ForestryMC,
// assets/forestry/textures/gui/apiary.png) -- the hex output cluster and vertical progress
// gauge are their layout, recoloured into this mod's wood palette but geometrically unchanged.
public class ApiaryMenu extends AbstractContainerMenu {
    private static final int QUEEN_SLOT_X = 29;
    private static final int QUEEN_SLOT_Y = 39;
    private static final int DRONE_SLOT_X = 29;
    private static final int DRONE_SLOT_Y = 65;
    /// The frame slots live outside the window, in the frame tab (see FramePanel).
    ///
    /// Coordinates rather than something computed, because Slot.x and Slot.y are final: a
    /// slot cannot be moved after construction, so a panel that holds real slots has to sit
    /// at a position known up front. It does. The tab strip only ever has one panel open at
    /// a time, so whenever the frame panel is open the objective tab above it is shut, and
    /// the frame panel's origin is the same every time -- one closed tab down from the top.
    ///
    /// Being outside imageWidth is fine and deliberate: slots render at leftPos + x with no
    /// bound on x, and the window's own texture simply does not reach this far.
    /// Nine, in three rows of three -- one row per apiary in the stack.
    ///
    /// This array's length *is* how many frame slots the menu has: one Slot is built per
    /// entry. It must therefore match ApiaryBlockEntity.FRAME_SLOTS exactly. When it did not,
    /// the container had nine frame slots and the menu only six, so the last three existed in
    /// the hive and were unreachable from the screen -- nothing could be put in them and
    /// nothing could be taken out.
    private static final int[] FRAME_SLOT_X = {183, 205, 227, 183, 205, 227, 183, 205, 227};
    private static final int[] FRAME_SLOT_Y = { 54,  54,  54,  76,  76,  76,  98,  98,  98};

    /// Everything right of the harvest panel shifts with the wider window a stacked hive
    /// uses -- including the frame tab, which hangs off the window's right edge.
    public static final int WIDE_SHIFT = 42;
    // All 7 hexes of the cluster, measured off Forestry's own GUI art.
    private static final int[] COMB_SLOT_X = {116,  95, 137, 116,  95, 137, 116};
    private static final int[] COMB_SLOT_Y = { 26,  39,  39,  52,  65,  65,  78};

    /// The five a stacked hive adds, continuing the same lattice leftwards -- 21 across and
    /// 13 down, the spacing the original cluster uses. They have to touch it: the hexagon is
    /// not the shape of one cell, it is what two chamfered cells make where they meet, so a
    /// detached cell renders as a rounded square no matter how it is drawn.
    ///
    /// Deliberately not shifted with the seven above: those move right by WIDE_SHIFT in the
    /// wider window and these fill the strip they vacate, which is what keeps the whole cluster
    /// one lattice. Three of them are live at two levels and all five at three (see
    /// ApiaryBlockEntity.activeOutputSlots), and their cells are baked into apiary_2.png and
    /// apiary_3.png -- so these coordinates and those textures must move together.
    private static final int[] EXTRA_COMB_X = {116, 116, 116,  95,  95};
    private static final int[] EXTRA_COMB_Y = { 26,  52,  78,  39,  65};

    /// Where the player's grid sits, and the same in all three windows: every height is 190
    /// tall, and only the width changes with the stack (see ApiaryScreen).
    ///
    /// These follow from that height rather than the other way round. Vanilla draws its own
    /// "Inventory" label at imageHeight - 94 and cannot be told otherwise, and its grid sits
    /// at imageHeight - 82 with the hotbar at imageHeight - 24 -- which for 190 is exactly the
    /// two numbers below. A window of another height has to move both.
    private static final int INVENTORY_Y = 108;
    private static final int HOTBAR_Y = 166;

    private static final int APIARY_SLOTS = ApiaryBlockEntity.TOTAL_SLOTS;
    private static final int PLAYER_INVENTORY_SLOTS = 27;
    private static final int HOTBAR_SLOTS = 9;

    private final Container apiary;
    private final ContainerData data;
    private final BlockPos apiaryPos;

    /// Whether the frame tab is currently open. See frameSlot.
    ///
    /// Starts true and is only ever set false by the client screen, so the server's copy
    /// stays true for the menu's whole life. That matters: the server must never decide a
    /// frame slot is inactive on account of a panel it cannot see.
    private boolean framesVisible = true;

    public void setFramesVisible(boolean visible) {
        this.framesVisible = visible;
    }

    /// The tower's height, carried in the open packet.
    ///
    /// Not a ContainerData slot: slots are built in this constructor, before any data has
    /// been synced, so a layout that depends on the height has to have it by then. The extra
    /// byte rides along with the position NeoForge already writes.
    private final int levels;

    public ApiaryMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, new SimpleContainer(APIARY_SLOTS),
            new SimpleContainerData(ApiaryBlockEntity.NUM_DATA_VALUES), buffer.readBlockPos(), buffer.readByte());
    }

    public ApiaryMenu(int containerId, Inventory playerInventory, Container apiary, ContainerData data, BlockPos apiaryPos, int levels) {
        super(MelliferaMenus.APIARY.get(), containerId);

        checkContainerSize(apiary, APIARY_SLOTS);
        // A slot the container has and the menu does not is a slot nobody can reach. Caught
        // exactly that once already, when the frame rows grew from two to three here but the
        // coordinate table was left at six entries.
        if (FRAME_SLOT_X.length != ApiaryBlockEntity.FRAME_SLOTS
            || COMB_SLOT_X.length + EXTRA_COMB_X.length != ApiaryBlockEntity.COMB_SLOTS) {
            throw new IllegalStateException("apiary slot tables do not match the container's slot counts");
        }

        this.apiary = apiary;
        this.data = data;
        this.apiaryPos = apiaryPos;
        this.levels = Math.max(1, Math.min(ApiaryBlockEntity.MAX_LEVELS, levels));

        int shift = this.levels > 1 ? WIDE_SHIFT : 0;
        addSlot(machineSlot(ApiaryBlockEntity.SLOT_QUEEN, QUEEN_SLOT_X, QUEEN_SLOT_Y));
        addSlot(machineSlot(ApiaryBlockEntity.SLOT_DRONE, DRONE_SLOT_X, DRONE_SLOT_Y));
        for (int i = 0; i < FRAME_SLOT_X.length; i++) {
            addSlot(frameSlot(ApiaryBlockEntity.SLOT_FRAME_START + i, FRAME_SLOT_X[i] + shift, FRAME_SLOT_Y[i]));
        }
        // A stacked hive's window is wider, so the cluster sits further right and the extra
        // cells fill the space opened to its left.
        for (int i = 0; i < COMB_SLOT_X.length; i++) {
            addSlot(machineSlot(ApiaryBlockEntity.SLOT_COMB_START + i, COMB_SLOT_X[i] + shift, COMB_SLOT_Y[i]));
        }
        for (int i = 0; i < EXTRA_COMB_X.length; i++) {
            int index = COMB_SLOT_X.length + i;
            addSlot(heightGated(ApiaryBlockEntity.SLOT_COMB_START + index, EXTRA_COMB_X[i], EXTRA_COMB_Y[i],
                index < ApiaryBlockEntity.activeOutputSlots(this.levels)));
        }

        // The player's grid is re-centred in the wider window rather than shifted with the
        // machine area -- it belongs to the player, not to the hive, and it should sit in
        // the middle of whatever window it is in.
        int inventoryX = 8 + shift / 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, inventoryX + column * 18, INVENTORY_Y + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, inventoryX + column * 18, HOTBAR_Y));
        }

        addDataSlots(data);
    }

    /// A frame slot, which exists only while its tab is open.
    ///
    /// isActive is the only way to hide a slot -- the coordinates are final -- and it is the
    /// right one: an inactive slot is not drawn and not hit-tested, so a shut tab leaves no
    /// clickable holes floating beside the window.
    ///
    /// `framesVisible` is set from the screen and so only ever changes on the client. The
    /// server's copy of this menu keeps it true for the whole session, which is what we want:
    /// hopper insertion and quickMoveStack both work by slot index and must keep working
    /// whether or not the player happens to have the tab open.
    private Slot frameSlot(int slotIndex, int x, int y) {
        return new Slot(apiary, slotIndex, x, y) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return ApiaryBlockEntity.isValidForSlot(slotIndex, itemStack);
            }

            @Override
            public boolean isActive() {
                return framesVisible && slotIndex - ApiaryBlockEntity.SLOT_FRAME_START
                    < ApiaryBlockEntity.activeFrameSlots(levels);
            }
        };
    }

    /// A slot that only exists at a certain tower height. Inactive is the honest state for
    /// one the hive has not grown into: not drawn, not clickable, and nothing the simulation
    /// will put anything in either (see BeeHousingBlockEntity.activeOutputSlots).
    private Slot heightGated(int slotIndex, int x, int y, boolean present) {
        return new Slot(apiary, slotIndex, x, y) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return ApiaryBlockEntity.isValidForSlot(slotIndex, itemStack);
            }

            @Override
            public boolean isActive() {
                return present;
            }
        };
    }

    private Slot machineSlot(int slotIndex, int x, int y) {
        return new Slot(apiary, slotIndex, x, y) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return ApiaryBlockEntity.isValidForSlot(slotIndex, itemStack);
            }
        };
    }

    public BlockPos apiaryPos() {
        return apiaryPos;
    }

    /// How many apiaries are stacked here. Drives which slots the panels draw.
    public int levels() {
        return levels;
    }

    public int progress() {
        return data.get(ApiaryBlockEntity.DATA_PROGRESS);
    }

    public int progressTotal() {
        return data.get(ApiaryBlockEntity.DATA_PROGRESS_TOTAL);
    }

    /// Production cycles the queen has left before she dies and leaves her brood.
    public int lifespanRemaining() {
        return data.get(ApiaryBlockEntity.DATA_LIFESPAN);
    }

    /// Blocks worth foraging that the hive's last survey found within its territory.
    public int flowers() {
        return data.get(ApiaryBlockEntity.DATA_FLOWERS);
    }

    public boolean queenPresent() {
        return data.get(ApiaryBlockEntity.DATA_QUEEN_PRESENT) != 0;
    }

    /// The three frame slots, for the panel that draws them (see FramePanel). Handed out as
    /// the live Slot objects rather than as their contents: the panel moves them, and moving
    /// a Slot is how a tabbed panel hides one.
    public List<Slot> frameSlots() {
        int start = ApiaryBlockEntity.SLOT_FRAME_START;
        return slots.subList(start, start + ApiaryBlockEntity.FRAME_SLOTS);
    }

    public ItemStack queenStack() {
        return slots.get(ApiaryBlockEntity.SLOT_QUEEN).getItem();
    }

    /// The two species whose cross the next brood will actually be rolled from. Read
    /// straight off the menu's own slots, which the client already has in full -- the
    /// objective panel's status line needs no sync of its own.
    ///
    /// The second one is the subtle half. Once a princess and a drone mate, the drone is
    /// consumed and the queen carries his genome inside her own stack, so reading the drone
    /// slot at that point finds nothing and makes a hive that is busy raising a brood look
    /// like an empty one waiting to be loaded. Asking the queen for her mate is what keeps
    /// the answer true for the whole cycle, not just the moment before mating.
    public @Nullable Identifier crossParentA() {
        return BeeStacks.speciesOf(slots.get(ApiaryBlockEntity.SLOT_QUEEN).getItem());
    }

    public @Nullable Identifier crossParentB() {
        ItemStack queenSlot = slots.get(ApiaryBlockEntity.SLOT_QUEEN).getItem();
        Identifier mate = BeeStacks.mateSpeciesOf(queenSlot);
        return mate != null ? mate : BeeStacks.speciesOf(slots.get(ApiaryBlockEntity.SLOT_DRONE).getItem());
    }

    @Override
    public boolean stillValid(Player player) {
        return apiary.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int inventoryStart = APIARY_SLOTS;
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
