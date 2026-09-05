"""Generates the block textures for the Engine.

    python tools/gen_engine_textures.py src/main/resources/assets/mellifera/textures/block

Not part of the Gradle build, and kept for the same reason as gen_machine_textures.py: the PNGs it
writes are otherwise unmaintainable binary blobs.

Chassis, rivets, recess, grain and light direction are imported from gen_machine_textures rather
than copied -- the Engine is the sixth machine on the same bench and the only way to guarantee it
reads as one is to build it out of the same parts.

The panel is a firebox with a piston standing on it: a grate low down with fire behind it, and a rod
above that rises on the stroke the fire pays for. It is the one machine in the mod that makes
something rather than changing something, and the fire is what says so at a glance -- every other
panel here is a mechanism, and this one is a mechanism with a fire under it.

The accent is its own, and hotter than the four in gen_machine_textures: an engine that burns should
not be mistaken across a room for the Centrifuge's amber. It is also the only one of the six whose
idle state loses most of its colour, because an engine that is not burning has no fire in it -- the
brass and the iron stay, and the flames go out.
"""
import sys

import numpy as np

from gen_machine_textures import (
    ACCENT, FRAMES, IRON, PANEL, RIVET, RIVET_DARK, SIZE,
    animated, blank_panel, bottom, compose, idle, put, still,
)

SIDE_FRAMETIME = 2
TOP_FRAMETIME = 3

# Where the firebox stops and the engine above it starts. Three rows of fire is enough to read as a
# fire at 16 pixels; four crowds the piston out of its own panel.
GRATE_ROW = PANEL - 4


def side_panel(frame, ramp, running):
    """A piston standing on a firebox: fire below the grate, a rod rising above it.

    The stroke is a triangle wave, like the Squeezer's and the Carpenter's -- the rod goes up and
    comes down, and it is the same length of travel every cycle. What is different here is that the
    fire drives it: the flames are tallest at the bottom of the stroke, where the charge has just
    caught, and lowest at the top.
    """
    panel = blank_panel()

    # The case: guide rails either side, lit from the upper left like every other machined part.
    for y in range(PANEL):
        put(panel, 0, y, IRON.mid)
        put(panel, PANEL - 1, y, IRON.shadow)

    # The grate the fire sits behind. Barred rather than solid, or the firebox reads as a drawer.
    for x in range(1, PANEL - 1):
        put(panel, x, GRATE_ROW, IRON.light if x % 2 else IRON.mid)

    stroke = frame / FRAMES
    travel = 2.0 * stroke if stroke < 0.5 else 2.0 * (1.0 - stroke)
    lift = int(round(travel * 2)) if running else 0

    # The rod and its head. Two pixels wide: one reads as a scratch, three as a wall.
    head = 2 + lift
    for y in range(head, GRATE_ROW):
        put(panel, PANEL // 2 - 1, y, IRON.light)
        put(panel, PANEL // 2, y, IRON.mid)

    for x in range(2, PANEL - 2):
        put(panel, x, head, IRON.spec)
        put(panel, x, head + 1, IRON.shadow)

    # The fire, tallest at the bottom of the stroke. Two tones and a lick of the brightest: three
    # flat rows of one colour is a hot plate, not a fire.
    if running:
        height = 3 - lift
        for x in range(1, PANEL - 1):
            put(panel, x, PANEL - 2, ramp.light)
            if height >= 2:
                put(panel, x, PANEL - 3, ramp.mid if x % 2 else ramp.light)
            if height >= 3 and x % 3 == frame % 3:
                put(panel, x, GRATE_ROW + 1, ramp.spec)
    else:
        # Cold: embers under the grate and nothing above it.
        for x in range(1, PANEL - 1):
            put(panel, x, PANEL - 2, ramp.shadow if x % 2 else ramp.mid)

    return panel


def top_panel(frame, ramp, running):
    """The engine from above: a flywheel turning under a bolted plate.

    Drawn as a ring with one lit spoke rather than as scattered highlights. The first pass laid the
    wheel over a checkered plate and the two read as one field of noise -- at 10x10 a mechanism has
    to be the only thing on the face with a shape, or it is not a mechanism.
    """
    panel = blank_panel()

    # A plain plate, lit from the upper left like every other machined part in the family.
    for y in range(PANEL):
        for x in range(PANEL):
            put(panel, x, y, IRON.mid)
    for i in range(PANEL):
        put(panel, i, 0, IRON.light)
        put(panel, 0, i, IRON.light)
        put(panel, i, PANEL - 1, IRON.shadow)
        put(panel, PANEL - 1, i, IRON.shadow)

    # The wheel: a ring two pixels inside the plate, thick enough to survive being a circle drawn on
    # ten pixels. Anything smaller closes into a blob and anything larger touches the rivets.
    centre = (PANEL - 1) / 2.0
    for y in range(PANEL):
        for x in range(PANEL):
            radius = np.hypot(x - centre, y - centre)
            if 2.6 <= radius <= 3.7:
                put(panel, x, y, IRON.shadow if y > centre else IRON.spec)

    # One spoke, and the hub it turns on. The spoke is the whole of the animation: a ring that
    # brightens all over would read as a lamp rather than as a wheel going round.
    angle = (frame / FRAMES) * 2.0 * np.pi
    for reach in (1.6, 3.0):
        put(panel,
            int(round(centre + np.cos(angle) * reach)),
            int(round(centre + np.sin(angle) * reach)),
            ramp.spec if running else IRON.light)

    for x, y in ((4, 4), (5, 4), (4, 5), (5, 5)):
        put(panel, x, y, ramp.mid if running else IRON.shadow)

    for x, y in ((1, 1), (PANEL - 2, 1), (1, PANEL - 2), (PANEL - 2, PANEL - 2)):
        put(panel, x, y, RIVET if y == 1 else RIVET_DARK)

    return panel


def generate(out):
    ramp = ACCENT["engine"]
    off = idle(ramp)

    still("%s/engine.png" % out, compose(side_panel(0, off, False)))
    still("%s/engine_top.png" % out, compose(top_panel(0, off, False)))
    still("%s/engine_bottom.png" % out, compose(bottom(off)))

    animated("%s/engine_on.png" % out,
             [compose(side_panel(f, ramp, True), ramp) for f in range(FRAMES)], SIDE_FRAMETIME)
    animated("%s/engine_top_on.png" % out,
             [compose(top_panel(f, ramp, True), ramp) for f in range(FRAMES)], TOP_FRAMETIME)


def preview(out, scale_to=9):
    """Off beside on, magnified -- the only way to judge whether a three-pixel fire reads as fire."""
    from PIL import Image

    ramp = ACCENT["engine"]
    off = idle(ramp)
    row = [compose(side_panel(0, off, False)), compose(top_panel(0, off, False)), compose(bottom(off))]
    row += [compose(side_panel(f, ramp, True), ramp) for f in range(0, FRAMES, 2)]
    row += [compose(top_panel(f, ramp, True), ramp) for f in range(0, FRAMES, 2)]

    cell = SIZE * scale_to
    canvas = Image.new("RGB", (len(row) * (cell + 4) + 4, cell + 8), (46, 44, 42))
    for x, face in enumerate(row):
        tile = Image.fromarray(face).convert("RGB").resize((cell, cell), Image.NEAREST)
        canvas.paste(tile, (4 + x * (cell + 4), 4))

    canvas.save(out + "/preview_engine.png")
    print("%s/preview_engine.png" % out)


if __name__ == "__main__":
    generate(sys.argv[1] if len(sys.argv) > 1 else ".")
    if len(sys.argv) > 2:
        preview(sys.argv[2])
