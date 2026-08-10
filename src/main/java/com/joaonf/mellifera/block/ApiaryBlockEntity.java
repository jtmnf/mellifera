package com.joaonf.mellifera.block;

import com.joaonf.mellifera.menu.ApiaryMenu;
import com.joaonf.mellifera.registry.MelliferaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/// The starter housing, and -- stacked -- the late-game one.
///
/// One apiary on its own is what it always was. Put a second directly on top and the two
/// become one hive with more room in it; a third adds more again. That is how a real hive
/// grows, a brood box with supers stacked over it, and it is why the multiblock is a column
/// rather than a cube: nothing has to be explained to a player who has ever seen a beehive.
///
/// The bottom block is the controller. It owns the inventory, runs the simulation, and is
/// what every block in the column opens; the ones above it are shell. Bottom rather than top
/// because it is the block that stays when the tower is dismantled from above, so the hive
/// survives being shortened instead of losing its contents to whichever block was removed.
///
/// Everything it actually does lives in BeeHousingBlockEntity; this class is the slot layout,
/// the menu, and the arithmetic of how tall it is.
public class ApiaryBlockEntity extends BeeHousingBlockEntity {
    /// Allocated once, for the tallest tower. See BeeHousingBlockEntity.activeFrameSlots for
    /// why the container is sized for the maximum and switched off rather than resized.
    public static final int FRAME_SLOTS = 9;
    public static final int COMB_SLOTS = 12;

    public static final int SLOT_COMB_START = SLOT_FRAME_START + FRAME_SLOTS;
    public static final int TOTAL_SLOTS = SLOT_COMB_START + COMB_SLOTS;

    public static final int MAX_LEVELS = 3;

    /// What each height gives, indexed by levels - 1.
    ///
    /// Frames are three a level, flat: each apiary you stack brings its own row of them,
    /// which is a rule a player can state without looking anything up. Outputs are not linear
    /// because the honeycomb they are drawn in is not -- the lattice only opens up so far.
    private static final int[] FRAMES_BY_LEVEL = {3, 6, 9};
    private static final int[] COMBS_BY_LEVEL = {7, 10, 12};

    /// Production speed per height. A taller hive is a faster one, which is the other half
    /// of what the player is buying.
    private static final float[] SPEED_BY_LEVEL = {1.0F, 1.25F, 1.5F};

    /// How many apiaries are stacked on this one, counting itself. Only meaningful on the
    /// controller; recomputed by ApiaryBlock whenever the column changes, and saved so a
    /// freshly loaded chunk does not run at one level until something touches it.
    private int levels = 1;

    public ApiaryBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.APIARY.get(), pos, state, FRAME_SLOTS, COMB_SLOTS);
    }

    public int levels() {
        return levels;
    }

    /// Static so the client menu can work out the same answer from the height alone, without
    /// a block entity -- the menu is built from a packet, not from the world.
    public static int activeFrameSlots(int levels) {
        return FRAMES_BY_LEVEL[clampLevels(levels) - 1];
    }

    public static int activeOutputSlots(int levels) {
        return COMBS_BY_LEVEL[clampLevels(levels) - 1];
    }

    private static int clampLevels(int levels) {
        return Math.max(1, Math.min(MAX_LEVELS, levels));
    }

    /// Called by ApiaryBlock when the column above this block changes.
    ///
    /// Drops whatever was in the slots the tower has just lost. Doing it here rather than
    /// leaving them switched off with items inside is the whole reason this is a method and
    /// not a setter: an inaccessible slot holding a stack of combs is a bug report waiting to
    /// happen, and the items belong to the player who built the thing.
    public void setLevels(Level level, int newLevels) {
        int clamped = Math.max(1, Math.min(MAX_LEVELS, newLevels));
        if (clamped == levels) {
            return;
        }

        boolean shrinking = clamped < levels;
        levels = clamped;
        if (shrinking) {
            dropInactive(level);
        }

        setChanged();
    }

    @Override
    public int activeFrameSlots() {
        return activeFrameSlots(levels);
    }

    @Override
    public int activeOutputSlots() {
        return activeOutputSlots(levels);
    }

    @Override
    protected float housingSpeedMultiplier() {
        return SPEED_BY_LEVEL[levels - 1];
    }

    /// Only the controller simulates. Every other block in the column has a block entity of
    /// its own -- Vanilla gives one to every block of this type -- and this is what stops a
    /// three-high tower from running three hives in the same space.
    @Override
    protected boolean canRun() {
        return level == null || ApiaryBlock.isController(level, worldPosition);
    }

    /// Spills the contents of every slot the current height does not reach.
    private void dropInactive(Level level) {
        for (int slot = SLOT_FRAME_START + activeFrameSlots(); slot < SLOT_COMB_START; slot++) {
            drop(level, slot);
        }
        for (int slot = SLOT_COMB_START + activeOutputSlots(); slot < TOTAL_SLOTS; slot++) {
            drop(level, slot);
        }
    }

    private void drop(Level level, int slot) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) {
            return;
        }

        setItem(slot, ItemStack.EMPTY);
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), stack);
    }

    /// Static so the client menu enforces the same rule -- see the base class.
    public static boolean isValidForSlot(int slot, ItemStack itemStack) {
        return isValidForSlot(slot, itemStack, FRAME_SLOTS);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mellifera.apiary");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ApiaryMenu(containerId, inventory, this, containerData(), worldPosition, levels);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        levels = Math.max(1, Math.min(MAX_LEVELS, input.getIntOr("levels", 1)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("levels", levels);
    }
}
