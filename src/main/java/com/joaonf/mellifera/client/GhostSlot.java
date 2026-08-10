package com.joaonf.mellifera.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/// A faded item in an empty slot, saying what belongs there.
///
/// The Isolator wants a glass bottle and the Infuser wants pollen, and neither is guessable from an
/// empty square: both machines stall silently without them, which is the one failure a player cannot
/// diagnose by looking. Vanilla answers the same question the same way in the armour slots.
///
/// Drawn from the item rather than painted into the background, so it follows the item's own model and
/// any resource pack over it, and so moving a slot cannot leave its hint behind.
///
/// The fade is a wash of the slot's own interior colour over the icon, in that order -- the same
/// ordering ObjectivePanel relies on for its hover, where a fill submitted after an item lands on top
/// of it. Faint enough to read as a hint and not as an item worth clicking.
public final class GhostSlot {
    /// The machine windows' slot interior (0x907753) at just over half. Anything more opaque and the
    /// icon disappears into the panel; anything less and it reads as a real item.
    private static final int WASH = 0x99907753;

    private static final int SIZE = 16;

    private GhostSlot() {}

    /// `left`/`top` are the item's own corner, which is what a Slot stores.
    public static void render(GuiGraphicsExtractor graphics, int left, int top, ItemStack stack) {
        graphics.fakeItem(stack, left, top);
        graphics.fill(left, top, left + SIZE, top + SIZE, WASH);
    }
}
