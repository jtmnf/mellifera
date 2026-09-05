package com.joaonf.mellifera.client;

import java.util.List;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.SqueezerBlockEntity;
import com.joaonf.mellifera.menu.SqueezerMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/// The Squeezer's window: a drop on the left, an arrow, and the tank on the right.
///
/// The tank level is drawn rather than painted, for the same reason the energy cells are (see EnergyColumn):
/// its level moves, so the art would only ever be the empty vessel, and a vessel drawn in code cannot
/// drift out of step with the numbers filling it.
public class SqueezerScreen extends AbstractContainerScreen<SqueezerMenu> {
    private static final Identifier BACKGROUND =
        Identifier.fromNamespaceAndPath(Mellifera.MODID, "textures/gui/container/squeezer.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 176;

    /// The insides of the track and the tank painted into the background, generated from the same
    /// table that paints them -- see tools/gen_machine_guis.py.
    private static final MachineGeometry.Rect TRACK = MachineGeometry.SQUEEZER_TRACK;
    private static final MachineGeometry.Rect TANK = MachineGeometry.SQUEEZER_TANK;
    private static final int BAR_FILL = 0xFFE0A526;

    /// The plate the bucket bay sits on, past the right edge. Drawn rather than painted, because there is
    /// no texture out there at all -- the same reason the energy cells draw their own plate.
    private static final int BAY_PLATE_X = 176;
    private static final int BAY_PLATE_Y = 22;
    private static final int BAY_PLATE_WIDTH = 26;
    private static final int BAY_PLATE_HEIGHT = 46;
    private static final int BAY_FRAME_X = 180;

    public SqueezerScreen(SqueezerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractBackground(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, imageWidth, imageHeight);

        EnergyColumn.render(graphics, x, y, menu.energy());
        renderBucketBay(graphics, x, y);
        renderProgressBar(graphics, x, y);
        HoneyGauge.render(graphics, x, y, TANK, menu.fluid(), SqueezerBlockEntity.TANK_CAPACITY);
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



    /// The bay's plate and the two cells in it. The items themselves are real Slots and the base class
    /// draws them; this is only the furniture they sit in.
    /// The bay keeps SideTab's dark chrome, and deliberately: it hangs off the window's edge like the
    /// Apiary's tabs do, so it is a panel standing on the screen rather than a well pressed into
    /// wood. Everything inside the window is painted -- see tools/gen_machine_guis.py.
    private void renderBucketBay(GuiGraphicsExtractor graphics, int x, int y) {
        SideTab.raisedPanel(graphics, x + BAY_PLATE_X, y + BAY_PLATE_Y,
            BAY_PLATE_WIDTH, BAY_PLATE_HEIGHT, SideTab.Side.RIGHT);
        SideTab.insetSlot(graphics, x + BAY_FRAME_X, y + SqueezerMenu.BAY_IN_Y - 1, 18);
        SideTab.insetSlot(graphics, x + BAY_FRAME_X, y + SqueezerMenu.BAY_OUT_Y - 1, 18);
    }

    private boolean overTank(int x, int y, int mouseX, int mouseY) {
        return HoneyGauge.isHovered(x, y, TANK, mouseX, mouseY);
    }

    /// Neither the strip nor the tank is a slot, so nothing draws their tooltips for us. Hooking
    /// extractContents puts them in the same pass the slot tooltips use, which is what keeps them above
    /// the window and below an item held on the cursor.
    /// The empty bucket the machine wants, faded into its slot while that slot is empty. See GhostSlot,
    /// and the same hint in InfuserScreen and IsolatorScreen.
    ///
    /// Only the input side gets one. The slot beside it is where the filled bucket comes out, and a
    /// hint there would be an invitation to put something in a slot that only ever gives.
    ///
    /// Positioned off the Slot itself rather than off the menu's constants, so the hint cannot be left
    /// behind when the slot moves.
    private void renderGhost(GuiGraphicsExtractor graphics, int x, int y) {
        Slot slot = menu.getSlot(SqueezerBlockEntity.SLOT_BUCKET_IN);
        if (slot.getItem().isEmpty()) {
            GhostSlot.render(graphics, x + slot.x, y + slot.y, new ItemStack(Items.BUCKET));
        }
    }

    /// The ghost goes in before super, so real items and the hover highlight land over it.
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        int ghostX = (width - imageWidth) / 2;
        int ghostY = (height - imageHeight) / 2;
        renderGhost(graphics, ghostX, ghostY);

        super.extractContents(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        if (EnergyColumn.isHovered(x, y, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font,
                EnergyColumn.tooltip(menu.energy(), SqueezerBlockEntity.FE_PER_TICK), mouseX, mouseY);
            return;
        }

        if (overTank(x, y, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                Component.translatable("fluid.mellifera.honey"),
                Component.translatable("gui.mellifera.squeezer.tank", menu.fluid(), SqueezerBlockEntity.TANK_CAPACITY)
                    .withStyle(style -> style.withColor(0xAAAAAA))), mouseX, mouseY);
        }
    }
}
