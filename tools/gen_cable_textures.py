"""Generates the textures for the Cable.

    python tools/gen_cable_textures.py src/main/resources/assets/mellifera/textures/block
    python tools/gen_cable_textures.py <textures> <preview-dir>   # also writes preview_cable.png

Not part of the Gradle build, and kept for the same reason as every other generator here: the PNGs
it writes are otherwise unmaintainable binary blobs.

WHY IT LOOKS LIKE THIS. The first pass drew the cable as copper braid, which was a fine wire and a
stranger on this bench: the machines it plugs into are iron mechanisms in a wood chassis, bolted
with brass, lit from the upper left. So the palette here is imported from gen_machine_textures
rather than picked -- the same iron, the same brass -- and the cable is a machined conduit that
happens to be six pixels thick rather than a length of flex.

TWO SHEETS. `cable` is the pipe: rolled iron with a bolt every so often along it. `cable_collar` is
the flange where a cable takes hold of a machine, in brass, and it is a separate file because it is
separate geometry -- a one-pixel rim standing proud of the pipe, the way a duct clamps onto a
machine rather than merely touching it.

WHICH WAY THE SHEET RUNS. A cable's side face maps the *length* of the pipe along the texture's X
and the way *across* it along Y. So the round of the pipe is rows, repeating every six -- six being
the thickness it is drawn at -- and anything meant to circle the pipe is columns. Drawn the other
way round, which is how one would draw a wire on paper, the bolts came out as bands and the round
as a spiral.
"""
import sys

import numpy as np
from PIL import Image

from gen_machine_textures import IRON, RIVET, RIVET_DARK

SIZE = 16

# The machines' own iron, darkest first, as a sequence so the round below can index it.
STEEL = (IRON.shadow, IRON.mid, IRON.light, IRON.spec)

# The machines' own brass, with one tone added above it for the lit edge of the flange. Brass is the
# family signature -- four bolts on every machine face -- and the collar is where the cable joins in.
BRASS = (RIVET_DARK, RIVET, (0xE6, 0xC0, 0x74))

# How bright each row across the pipe is, as an index into a ramp: a lit edge below the top, falling
# away to a dark underside. Six rows because the pipe is drawn six pixels thick, repeated up the
# sheet so a window cut anywhere carries one whole round rather than the seam between two.
ROUND = (3, 2, 2, 1, 1, 0)

# Where the window every model face uses starts, so the round lines up with it exactly.
WINDOW_TOP = 5

# A bolt every eight pixels along the run, on the row below the pipe's lit edge. The same thing the
# machines do with the four on each face: it is what makes a straight length read as built rather
# than extruded.
BOLT_PITCH = 8
BOLT_ROW = 1

# The seam of the rolled sheet, seen low on the pipe where the light has gone.
SEAM_ROW = 4


def rgba(color):
    return (color[0], color[1], color[2], 255)


def tone(index, ramp):
    return ramp[min(len(ramp) - 1, max(0, index))]


def round_at(y):
    """The round of the pipe at this row, aligned to the window the models cut."""
    return ROUND[(y - WINDOW_TOP) % len(ROUND)]


def body():
    """The pipe itself: iron, rolled, bolted."""
    image = np.zeros((SIZE, SIZE, 4), np.uint8)

    for y in range(SIZE):
        offset = (y - WINDOW_TOP) % len(ROUND)

        for x in range(SIZE):
            shade = round_at(y)

            # The closed seam: a broken line rather than a solid one, or it reads as something taped
            # along the pipe instead of the pipe's own edge.
            if offset == SEAM_ROW and x % 2 == 0:
                shade -= 1

            image[y, x] = rgba(tone(shade, STEEL))

    for y in range(SIZE):
        if (y - WINDOW_TOP) % len(ROUND) != BOLT_ROW:
            continue

        for x in range(3, SIZE, BOLT_PITCH):
            image[y, x] = rgba(RIVET)
            if y + 1 < SIZE:
                image[y + 1, x] = rgba(RIVET_DARK)

    return image


def collar():
    """The flange, in brass: what stands proud of the pipe where it clamps onto a machine.

    Its own sheet because it is its own geometry -- a one-pixel rim around the pipe, two deep. The
    rim is seen edge-on from every side, so it is grooved *across* rather than along: the ring goes
    round the pipe, and that is the direction it has to read in.
    """
    image = np.zeros((SIZE, SIZE, 4), np.uint8)

    for y in range(SIZE):
        for x in range(SIZE):
            # A darker groove every fourth pixel around the rim, which is what keeps a plain brass
            # ring from reading as a painted stripe.
            shade = round_at(y) - (1 if x % 4 == 3 else 0)
            image[y, x] = rgba(tone(shade - 1, BRASS))

    return image


def generate(out):
    Image.fromarray(body()).save("%s/cable.png" % out)
    print("%s/cable.png  16x16  still" % out)

    Image.fromarray(collar()).save("%s/cable_collar.png" % out)
    print("%s/cable_collar.png  16x16  still" % out)


def preview(textures, out, scale_to=12):
    """Both sheets, and the straight run the models build out of them.

    The run is the only place this can actually be judged: pipe, junction, pipe, with the brass
    flange at each end where it takes hold of a machine.
    """
    pipe = Image.open("%s/cable.png" % textures).convert("RGB")
    ring = Image.open("%s/cable_collar.png" % textures).convert("RGB")

    top, bottom = WINDOW_TOP, WINDOW_TOP + len(ROUND)
    arm = pipe.crop((0, top, 6, bottom))
    core = pipe.crop((5, top, 11, bottom))
    # The flange stands a pixel proud on each side, which is why it is cropped a row taller.
    flange = ring.crop((0, top - 1, 2, bottom + 1))

    run = Image.new("RGB", (flange.width * 2 + arm.width * 2 + core.width, flange.height), (46, 44, 42))
    run.paste(flange, (0, 0))
    run.paste(arm, (flange.width, 1))
    run.paste(core, (flange.width + arm.width, 1))
    run.paste(arm, (flange.width + arm.width + core.width, 1))
    run.paste(flange, (flange.width + arm.width * 2 + core.width, 0))

    tiles = [pipe.resize((SIZE * scale_to, SIZE * scale_to), Image.NEAREST),
             ring.resize((SIZE * scale_to, SIZE * scale_to), Image.NEAREST)]
    tiles += [tile.resize((tile.width * scale_to, tile.height * scale_to), Image.NEAREST)
              for tile in (core, run)]

    width = sum(tile.width for tile in tiles) + 4 * (len(tiles) + 1)
    canvas = Image.new("RGB", (width, SIZE * scale_to + 8), (46, 44, 42))

    cursor = 4
    for tile in tiles:
        canvas.paste(tile, (cursor, 4))
        cursor += tile.width + 4

    canvas.save("%s/preview_cable.png" % out)
    print("%s/preview_cable.png" % out)


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "."
    generate(target)
    if len(sys.argv) > 2:
        preview(target, sys.argv[2])
