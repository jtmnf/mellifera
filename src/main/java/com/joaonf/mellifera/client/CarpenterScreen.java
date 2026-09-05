package com.joaonf.mellifera.client;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.CarpenterRecipe;
import com.joaonf.mellifera.block.CarpenterBlockEntity;
import com.joaonf.mellifera.item.FrameItem;
import com.joaonf.mellifera.menu.CarpenterMenu;
import com.joaonf.mellifera.registry.MelliferaCarpenterRecipes;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaFluids;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/// The Carpenter's window: a frame and an ingredient going in, a finished frame coming out, and the
/// honey it is all paid for down the right-hand side.
///
/// The wells, the drive track and the tank housing are painted into the background by
/// tools/gen_machine_guis.py, which builds all five machine windows out of the same parts. What is
/// drawn here is only what moves: the progress, the honey, the energy, and the hint of what the
/// inputs are about to become.
public class CarpenterScreen extends AbstractContainerScreen<CarpenterMenu> {
    private static final Identifier BACKGROUND =
        Identifier.fromNamespaceAndPath(Mellifera.MODID, "textures/gui/container/carpenter.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 176;

    /// The insides of the track and the glass column painted into the background. Generated from
    /// the same table that paints them -- see tools/gen_machine_guis.py -- so a bar cannot be drawn
    /// over a housing that is a different size.
    private static final MachineGeometry.Rect TRACK = MachineGeometry.CARPENTER_TRACK;
    private static final MachineGeometry.Rect TANK = MachineGeometry.CARPENTER_TANK;

    private static final int BAR_FILL = 0xFFE0A526;
    private static final int HONEY_TOP = 0xFFE0A526;

    /// One tile of the fluid sprite, which is what a fluid texture is authored at.
    private static final int TILE = 16;

    public CarpenterScreen(CarpenterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractBackground(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, imageWidth, imageHeight);

        // Nothing static is drawn here: the background carries it. What is left is what moves.
        EnergyColumn.render(graphics, x, y, menu.energy());

        renderProgressBar(graphics, x, y);
        renderTank(graphics, x, y);
    }

    // Fills left-to-right, like a furnace's arrow.
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

    /// The honey the machine has left to spend, drawn from the fluid's *own* sprite for the same
    /// reason the Squeezer's tank is: a flat rectangle would be the one place in the game where this
    /// fluid does not look like itself, and its colour would have to be kept in step by hand.
    private void renderTank(GuiGraphicsExtractor graphics, int x, int y) {
        int filled = Math.round(TANK.height() * Math.min(1.0F, (float) menu.fluid() / CarpenterBlockEntity.TANK_CAPACITY));
        if (menu.fluid() > 0 && filled == 0) {
            filled = 1;
        }

        if (filled <= 0) {
            return;
        }

        int left = x + TANK.x();
        int bottom = y + TANK.y() + TANK.height();
        TextureAtlasSprite sprite = honeySprite();

        if (sprite == null) {
            // Before the atlas is built there is nothing to read; a flat fill for one frame beats a hole.
            graphics.fill(left, bottom - filled, left + TANK.width(), bottom, HONEY_TOP);
        } else {
            for (int offsetX = 0; offsetX < TANK.width(); offsetX += TILE) {
                int tileWidth = Math.min(TILE, TANK.width() - offsetX);

                for (int tileBottom = bottom; tileBottom > bottom - filled; tileBottom -= TILE) {
                    int tileHeight = Math.min(TILE, tileBottom - (bottom - filled));
                    float u0 = sprite.getU0();
                    float u1 = u0 + (sprite.getU1() - u0) * tileWidth / TILE;
                    float v1 = sprite.getV1();
                    // The clipped tile keeps the *bottom* of the sprite, so the cut lands at the surface.
                    float v0 = v1 - (v1 - sprite.getV0()) * tileHeight / TILE;

                    graphics.blit(sprite.atlasLocation(),
                        left + offsetX, tileBottom - tileHeight,
                        left + offsetX + tileWidth, tileBottom,
                        u0, u1, v0, v1);
                }
            }
        }

        graphics.fill(left, bottom - filled, left + TANK.width(), bottom - filled + 1, HONEY_TOP);
    }

    /// The still sprite liquid honey is rendered with in the world, or null before the atlas exists.
    private static @Nullable TextureAtlasSprite honeySprite() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getModelManager() == null) {
            return null;
        }

        try {
            FluidModel model = minecraft.getModelManager()
                .getFluidStateModelSet()
                .get(MelliferaFluids.HONEY.get().defaultFluidState());
            return model.stillMaterial().sprite();
        } catch (RuntimeException notReadyYet) {
            // getFluidStateModelSet throws until models have baked, which is one frame at worst.
            return null;
        }
    }

    /// Neither the strip nor the gauge is a slot, so nothing draws their tooltips for us. Same hook
    /// the other machine screens use, for the same reason.
    /// What the two inputs are about to become, faded into the empty output slot.
    ///
    /// The machine already knows -- it checks the same table every tick (see
    /// CarpenterBlockEntity.job) -- and until this existed the window gave a player no way to find
    /// out whether the thing they had just dropped in was going to make anything at all. It answers
    /// the repair case too, which is otherwise entirely invisible: a worn frame with nothing beside
    /// it looks exactly like a worn frame in the wrong machine.
    ///
    /// Read off the Slot rather than the menu's constants, so the hint cannot be left behind when a
    /// slot moves.
    private void renderResultGhost(GuiGraphicsExtractor graphics, int x, int y) {
        Slot output = menu.getSlot(CarpenterBlockEntity.SLOT_OUTPUT);
        if (!output.getItem().isEmpty()) {
            return;
        }

        ItemStack frame = menu.getSlot(CarpenterBlockEntity.SLOT_FRAME).getItem();
        ItemStack ingredient = menu.getSlot(CarpenterBlockEntity.SLOT_INGREDIENT).getItem();
        ItemStack result = preview(frame, ingredient);

        if (!result.isEmpty()) {
            GhostSlot.render(graphics, x + output.x, y + output.y, result);
        }
    }

    /// The same two branches the block entity takes: a recipe, or a repair.
    private static ItemStack preview(ItemStack frame, ItemStack ingredient) {
        if (!(frame.getItem() instanceof FrameItem)) {
            return ItemStack.EMPTY;
        }

        if (ingredient.isEmpty()) {
            float wear = frame.getOrDefault(MelliferaDataComponents.FRAME_WEAR.get(), FrameItem.FRESH_WEAR);
            if (wear >= FrameItem.FRESH_WEAR || FrameItem.neverWears(frame)) {
                return ItemStack.EMPTY;
            }

            ItemStack fresh = frame.copyWithCount(1);
            fresh.set(MelliferaDataComponents.FRAME_WEAR.get(), FrameItem.FRESH_WEAR);
            return fresh;
        }

        CarpenterRecipe recipe = MelliferaCarpenterRecipes.find(frame, ingredient);
        return recipe == null ? ItemStack.EMPTY : new ItemStack(recipe.result().get());
    }

    /// The ghost goes in before super, so real items and the hover highlight land over it -- the
    /// same order SqueezerScreen uses.
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        renderResultGhost(graphics, (width - imageWidth) / 2, (height - imageHeight) / 2);

        super.extractContents(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        if (EnergyColumn.isHovered(x, y, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font,
                EnergyColumn.tooltip(menu.energy(), CarpenterBlockEntity.FE_PER_TICK), mouseX, mouseY);
            return;
        }

        if (mouseX >= x + TANK.x() && mouseX < x + TANK.x() + TANK.width()
            && mouseY >= y + TANK.y() && mouseY < y + TANK.y() + TANK.height()) {
            graphics.setTooltipForNextFrame(font, Component.translatable("gui.mellifera.carpenter.tank",
                menu.fluid(), CarpenterBlockEntity.TANK_CAPACITY), mouseX, mouseY);
        }
    }
}
