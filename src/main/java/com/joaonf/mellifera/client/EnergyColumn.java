package com.joaonf.mellifera.client;

import java.util.List;

import com.joaonf.mellifera.block.MachineEnergy;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/// The FE buffer, as a stack of cells bolted to the left edge of a machine's window.
///
/// WHY OUTSIDE, AND WHY DRAWN. It used to be a well painted into every machine background, which
/// cost each window its left margin -- the most valuable strip in it -- and tied five textures to
/// one pair of constants. Out here it owns its own space and is drawn rather than painted, so the
/// art and the code cannot drift apart.
///
/// WHAT REPLACED THE BAR. The plate is where the old one was, to the pixel: 14 by 63, aligned with
/// the machine panel, because that placement -- it owns space no slot wants and it reads without being asked. What is
/// different is what fills it. A single red column says "some" and nothing else: with no scale to
/// read against, a fifth full and a third full are the same picture, and the colour belonged to no
/// other part of the machine.
///
/// A stack of thin cells, filling upward. A cell is a unit -- countable at a glance, which a smooth
/// bar never is -- and the dark well showing between them is what keeps the column from reading as
/// one bar with lines drawn across it. Each lit cell carries a bright cap along its top edge.
///
/// Amber rather than red, and the same amber the honey and the progress bar use. This is a mod about
/// bees; a machine window has no business carrying a colour that appears nowhere else in it.
public final class EnergyColumn {
    /// The plate. Its rows are the window's own inner frame, read from the generated geometry rather
    /// than guessed: the column and the machine panel beside it used to be three pixels out of
    /// alignment, which is invisible until the two are seen together and then impossible to unsee.
    private static final int PLATE_WIDTH = 14;
    private static final int PLATE_TOP = MachineGeometry.FRAME.y();
    private static final int PLATE_HEIGHT = MachineGeometry.FRAME.height();
    private static final int WELL_INSET = 3;

    /// The cells: five pixels of pitch, three of them amber and two of seam.
    ///
    /// The first pass divided the well into seven fat cells, which put a six or seven pixel band of
    /// colour in each -- a bar with lines drawn across it. Three pixels reads as a charge sitting in
    /// a cell, and eleven of them up the column give the gauge a scale worth counting against.
    private static final int CELL_PITCH = 5;
    private static final int CELL_BODY = 3;

    private static final int PLATE_FACE = 0xFF504D49;
    private static final int PLATE_LIGHT = 0xFF726E68;
    private static final int PLATE_DARK = 0xFF33312F;
    private static final int BOLT = 0xFFC9A04E;
    private static final int BOLT_DARK = 0xFF8A6A2E;

    private static final int WELL_FACE = 0xFF1D160C;
    private static final int CELL_EMPTY = 0xFF2B2214;
    private static final int CELL_LIT = 0xFFF0A830;
    private static final int CELL_CAP = 0xFFFFDC9A;

    private EnergyColumn() {}

    private static int plateLeft(int guiLeft) {
        return guiLeft - PLATE_WIDTH;
    }

    public static void render(GuiGraphicsExtractor graphics, int guiLeft, int guiTop, int stored) {
        int left = plateLeft(guiLeft);
        int top = guiTop + PLATE_TOP;
        int right = left + PLATE_WIDTH;
        int bottom = top + PLATE_HEIGHT;

        // The plate, lit from the upper left like every other machined part in this window.
        graphics.fill(left, top, right, bottom, PLATE_FACE);
        graphics.fill(left, top, right - 1, top + 1, PLATE_LIGHT);
        graphics.fill(left, top, left + 1, bottom - 1, PLATE_LIGHT);
        graphics.fill(left + 1, bottom - 1, right, bottom, PLATE_DARK);
        graphics.fill(right - 1, top + 1, right, bottom, PLATE_DARK);

        graphics.fill(left + 2, top + 2, left + 3, top + 3, BOLT);
        graphics.fill(right - 3, top + 2, right - 2, top + 3, BOLT);
        graphics.fill(left + 2, bottom - 3, left + 3, bottom - 2, BOLT_DARK);
        graphics.fill(right - 3, bottom - 3, right - 2, bottom - 2, BOLT_DARK);

        int wellLeft = left + WELL_INSET;
        int wellTop = top + WELL_INSET;
        int wellRight = right - WELL_INSET;
        int wellBottom = bottom - WELL_INSET;
        graphics.fill(wellLeft, wellTop, wellRight, wellBottom, WELL_FACE);

        float charge = Math.clamp(stored, 0, MachineEnergy.CAPACITY) / (float) MachineEnergy.CAPACITY;

        // Centred in the well: the well's height is not a whole number of cells, and putting all the
        // slack at one end leaves the last cell looking like a different size from the rest.
        int span = wellBottom - wellTop - 1;
        int cells = span / CELL_PITCH;
        int base = wellBottom - 1 - (span - cells * CELL_PITCH) / 2;

        for (int index = 0; index < cells; index++) {
            int cellBottom = base - index * CELL_PITCH;
            int cellTop = cellBottom - CELL_BODY;

            // A cell lights once the buffer is past its own midpoint, so a buffer with anything in
            // it at all shows one lit cell rather than nothing.
            float share = (index + 1) / (float) cells;
            boolean lit = stored > 0 && charge >= share - 1.0F / (cells * 2);

            graphics.fill(wellLeft + 1, cellTop, wellRight - 1, cellBottom, lit ? CELL_LIT : CELL_EMPTY);
            if (lit) {
                graphics.fill(wellLeft + 1, cellTop, wellRight - 1, cellTop + 1, CELL_CAP);
            }
        }
    }

    /// The whole plate, not just the cells: a pointer anywhere on the thing deserves the reading.
    public static boolean isHovered(int guiLeft, int guiTop, int mouseX, int mouseY) {
        int left = plateLeft(guiLeft);
        int top = guiTop + PLATE_TOP;
        return mouseX >= left && mouseX < left + PLATE_WIDTH
            && mouseY >= top && mouseY < top + PLATE_HEIGHT;
    }

    /// The same gauge read the other way round, for the one block that fills it instead of
    /// emptying it. A generator has no unpowered mode to explain and no cost to state -- what a
    /// player wants off an engine is what it is putting out.
    public static List<Component> generatorTooltip(int stored, int perTick) {
        return List.of(
            Component.translatable("gui.mellifera.energy", stored, MachineEnergy.CAPACITY),
            Component.translatable("gui.mellifera.energy.output", perTick)
                .withStyle(style -> style.withColor(0xAAAAAA)));
    }

    public static List<Component> tooltip(int stored, int perTick) {
        return List.of(
            Component.translatable("gui.mellifera.energy", stored, MachineEnergy.CAPACITY),
            Component.translatable("gui.mellifera.energy.usage", perTick * MachineEnergy.POWERED_SPEED)
                .withStyle(style -> style.withColor(0xAAAAAA)),
            Component.translatable("gui.mellifera.energy.unpowered", MachineEnergy.POWERED_SPEED)
                .withStyle(style -> style.withColor(0xAAAAAA)));
    }

}
