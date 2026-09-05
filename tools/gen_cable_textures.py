"""Generates the texture for the Cable.

    python tools/gen_cable_textures.py src/main/resources/assets/mellifera/textures/block
    python tools/gen_cable_textures.py <textures> <preview-dir>   # also writes preview_cable.png

Not part of the Gradle build, and kept for the same reason as every other generator here: the PNG it
writes is otherwise an unmaintainable binary blob.

WHICH WAY THE SHEET RUNS, which is what the first pass got wrong. A cable's side face maps the
*length* of the wire along the texture's X and the way *across* it along Y. So the strands have to
be rows and the wax has to be columns. Drawn the other way round -- strands down the sheet, bands
across it, which is how one would draw a single wire on paper -- the bands came out running the
length of the wire like painted racing stripes and the strands wound around it like a spring.

So: rows are the round of the wire, lit from above like every other texture in this mod, and the wax
bands are columns crossing it. The models cut their windows out of this sheet accordingly, and
cable_core deliberately takes a stretch with no band in it -- a six-pixel cube caught mid-band would
read as a lump of wax with copper edges.

The strands are the conductor and the bands are what stops it shorting on the machine it is bolted
to. That is also the recipe, which is the point: a player who has seen the wire should be able to
guess it is copper and wax before reading the book.
"""
import sys

import numpy as np
from PIL import Image

SIZE = 16

# Copper, darkest to brightest. Vanilla's own unoxidised copper block sits between the second and
# third of these, so a cable run along a copper wall belongs there rather than glowing off it.
COPPER = (
    (0x6B, 0x38, 0x21),
    (0x8E, 0x4C, 0x2C),
    (0xB4, 0x63, 0x37),
    (0xD1, 0x7E, 0x4C),
)

# The mod's own beeswax, in the same three tones the frames and the comb are painted in.
WAX = (
    (0x8A, 0x6A, 0x2E),
    (0xC9, 0xA0, 0x4E),
    (0xE8, 0xC3, 0x4A),
)

# How bright each row across the wire is, as an index into the ramps above.
#
# Six rows, because six pixels is how thick the wire is drawn and every face the models use takes a
# six-row window: lit along the top, falling away to a dark underside. Repeated up the sheet so that
# a window cut anywhere carries one whole round instead of the seam between two.
#
# The first pass repeated it every four rows, and a six-pixel window then showed one and a half
# rounds -- which is not a wire, it is a stack of wires.

# Which columns the wax wraps, and how wide. Placed so the windows the models use each catch what
# they should: the arm's 0-5 catches one band, the inventory rod's full width catches three, and the
# core's 4-10 catches none.
BANDS = (1, 11)
BAND_WIDTH = 2

ROUND_PROFILE = (3, 3, 2, 2, 1, 0)

# Where the window every model face uses starts, so the round below lines up with it exactly.
WINDOW_TOP = 5

# A nick in the copper every seventh pixel *along* the wire. Lengthwise on purpose: anything drawn
# across the wire that is not wax reads as another band, and two kinds of band is one too many.
NICK_PITCH = 7


def rgba(color):
    return (color[0], color[1], color[2], 255)


def clamp(index, ramp):
    return ramp[min(len(ramp) - 1, max(0, index))]


def round_at(y):
    """The round of the wire at this row, aligned so the models' window starts at its lit edge."""
    return ROUND_PROFILE[(y - WINDOW_TOP) % len(ROUND_PROFILE)]


def generate(out):
    image = np.zeros((SIZE, SIZE, 4), np.uint8)

    for y in range(SIZE):
        for x in range(SIZE):
            shade = round_at(y) - (1 if x % NICK_PITCH == 3 else 0)
            image[y, x] = rgba(clamp(shade, COPPER))

    # The bands take the same round as the copper under them -- wax wrapped on a wire is lit by the
    # same light -- so they read as something tied around it rather than a stripe laid over it. The
    # trailing pixel of each is its shadowed side.
    for band in BANDS:
        for offset in range(BAND_WIDTH):
            x = (band + offset) % SIZE
            for y in range(SIZE):
                shade = round_at(y) - 1 - (1 if offset == BAND_WIDTH - 1 else 0)
                image[y, x] = rgba(clamp(shade, WAX))

    Image.fromarray(image).save("%s/cable.png" % out)
    print("%s/cable.png  16x16  still" % out)


def preview(textures, out, scale_to=12):
    """The sheet, the two windows the models cut out of it, and a straight run built from both.

    The core window is the one worth looking at twice. It is the only part of the wire visible where
    two arms meet at a corner, and it is the one that must not carry a band.
    """
    sheet = Image.open("%s/cable.png" % textures).convert("RGB")
    arm = sheet.crop((0, 5, 5, 11))
    core = sheet.crop((4, 5, 10, 11))

    # What a straight run actually looks like from the side: an arm, the junction, the next arm. The
    # rhythm of the banding only exists here -- neither window shows it on its own.
    run = Image.new("RGB", (arm.width * 2 + core.width, arm.height))
    run.paste(arm.transpose(Image.FLIP_LEFT_RIGHT), (0, 0))
    run.paste(core, (arm.width, 0))
    run.paste(arm, (arm.width + core.width, 0))

    tiles = [sheet.resize((SIZE * scale_to, SIZE * scale_to), Image.NEAREST)]
    tiles += [w.resize((w.width * scale_to, w.height * scale_to), Image.NEAREST)
              for w in (arm, core, run)]

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
