package com.joaonf.mellifera.client;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.CentrifugeBlockEntity;
import com.joaonf.mellifera.menu.CentrifugeMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class CentrifugeScreen extends AbstractContainerScreen<CentrifugeMenu> {
    private static final Identifier BACKGROUND =
        Identifier.fromNamespaceAndPath(Mellifera.MODID, "textures/gui/container/centrifuge.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 176;

    // Inset 1px inside the groove baked into the background, which spans (56,45)-(93,50) and is
    // centred in the gap between the input and the output bay.
    private static final int BAR_X = 52;
    private static final int BAR_Y = 46;
    private static final int BAR_WIDTH = 36;
    private static final int BAR_HEIGHT = 4;
    private static final int BAR_FILL = 0xFFE0A526;

    public CentrifugeScreen(CentrifugeMenu menu, Inventory inventory, Component title) {
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
    }

    // Fills left-to-right, like a furnace's arrow.
    private void renderProgressBar(GuiGraphicsExtractor graphics, int x, int y) {
        int total = menu.progressTotal();
        if (total <= 0) {
            return;
        }

        int filled = Math.round(BAR_WIDTH * Math.min(1.0F, (float) menu.progress() / total));
        if (filled > 0) {
            graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + filled, y + BAR_Y + BAR_HEIGHT, BAR_FILL);
        }
    }

    /// The strip is not a slot, so nothing draws its tooltip for us. Hooking extractContents
    /// puts it in the same pass the slot tooltips use, which is what keeps it above the
    /// window and below an item held on the cursor.
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractContents(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        if (EnergyStrip.isHovered(x, y, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font,
                EnergyStrip.tooltip(menu.energy(), CentrifugeBlockEntity.FE_PER_TICK), mouseX, mouseY);
        }
    }
}
