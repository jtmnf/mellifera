"""Generates the block textures for the Capacitor.

    python tools/gen_capacitor_textures.py src/main/resources/assets/mellifera/textures/block
    python tools/gen_capacitor_textures.py <textures> <preview-dir>

Not part of the Gradle build, and kept for the same reason as every other generator here: the PNGs
it writes are otherwise unmaintainable binary blobs.

Chassis, rivets, recess, grain and light direction are imported from gen_machine_textures -- the
Capacitor stands on the same bench as the rest and has to read as one of them.

The panel is the energy column out of the machine windows, turned into a block face: a well with a
stack of cells filling upward, amber, each with a lit cap. That is deliberate and it is the whole
design. A player learns what a charge looks like from the gauge inside every machine window in the
mod; putting the same gauge on the outside of the block means the Capacitor needs no window of its
own and no explanation.

Five sheets, one per step of charge, because the block state has five steps. Empty is unlit cells in
a dark well, and the four above it light one more cell each.
"""
import sys

import numpy as np

from gen_machine_textures import (
    ACCENT, IRON, PANEL, RIVET, RIVET_DARK, SIZE,
    blank_panel, bottom, compose, idle, put, still,
)

# The gauge: four cells up the panel, which is the number of steps the block state has and also as
# many as ten pixels of panel will carry.
#
# Two pixels each and no seam between them: the first pass left a pixel of gap and the four cells
# then wanted twelve rows of a ten-row panel, so the top two were clipped away and a full capacitor
# looked like a three-quarters one. What separates them instead is that each cell is a lit cap over
# a darker body, which is the same thing the energy column in the machine windows does.
CELLS = 4
CELL_HEIGHT = 2
CELL_TOP = 1


def side_panel(step, ramp):
    """The well, and `step` of its four cells lit."""
    panel = blank_panel()

    # The well the cells sit in, inset one pixel so the chassis reads as a frame around it.
    for y in range(PANEL):
        for x in range(PANEL):
            put(panel, x, y, IRON.shadow)
    for y in range(1, PANEL - 1):
        for x in range(1, PANEL - 1):
            put(panel, x, y, (0x1D, 0x16, 0x0C))

    for index in range(CELLS):
        # Counted from the bottom, because that is the way a charge fills.
        top = CELL_TOP + (CELLS - 1 - index) * CELL_HEIGHT
        lit = index < step

        for x in range(2, PANEL - 2):
            put(panel, x, top, ramp.spec if lit else (0x3A, 0x2E, 0x1C))
            put(panel, x, top + 1, ramp.mid if lit else (0x2B, 0x22, 0x14))

    for x, y in ((0, 0), (PANEL - 1, 0), (0, PANEL - 1), (PANEL - 1, PANEL - 1)):
        put(panel, x, y, RIVET if y == 0 else RIVET_DARK)

    return panel


def top_panel(step, ramp):
    """The terminals: two brass posts, and the bus between them lit by what is stored."""
    panel = blank_panel()

    for y in range(PANEL):
        for x in range(PANEL):
            put(panel, x, y, IRON.mid if (x + y) % 2 else IRON.shadow)

    for x in range(2, PANEL - 2):
        put(panel, x, PANEL // 2 - 1, ramp.mid if step > 0 else IRON.shadow)
        put(panel, x, PANEL // 2, ramp.spec if step >= CELLS else IRON.shadow)

    for x, y in ((2, 2), (PANEL - 3, 2), (2, PANEL - 3), (PANEL - 3, PANEL - 3)):
        put(panel, x, y, RIVET if y == 2 else RIVET_DARK)

    return panel


def generate(out):
    ramp = ACCENT["centrifuge"]
    off = idle(ramp)

    still("%s/capacitor_bottom.png" % out, compose(bottom(off)))

    for step in range(CELLS + 1):
        tone = ramp if step > 0 else off
        still("%s/capacitor_%d.png" % (out, step), compose(side_panel(step, tone), tone if step else None))
        still("%s/capacitor_top_%d.png" % (out, step), compose(top_panel(step, tone), tone if step else None))


def preview(out, scale_to=9):
    """The five steps side by side, which is the only way to tell whether they read as a scale."""
    from PIL import Image

    ramp = ACCENT["centrifuge"]
    off = idle(ramp)
    row = [compose(side_panel(step, ramp if step else off)) for step in range(CELLS + 1)]
    row += [compose(top_panel(step, ramp if step else off)) for step in (0, CELLS)]
    row.append(compose(bottom(off)))

    cell = SIZE * scale_to
    canvas = Image.new("RGB", (len(row) * (cell + 4) + 4, cell + 8), (46, 44, 42))
    for x, face in enumerate(row):
        tile = Image.fromarray(face).convert("RGB").resize((cell, cell), Image.NEAREST)
        canvas.paste(tile, (4 + x * (cell + 4), 4))

    canvas.save(out + "/preview_capacitor.png")
    print("%s/preview_capacitor.png" % out)


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "."
    generate(target)
    if len(sys.argv) > 2:
        preview(sys.argv[2])
