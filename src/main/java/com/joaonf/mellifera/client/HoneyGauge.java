package com.joaonf.mellifera.client;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaFluids;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/// The honey in a machine's tank, drawn into the glass column painted into its window.
///
/// One copy for the three machines that have a tank. It was three copies of the same forty lines,
/// which had already drifted: the Squeezer's surface line was a different amber from the
/// Carpenter's, and nothing but opening the two windows in turn would ever have shown it.
///
/// The fluid is drawn from its *own* sprite rather than as a coloured rectangle. A flat fill would
/// be the one place in the game where this fluid does not look like itself, and its colour would
/// have to be kept in step with the texture by hand.
public final class HoneyGauge {
    /// The surface line, and the fallback fill. Read against the fluid sprite rather than picked:
    /// it is the lit edge of a body of honey seen from the side.
    private static final int SURFACE = 0xFFE0A526;

    /// One tile of the fluid sprite, which is what a fluid texture is authored at.
    private static final int TILE = 16;

    private HoneyGauge() {}

    /// Fills `tank` from the bottom up, in window space. `x` and `y` are the window's top-left
    /// corner.
    ///
    /// Anything at all in the tank draws at least one pixel: a machine holding 40 mB of a 4000 mB
    /// tank is not empty, and a gauge that says it is will be reported as a bug.
    public static void render(GuiGraphicsExtractor graphics, int x, int y, MachineGeometry.Rect tank, int stored, int capacity) {
        if (stored <= 0 || capacity <= 0) {
            return;
        }

        int filled = Math.max(1, Math.round(tank.height() * Math.min(1.0F, (float) stored / capacity)));

        int left = x + tank.x();
        int bottom = y + tank.y() + tank.height();
        TextureAtlasSprite sprite = sprite();

        if (sprite == null) {
            // Before the atlas is built there is nothing to read; a flat fill for one frame beats a hole.
            graphics.fill(left, bottom - filled, left + tank.width(), bottom, SURFACE);
        } else {
            for (int offsetX = 0; offsetX < tank.width(); offsetX += TILE) {
                int tileWidth = Math.min(TILE, tank.width() - offsetX);

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

        graphics.fill(left, bottom - filled, left + tank.width(), bottom - filled + 1, SURFACE);
    }

    /// Whether the pointer is over that tank, for the window that wants to put a reading on it.
    public static boolean isHovered(int x, int y, MachineGeometry.Rect tank, int mouseX, int mouseY) {
        return mouseX >= x + tank.x() && mouseX < x + tank.x() + tank.width()
            && mouseY >= y + tank.y() && mouseY < y + tank.y() + tank.height();
    }

    /// The still sprite liquid honey is rendered with in the world, or null before the atlas exists.
    private static @Nullable TextureAtlasSprite sprite() {
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
}
