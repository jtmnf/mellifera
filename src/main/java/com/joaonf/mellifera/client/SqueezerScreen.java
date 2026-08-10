package com.joaonf.mellifera.client;

import java.util.List;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.SqueezerBlockEntity;
import com.joaonf.mellifera.menu.SqueezerMenu;

import org.jspecify.annotations.Nullable;
import com.joaonf.mellifera.registry.MelliferaFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
/// The tank is drawn rather than painted, for the same reason the energy strip is (see EnergyStrip):
/// its level moves, so the art would only ever be the empty vessel, and a vessel drawn in code cannot
/// drift out of step with the numbers filling it.
public class SqueezerScreen extends AbstractContainerScreen<SqueezerMenu> {
    private static final Identifier BACKGROUND =
        Identifier.fromNamespaceAndPath(Mellifera.MODID, "textures/gui/container/squeezer.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 176;

    /// The groove between the drop and the tank, matching the Centrifuge's bar in size so the two
    /// machines animate alike.
    private static final int BAR_X = 52;
    private static final int BAR_Y = 46;
    private static final int BAR_WIDTH = 30;
    private static final int BAR_HEIGHT = 4;
    private static final int BAR_FILL = 0xFFE0A526;

    /// The tank, on the right of the machine panel: 24 wide, the height of the panel's interior less a
    /// margin, so it reads as the vessel the machine fills rather than as a second gauge.
    /// Centred in the footprint the Centrifuge's output bay occupies in the shared art -- measured, not
    /// assumed: x 98..151, y 20..73. Guessing that box is exactly how the first attempt left three
    /// painted slots showing beside the tank.
    private static final int TANK_X = 113;
    private static final int TANK_Y = 22;
    private static final int TANK_WIDTH = 24;
    private static final int TANK_HEIGHT = 50;

    /// The plate the bucket bay sits on, past the right edge. Drawn rather than painted, because there is
    /// no texture out there at all -- the same reason the energy strip draws its own.
    private static final int BAY_PLATE_X = 176;
    private static final int BAY_PLATE_Y = 22;
    private static final int BAY_PLATE_WIDTH = 26;
    private static final int BAY_PLATE_HEIGHT = 46;
    private static final int BAY_FRAME_X = 180;

    /// The meniscus, drawn over the fluid's own sprite so the surface reads as a line rather than as the
    /// top of a texture that happens to stop.
    private static final int HONEY_TOP = 0xFFFFC54B;

    /// One tile of the fluid sprite, which is what a fluid texture is authored at.
    private static final int TILE = 16;

    public SqueezerScreen(SqueezerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractBackground(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, imageWidth, imageHeight);

        EnergyStrip.render(graphics, x, y, menu.energy());
        renderBucketBay(graphics, x, y);
        renderProgressBar(graphics, x, y);
        renderTank(graphics, x, y);
    }

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

    /// The vessel and the honey in it, filling upward.
    ///
    /// The honey is the *fluid's own sprite*, tiled, not a coloured rectangle. It was a rectangle first,
    /// and that was wrong twice over: it made the tank the one place in the game where this fluid did not
    /// look like itself, and it meant the colour had to be kept in step with the fluid's by hand. Reading
    /// the sprite means the tank animates with the fluid and follows a resource pack over it.
    private void renderTank(GuiGraphicsExtractor graphics, int x, int y) {
        SideTab.insetRect(graphics, x + TANK_X - 1, y + TANK_Y - 1, TANK_WIDTH + 2, TANK_HEIGHT + 2);

        int filled = Math.round(TANK_HEIGHT * Math.min(1.0F, (float) menu.fluid() / SqueezerBlockEntity.TANK_CAPACITY));
        if (menu.fluid() > 0 && filled == 0) {
            filled = 1;
        }

        if (filled <= 0) {
            return;
        }

        int left = x + TANK_X;
        int bottom = y + TANK_Y + TANK_HEIGHT;
        TextureAtlasSprite sprite = honeySprite();

        if (sprite == null) {
            // Before the atlas is built there is nothing to read; a flat fill for one frame beats a hole.
            graphics.fill(left, bottom - filled, left + TANK_WIDTH, bottom, HONEY_TOP);
        } else {
            for (int offsetX = 0; offsetX < TANK_WIDTH; offsetX += TILE) {
                int tileWidth = Math.min(TILE, TANK_WIDTH - offsetX);

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

        graphics.fill(left, bottom - filled, left + TANK_WIDTH, bottom - filled + 1, HONEY_TOP);
    }

    /// The still sprite the fluid is rendered with in the world, or null before the atlas exists.
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

    /// The bay's plate and the two cells in it. The items themselves are real Slots and the base class
    /// draws them; this is only the furniture they sit in.
    private void renderBucketBay(GuiGraphicsExtractor graphics, int x, int y) {
        SideTab.raisedPanel(graphics, x + BAY_PLATE_X, y + BAY_PLATE_Y,
            BAY_PLATE_WIDTH, BAY_PLATE_HEIGHT, SideTab.Side.RIGHT);
        SideTab.insetSlot(graphics, x + BAY_FRAME_X, y + SqueezerMenu.BAY_IN_Y - 1, 18);
        SideTab.insetSlot(graphics, x + BAY_FRAME_X, y + SqueezerMenu.BAY_OUT_Y - 1, 18);
    }

    private boolean overTank(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x + TANK_X && mouseX < x + TANK_X + TANK_WIDTH
            && mouseY >= y + TANK_Y && mouseY < y + TANK_Y + TANK_HEIGHT;
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

        if (EnergyStrip.isHovered(x, y, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font,
                EnergyStrip.tooltip(menu.energy(), SqueezerBlockEntity.FE_PER_TICK), mouseX, mouseY);
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
