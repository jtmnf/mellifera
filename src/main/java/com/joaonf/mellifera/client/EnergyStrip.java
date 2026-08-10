package com.joaonf.mellifera.client;

import java.util.List;

import com.joaonf.mellifera.block.MachineEnergy;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/// The FE buffer, as a strip bolted to the left edge of a machine's window.
///
/// WHY OUTSIDE, AND WHY A BARE STRIP. It used to be a well painted into all three machine
/// backgrounds, which cost each window its left margin -- the most valuable strip in it, where a
/// machine's input belongs -- and tied three textures to one pair of constants, so nudging the gauge
/// a pixel meant repainting all of them. Out here it owns its own space and is drawn rather than
/// painted, so the art and the code cannot drift apart.
///
/// A strip and not a panel: there is nothing to open. A sliding tab with a header and a line of text
/// is the right shape for something with contents -- see the Apiary's tabs -- and the wrong shape for
/// a meter, whose whole job is being readable without being asked. The numbers live in the tooltip,
/// which is where a number that is only occasionally interesting belongs.
///
/// Vertical and filling upward, the shape every other mod's power meter has, aligned with the machine
/// panel baked into the background so the two read as one piece of hardware.
public final class EnergyStrip {
    /// The plate, against the window's left edge. Its height and offset match the machine panel in
    /// the backgrounds, whose interior runs from 16 to 78.
    private static final int PLATE_WIDTH = 14;
    private static final int PLATE_TOP = 16;
    private static final int PLATE_HEIGHT = 63;

    /// The well inside the plate: 6 wide with its bevel, inset evenly so the plate frames it.
    private static final int WELL_INSET = 3;
    private static final int WIDTH = 6;
    private static final int HEIGHT = PLATE_HEIGHT - WELL_INSET * 2 - 2;

    /// Deep amber rather than the progress bar's brighter tone, so a glance tells the two apart in a
    /// window that shows both.
    private static final int FILL = 0xFFC2551B;
    private static final int FILL_TOP = 0xFFE0803A;

    private EnergyStrip() {}

    private static int plateLeft(int guiLeft) {
        return guiLeft - PLATE_WIDTH;
    }

    public static void render(GuiGraphicsExtractor graphics, int guiLeft, int guiTop, int stored) {
        int left = plateLeft(guiLeft);
        int top = guiTop + PLATE_TOP;

        SideTab.raisedPanel(graphics, left, top, PLATE_WIDTH, PLATE_HEIGHT, SideTab.Side.LEFT);
        SideTab.insetRect(graphics, left + WELL_INSET, top + WELL_INSET, WIDTH + 2, HEIGHT + 2);

        int clamped = Math.clamp(stored, 0, MachineEnergy.CAPACITY);
        int filled = Math.round(HEIGHT * (float) clamped / MachineEnergy.CAPACITY);

        // A machine with a trickle in the buffer should still show something; rounding it away would
        // make "almost empty" and "dead" look identical.
        if (clamped > 0 && filled == 0) {
            filled = 1;
        }

        if (filled <= 0) {
            return;
        }

        int wellLeft = left + WELL_INSET + 1;
        int wellBottom = top + WELL_INSET + 1 + HEIGHT;
        graphics.fill(wellLeft, wellBottom - filled, wellLeft + WIDTH, wellBottom, FILL);
        graphics.fill(wellLeft, wellBottom - filled, wellLeft + WIDTH, wellBottom - filled + 1, FILL_TOP);
    }

    /// The whole plate, not just the well: a pointer anywhere on the thing deserves the reading.
    public static boolean isHovered(int guiLeft, int guiTop, int mouseX, int mouseY) {
        int left = plateLeft(guiLeft);
        int top = guiTop + PLATE_TOP;
        return mouseX >= left && mouseX < left + PLATE_WIDTH
            && mouseY >= top && mouseY < top + PLATE_HEIGHT;
    }

    /// `perTick` is the machine's cost per tick *of progress*; what the tooltip has to quote is
    /// what the grid actually sees, which is that times the boost the power buys.
    ///
    /// The second line is the important one: without it a player with no power mod installed
    /// reads an empty gauge on a running machine as something broken.
    public static List<Component> tooltip(int stored, int perTick) {
        return List.of(
            Component.translatable("gui.mellifera.energy", stored, MachineEnergy.CAPACITY),
            Component.translatable("gui.mellifera.energy.usage", perTick * MachineEnergy.POWERED_SPEED)
                .withStyle(style -> style.withColor(0xAAAAAA)),
            Component.translatable("gui.mellifera.energy.unpowered", MachineEnergy.POWERED_SPEED)
                .withStyle(style -> style.withColor(0xAAAAAA)));
    }
}
