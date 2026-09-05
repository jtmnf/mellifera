"""Generates the textures for the Pipe.

    python tools/gen_pipe_textures.py src/main/resources/assets/mellifera/textures/block
    python tools/gen_pipe_textures.py <textures> <preview-dir>

Not part of the Gradle build, and kept for the same reason as every other generator here: the PNGs
it writes are otherwise unmaintainable binary blobs.

TWO SHEETS, because the pipe is two pieces of geometry: `pipe` is the glass shell, and `pipe_fluid`
is the liquid inside it. The shell is mostly transparent -- that is the whole point of the block, and
the model marks it force_translucent so it renders as glass rather than as a cutout. What is left of
it is a machined skeleton: iron rails along the length, brass at the seams, and a specular streak
where the light catches the round. Enough to read as a pipe from across a room and thin enough to see
the honey move.

The liquid sheet is deliberately colourless -- a white with the round shaded into it. It is tinted at
runtime by whatever is going through, which is what lets one texture carry this mod's honey and
another mod's lava without either being drawn here. See PipeFluidTintSource.

WHICH WAY THE SHEET RUNS is the Cable's rule and for the Cable's reason: a side face maps the length
of the pipe along the texture's X, so the round of it is rows and anything circling it is columns.
"""
import sys

import numpy as np
from PIL import Image

from gen_machine_textures import IRON, RIVET, RIVET_DARK

SIZE = 16

# The round of the pipe, six rows deep as the Cable's is, aligned to the window the models cut.
ROUND = (3, 2, 2, 1, 1, 0)
WINDOW_TOP = 5

STEEL = (IRON.shadow, IRON.mid, IRON.light, IRON.spec)

# The glass. Barely there: a cold tint over what is behind it, and a highlight along the top of the
# round.
#
# Twice tuned, both times downward. At alpha 54 the honey behind it came out olive -- glass that
# changes the colour of what is in it is a filter, not a window. And the highlight sat at 132, which
# on a six-pixel pipe is a solid white bar across a third of the only clear part: what was meant to
# say "there is glass here" was saying "there is nothing to see here".
GLASS = (0x9E, 0xB4, 0xBC)
GLASS_ALPHA = 16
GLASS_SPEC_ALPHA = 56

# The skeleton: a rail along the lit edge of the round and another along its underside, with a brass
# band at each end of the sheet where a section is bolted to the next.
#
# Two rails out of six rows is a third of the pipe in solid metal, and that is the most it can be.
# Everything between them is the window, and the liquid inside is drawn wide enough to fill it -- see
# the models, where the sleeve is five pixels inside a six-pixel shell rather than four.
RAIL_ROWS = (0, 5)
BAND_COLUMNS = (0, 15)


def rgba(color, alpha=255):
    return (color[0], color[1], color[2], alpha)


def tone(index, ramp):
    return ramp[min(len(ramp) - 1, max(0, index))]


def offset_at(y):
    return (y - WINDOW_TOP) % len(ROUND)


def shell():
    """The glass and the metal that holds it."""
    image = np.zeros((SIZE, SIZE, 4), np.uint8)

    for y in range(SIZE):
        offset = offset_at(y)

        for x in range(SIZE):
            if offset in RAIL_ROWS:
                # Solid metal: the rails are what the eye follows, and they are the only part of the
                # shell that is not see-through.
                image[y, x] = rgba(tone(ROUND[offset], STEEL))
            elif x in BAND_COLUMNS:
                image[y, x] = rgba(RIVET if offset < 3 else RIVET_DARK)
            elif offset == 1:
                image[y, x] = rgba(GLASS, GLASS_SPEC_ALPHA)
            else:
                image[y, x] = rgba(GLASS, GLASS_ALPHA)

    return image


def fluid():
    """The liquid inside, white so it can be tinted to any fluid."""
    image = np.zeros((SIZE, SIZE, 4), np.uint8)

    for y in range(SIZE):
        offset = offset_at(y)
        # The same round as the shell, so the liquid looks like a body inside a tube rather than a
        # flat card behind it. Brightest under the shell's lit edge, darkest along its floor.
        level = (0xFF, 0xE4, 0xC8, 0xA8, 0x8C, 0x70)[offset]

        for x in range(SIZE):
            # A slow ripple along the length. One tone, one pixel: at this size anything more reads
            # as dirt in the pipe.
            ripple = 12 if (x + offset) % 5 == 0 else 0
            value = min(255, level + ripple)
            image[y, x] = (value, value, value, 255)

    return image


def generate(out):
    Image.fromarray(shell()).save("%s/pipe.png" % out)
    print("%s/pipe.png  16x16  still" % out)

    Image.fromarray(fluid()).save("%s/pipe_fluid.png" % out)
    print("%s/pipe_fluid.png  16x16  still" % out)


def preview(textures, out, scale_to=12):
    """The two sheets, and the run they build: glass over honey-coloured liquid.

    The liquid is shown tinted the way the game will tint it, since untinted white says nothing
    about whether the two sheets work together.
    """
    HONEY = (0xE0, 0xA5, 0x26)

    shell_sheet = Image.open("%s/pipe.png" % textures).convert("RGBA")
    fluid_sheet = Image.open("%s/pipe_fluid.png" % textures).convert("RGBA")

    tinted = Image.fromarray(
        (np.array(fluid_sheet, np.float32) * np.array([*[c / 255.0 for c in HONEY], 1.0], np.float32))
        .astype(np.uint8))

    over = Image.alpha_composite(tinted, shell_sheet)

    top, bottom = WINDOW_TOP, WINDOW_TOP + len(ROUND)
    run = over.crop((0, top, SIZE, bottom))

    tiles = [shell_sheet, tinted, over]
    scaled = [t.resize((SIZE * scale_to, SIZE * scale_to), Image.NEAREST) for t in tiles]
    scaled.append(run.resize((run.width * scale_to, run.height * scale_to), Image.NEAREST))

    width = sum(t.width for t in scaled) + 4 * (len(scaled) + 1)
    canvas = Image.new("RGBA", (width, SIZE * scale_to + 8), (46, 44, 42, 255))

    cursor = 4
    for tile in scaled:
        canvas.paste(tile, (cursor, 4), tile)
        cursor += tile.width + 4

    canvas.convert("RGB").save("%s/preview_pipe.png" % out)
    print("%s/preview_pipe.png" % out)


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "."
    generate(target)
    if len(sys.argv) > 2:
        preview(target, sys.argv[2])
