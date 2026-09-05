"""Generates the texture for the Cable.

    python tools/gen_cable_textures.py src/main/resources/assets/mellifera/textures/block

Not part of the Gradle build, and kept for the same reason as every other generator here: the PNG it
writes is otherwise an unmaintainable binary blob.

One texture, used by all three cable models -- the core, the arm and the one held in the hand -- with
each of them taking the part of it their own faces sit over. So the sheet has to read the same way
whichever six-pixel window is cut out of it, which is what rules out a picture and leaves a material:
copper strands running one way, wax banding across them.

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

# The mod's own beeswax, the same tones the comb and the frame are painted in.
WAX = ((0x8A, 0x6A, 0x2E), (0xC9, 0xA0, 0x4E), (0xE8, 0xC3, 0x4A))

# Where the wax bands sit. Two of them, four pixels apart on a sixteen-pixel sheet, so a six-pixel
# window cut anywhere across the texture catches one band and never two.
BANDS = (3, 11)


def rgba(color):
    return (color[0], color[1], color[2], 255)


def generate(out):
    image = np.zeros((SIZE, SIZE, 4), np.uint8)

    # The strands run down the sheet, lit from the left the way every other texture in this mod is
    # lit from the upper left. The tone falls off across the width in four even steps, which is what
    # makes a flat sheet read as a round wire, and every third column is pulled one step back up for
    # the strands themselves. The first pass switched from one four-tone cycle to another halfway
    # across and left a seam down the middle of the wire.
    for x in range(SIZE):
        shade = 3 - (x * len(COPPER)) // SIZE
        if x % 3 == 0:
            shade += 1

        tone = COPPER[min(len(COPPER) - 1, max(0, shade))]
        for y in range(SIZE):
            image[y, x] = rgba(tone)

    # The bands, two pixels each: one lit, one in shadow, so they read as something wrapped around
    # the wire rather than painted onto it.
    for band in BANDS:
        for x in range(SIZE):
            image[band, x] = rgba(WAX[2] if x % 4 else WAX[1])
            image[band + 1, x] = rgba(WAX[0])

    Image.fromarray(image).save("%s/cable.png" % out)
    print("%s/cable.png  16x16  still" % out)


def preview(textures, out, scale_to=12):
    """The sheet magnified, and a strip of it cut to the six pixels the models actually show."""
    sheet = Image.open("%s/cable.png" % textures).convert("RGB")
    window = sheet.crop((5, 0, 11, SIZE))

    canvas = Image.new("RGB", (SIZE * scale_to + window.width * scale_to + 12, SIZE * scale_to + 8), (46, 44, 42))
    canvas.paste(sheet.resize((SIZE * scale_to, SIZE * scale_to), Image.NEAREST), (4, 4))
    canvas.paste(window.resize((window.width * scale_to, SIZE * scale_to), Image.NEAREST), (SIZE * scale_to + 8, 4))
    canvas.save("%s/preview_cable.png" % out)
    print("%s/preview_cable.png" % out)


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "."
    generate(target)
    if len(sys.argv) > 2:
        preview(target, sys.argv[2])
