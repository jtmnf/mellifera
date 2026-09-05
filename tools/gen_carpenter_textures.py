"""Generates the block textures for the Carpenter.

    python tools/gen_carpenter_textures.py src/main/resources/assets/mellifera/textures/block

Not part of the Gradle build, and kept for the same reason as gen_machine_textures.py: the PNGs
it writes are otherwise unmaintainable binary blobs.

Chassis, rivets, recess, grain and light direction are imported from gen_machine_textures rather
than copied -- the Carpenter is the fifth machine on the same bench and the only way to guarantee
it reads as one is to build it out of the same parts.

The panel is a frame on a workbench with a press coming down on it: two uprights and two rails
making the frame itself, and a bar above that descends and lifts. That is deliberately close to
the Squeezer's press, because the two machines really are doing the same kind of thing to the
same fluid -- one squeezes honey out, the other works it in -- and it is the *frame* in the jaws
that says which is which.

The accent is the Infuser's gold. The honey machines are amber and copper and the genetics bench
teal and gold; a machine that spends honey on equipment belongs with the warm half, and gold is
the one already in the set that reads as wax rather than as syrup.
"""
import sys

import numpy as np

from gen_machine_textures import (
    ACCENT, FRAMES, IRON, PANEL, RIVET, RIVET_DARK, SIZE,
    animated, blank_panel, bottom, compose, idle, put, still,
)

SIDE_FRAMETIME = 2
TOP_FRAMETIME = 3


def side_panel(frame, ramp, running):
    """A frame in the jaws of a press, with honey beading along its top rail.

    The stroke is a triangle wave, as the Squeezer's is: the head should come down and lift, not
    hover. The frame itself never moves -- it is the workpiece -- so the only thing travelling is
    the head and the bead of honey it leaves behind.
    """
    panel = blank_panel()

    # Guide rails, so the head is travelling in something.
    for y in range(PANEL):
        put(panel, 0, y, IRON.mid)
        put(panel, PANEL - 1, y, IRON.shadow)

    # The frame on the bed: two uprights and two rails, drawn as an outline so it reads as a frame
    # rather than as a block.
    for x in range(2, PANEL - 2):
        put(panel, x, PANEL - 5, ramp.mid)
        put(panel, x, PANEL - 2, ramp.shadow)
    for y in range(PANEL - 5, PANEL - 1):
        put(panel, 2, y, ramp.light)
        put(panel, PANEL - 3, y, ramp.shadow)

    stroke = frame / FRAMES
    travel = 2.0 * stroke if stroke < 0.5 else 2.0 * (1.0 - stroke)
    head = 1 + int(round(travel * 3)) if running else 1

    for x in range(1, PANEL - 1):
        put(panel, x, head, IRON.spec)
        put(panel, x, head + 1, IRON.mid)
    put(panel, 4, head + 2, IRON.light)
    put(panel, 5, head + 2, IRON.light)

    # Wax worked into the top rail, only in the frames where the head is down on it.
    if running and head >= 3:
        for x in (3, 6, PANEL - 4):
            put(panel, x, PANEL - 5, ramp.spec)

    return panel


def top_panel(frame, ramp, running):
    """The bench from above: a rack of finished frames and the glue pot beside it."""
    panel = blank_panel()

    for y in range(PANEL):
        for x in range(PANEL):
            put(panel, x, y, IRON.shadow if (x + y) % 2 else IRON.mid)

    # Three frames stacked in the rack.
    for slot in range(3):
        top = 1 + slot * 3
        for x in range(1, PANEL - 4):
            put(panel, x, top, ramp.mid)
            put(panel, x, top + 1, ramp.shadow)

    # The pot, which brightens as the machine works -- the one thing on this face that moves.
    pot = ramp.spec if running and (frame // 2) % 2 == 0 else ramp.mid
    for y in range(PANEL - 4, PANEL - 1):
        for x in range(PANEL - 4, PANEL - 1):
            put(panel, x, y, pot if running else ramp.shadow)

    for x, y in ((1, 1), (PANEL - 2, 1), (1, PANEL - 2), (PANEL - 2, PANEL - 2)):
        put(panel, x, y, RIVET if y == 1 else RIVET_DARK)

    return panel


def generate(out):
    ramp = ACCENT["infuser"]
    off = idle(ramp)

    still("%s/carpenter.png" % out, compose(side_panel(0, off, False)))
    still("%s/carpenter_top.png" % out, compose(top_panel(0, off, False)))
    still("%s/carpenter_bottom.png" % out, compose(bottom(off)))

    animated("%s/carpenter_on.png" % out,
             [compose(side_panel(f, ramp, True), ramp) for f in range(FRAMES)], SIDE_FRAMETIME)
    animated("%s/carpenter_top_on.png" % out,
             [compose(top_panel(f, ramp, True), ramp) for f in range(FRAMES)], TOP_FRAMETIME)


def preview(out, scale_to=9):
    """Off beside on, magnified -- the only way to judge whether the press reads at this size."""
    from PIL import Image

    ramp = ACCENT["infuser"]
    off = idle(ramp)
    row = [compose(side_panel(0, off, False)), compose(top_panel(0, off, False)), compose(bottom(off))]
    row += [compose(side_panel(f, ramp, True), ramp) for f in range(0, FRAMES, 2)]
    row += [compose(top_panel(f, ramp, True), ramp) for f in range(0, FRAMES, 2)]

    cell = SIZE * scale_to
    canvas = Image.new("RGB", (len(row) * (cell + 4) + 4, cell + 8), (46, 44, 42))
    for x, face in enumerate(row):
        tile = Image.fromarray(face).convert("RGB").resize((cell, cell), Image.NEAREST)
        canvas.paste(tile, (4 + x * (cell + 4), 4))

    canvas.save(out + "/preview_carpenter.png")
    print("%s/preview_carpenter.png" % out)


if __name__ == "__main__":
    generate(sys.argv[1] if len(sys.argv) > 1 else ".")
    if len(sys.argv) > 2:
        preview(sys.argv[2])
