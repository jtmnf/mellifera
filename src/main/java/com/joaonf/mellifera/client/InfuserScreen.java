package com.joaonf.mellifera.client;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.InfuserBlockEntity;
import com.joaonf.mellifera.menu.InfuserMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.joaonf.mellifera.registry.MelliferaItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class InfuserScreen extends AbstractContainerScreen<InfuserMenu> {
    private static final Identifier BACKGROUND =
        Identifier.fromNamespaceAndPath(Mellifera.MODID, "textures/gui/container/infuser.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 176;

    // Inset 1px inside the groove baked into the background, which spans (51,44)-(82,49) and is
    // centred in the gap between the bee column and the serum bay.
    /// The inside of the drive track painted into the background, generated from the same table
    /// that paints it -- see tools/gen_machine_guis.py.
    private static final MachineGeometry.Rect TRACK = MachineGeometry.INFUSER_TRACK;

    /// Honey, because that is what is being pushed into the bee.
    private static final int BAR_FILL = 0xFFE0A526;

    /// One pip per infusion left in the burning pollen, drawn under the pollen slot in the
    /// way a furnace shows its flame -- fuel you cannot see the level of is fuel a player
    /// stops trusting.
    ///
    /// Just under the slot's cell, and centred in it. Three pixels of air rather than two, so the row
    /// reads as belonging to the slot without touching it; further down still and they were stranded
    /// between the slot and the panel's bottom bevel, belonging to neither.
    ///
    /// Two pixels wide and three tall, which looks arbitrary and is not: the centring is what fixes
    /// the width. Four pips at a pitch of four span 3 + 3*4 = 15 when they are 3 wide, and 15 cannot
    /// sit centred in an 18-wide cell -- the three spare pixels split 2/1 whichever way it is nudged.
    /// At 2 wide the row spans 14, the cell has 4 to give, and it lands 2 either side. The height is
    /// free of that constraint, so it keeps the 3 that made the pips read as pips.
    private static final int PIP_X = 21;
    private static final int PIP_Y = 69;
    private static final int PIP_WIDTH = 2;
    private static final int PIP_HEIGHT = 3;
    private static final int PIP_GAP = 4;
    private static final int PIP_FULL = 0xFFE8C93A;
    private static final int PIP_SPENT = 0xFF6C583C;

    public InfuserScreen(InfuserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractBackground(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, imageWidth, imageHeight);

        EnergyColumn.render(graphics, x, y, menu.energy());

        renderProgressBar(graphics, x, y);
        renderCharges(graphics, x, y);
    }

    private void renderProgressBar(GuiGraphicsExtractor graphics, int x, int y) {
        int total = menu.progressTotal();
        if (total <= 0) {
            return;
        }

        int filled = Math.round(TRACK.width() * Math.min(1.0F, (float) menu.progress() / total));
        if (filled > 0) {
            graphics.fill(x + TRACK.x(), y + TRACK.y(), x + TRACK.x() + filled, y + TRACK.y() + TRACK.height(), BAR_FILL);
        }
    }

    /// Four pips, lit for the infusions still owed by the pollen already burnt.
    private void renderCharges(GuiGraphicsExtractor graphics, int x, int y) {
        int charges = menu.charges();
        for (int i = 0; i < InfuserBlockEntity.CHARGES_PER_POLLEN; i++) {
            int pipX = x + PIP_X + i * PIP_GAP;
            int pipY = y + PIP_Y;
            graphics.fill(pipX, pipY, pipX + PIP_WIDTH, pipY + PIP_HEIGHT, i < charges ? PIP_FULL : PIP_SPENT);
        }
    }

    /// The pollen the machine needs, faded into its slot while that slot is empty. See GhostSlot.
    ///
    /// Positioned off the Slot itself rather than off the menu's constants, so the hint cannot be left
    /// behind when the slot moves.
    private void renderGhost(GuiGraphicsExtractor graphics, int x, int y) {
        Slot slot = menu.getSlot(InfuserBlockEntity.SLOT_POLLEN);
        if (slot.getItem().isEmpty()) {
            GhostSlot.render(graphics, x + slot.x, y + slot.y, new ItemStack(MelliferaItems.POLLEN.get()));
        }
    }

    /// The strip is not a slot, so nothing draws its tooltip for us. Hooking extractContents puts it in
    /// the same pass the slot tooltips use, which is what keeps it above the window and below an item
    /// held on the cursor. The ghost goes in before super, so real items and the hover land over it.
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        renderGhost(graphics, x, y);

        super.extractContents(graphics, mouseX, mouseY, partial);

        if (EnergyColumn.isHovered(x, y, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font,
                EnergyColumn.tooltip(menu.energy(), InfuserBlockEntity.FE_PER_TICK), mouseX, mouseY);
        }
    }
}
