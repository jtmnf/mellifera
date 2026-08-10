package com.joaonf.mellifera.client;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.BeeTrait;
import com.joaonf.mellifera.block.IsolatorBlockEntity;
import com.joaonf.mellifera.menu.IsolatorMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class IsolatorScreen extends AbstractContainerScreen<IsolatorMenu> {
    private static final Identifier BACKGROUND =
        Identifier.fromNamespaceAndPath(Mellifera.MODID, "textures/gui/container/isolator.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 176;

    // Inset 1px inside the groove baked into the background, which spans (51,44)-(82,49) and is
    // centred in the gap between the bee column and the output bay.
    private static final int BAR_X = 46;
    private static final int BAR_Y = 45;
    private static final int BAR_WIDTH = 30;
    private static final int BAR_HEIGHT = 4;

    /// Four pixels under the groove, and it has to move with it. It is a caption for the bar, so it
    /// reads as one thing with it; six pixels down and it drifted into the output rows' band instead.
    private static final int STAGE_LABEL_Y = 52;

    /// Three quarters, the same reduction the Apiary's status line uses. A caption competing with the
    /// window's own title for weight reads as a second title.
    private static final float STAGE_LABEL_SCALE = 0.75F;

    /// The darkest tone in the Isolator's own GUI palette (0x46321C), shadowless. The
    /// original mid-grey with a drop shadow was unreadable on this panel's light tan: the
    /// shadow muddied the glyphs instead of separating them, and grey belonged to no part
    /// of this machine.
    private static final int STAGE_LABEL_COLOR = 0xFF46321C;

    public IsolatorScreen(IsolatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractBackground(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, imageWidth, imageHeight);

        EnergyStrip.render(graphics, x, y, menu.energy());

        renderProgressBar(graphics, x, y);
        renderStage(graphics, x, y);
    }

    /// Filled in the colour of the trait being pulled out, not a fixed one: the serum coming out of
    /// this machine is that colour, so the bar is a preview of what the run produces.
    private void renderProgressBar(GuiGraphicsExtractor graphics, int x, int y) {
        int total = menu.progressTotal();
        if (total <= 0) {
            return;
        }

        int filled = Math.round(BAR_WIDTH * Math.min(1.0F, (float) menu.progress() / total));
        if (filled > 0) {
            int color = 0xFF000000 | BeeTrait.ALL[Math.floorMod(menu.cursor(), BeeTrait.ALL.length)].liquidColor();
            graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + filled, y + BAR_Y + BAR_HEIGHT, color);
        }
    }

    /// "3 / 8". Without it the machine looks stuck: a bee sits in the slot for half a minute
    /// and only the bar moves, with nothing to say how much of the genome is left.
    private void renderStage(GuiGraphicsExtractor graphics, int x, int y) {
        Component text = Component.translatable("gui.mellifera.isolator.stage", menu.cursor() + 1, BeeTrait.ALL.length);

        // Centred on the groove at the size it is actually drawn. Centring the unscaled width and
        // scaling afterwards would leave the label sitting right of the thing it labels.
        float textX = x + BAR_X + BAR_WIDTH / 2.0F - font.width(text) * STAGE_LABEL_SCALE / 2.0F;

        graphics.pose().pushMatrix();
        graphics.pose().translate(textX, y + STAGE_LABEL_Y);
        graphics.pose().scale(STAGE_LABEL_SCALE, STAGE_LABEL_SCALE);
        graphics.text(font, text, 0, 0, STAGE_LABEL_COLOR, false);
        graphics.pose().popMatrix();
    }

    /// The glass bottle the machine needs, faded into its slot while that slot is empty. See GhostSlot.
    ///
    /// Positioned off the Slot itself rather than off the menu's constants, so the hint cannot be left
    /// behind when the slot moves.
    private void renderGhost(GuiGraphicsExtractor graphics, int x, int y) {
        Slot slot = menu.getSlot(IsolatorBlockEntity.SLOT_BOTTLE);
        if (slot.getItem().isEmpty()) {
            GhostSlot.render(graphics, x + slot.x, y + slot.y, new ItemStack(Items.GLASS_BOTTLE));
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

        if (EnergyStrip.isHovered(x, y, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font,
                EnergyStrip.tooltip(menu.energy(), IsolatorBlockEntity.FE_PER_TICK), mouseX, mouseY);
        }
    }
}
