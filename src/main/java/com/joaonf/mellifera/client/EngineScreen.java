package com.joaonf.mellifera.client;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.EngineBlockEntity;
import com.joaonf.mellifera.menu.EngineMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/// The Engine's window: peat going in, honey down the side, and a fire between them.
///
/// The one machine window with nothing coming out of it, because what it makes leaves through the
/// sides of the block. The drive track every other window fills with progress is a fuel gauge here
/// -- it empties rather than fills, which is the only honest way to draw a fire.
public class EngineScreen extends AbstractContainerScreen<EngineMenu> {
    private static final Identifier BACKGROUND =
        Identifier.fromNamespaceAndPath(Mellifera.MODID, "textures/gui/container/engine.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 176;

    private static final MachineGeometry.Rect TRACK = MachineGeometry.ENGINE_TRACK;
    private static final MachineGeometry.Rect TANK = MachineGeometry.ENGINE_TANK;

    /// The fire, hotter than the progress amber the other windows use: this bar is burning rather
    /// than counting.
    private static final int BURN_FILL = 0xFFFF7A18;

    public EngineScreen(EngineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractBackground(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, imageWidth, imageHeight);

        EnergyColumn.render(graphics, x, y, menu.energy());

        renderBurnBar(graphics, x, y);
        HoneyGauge.render(graphics, x, y, TANK, menu.fluid(), EngineBlockEntity.TANK_CAPACITY);
    }

    /// What is left of the current fuel, shrinking from the right the way a furnace's flame burns
    /// down rather than filling the way its arrow fills.
    private void renderBurnBar(GuiGraphicsExtractor graphics, int x, int y) {
        int total = menu.burnTotal();
        if (total <= 0 || menu.burn() <= 0) {
            return;
        }

        int left = Math.round(TRACK.width() * Math.min(1.0F, (float) menu.burn() / total));
        if (left > 0) {
            graphics.fill(x + TRACK.x(), y + TRACK.y(), x + TRACK.x() + left, y + TRACK.y() + TRACK.height(), BURN_FILL);
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractContents(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        if (EnergyColumn.isHovered(x, y, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font,
                EnergyColumn.generatorTooltip(menu.energy(), EngineBlockEntity.FE_PER_TICK), mouseX, mouseY);
            return;
        }

        if (HoneyGauge.isHovered(x, y, TANK, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, Component.translatable("gui.mellifera.engine.tank",
                menu.fluid(), EngineBlockEntity.TANK_CAPACITY), mouseX, mouseY);
        }
    }
}
