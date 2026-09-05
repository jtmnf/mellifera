"""Generates the textures for the Pipe.

    python tools/gen_pipe_textures.py src/main/resources/assets/mellifera/textures/block
    python tools/gen_pipe_textures.py <textures> <preview-dir>

Not part of the Gradle build, and kept for the same reason as every other generator here: the PNGs
it writes are otherwise unmaintainable binary blobs.

TWO SHEETS, because the pipe is two pieces of geometry: `pipe` is the shell, and `pipe_fluid` is the
liquid inside it. The shell is iron with a sight glass down the middle of it -- two rows of window in
six rows of round -- and the model marks that texture force_translucent so the glass part renders as
glass rather than as a hole. Enough casing to read as plumbing from across a room, enough window to
watch the honey move.

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

# The skeleton: metal along the top and bottom of the round, a brass band at each end of the sheet
# where a section bolts to the next, and a window between them.
#
# The window is two rows of the six, not four. Four was the first instinct -- more glass, more to see
# -- and it was wrong twice over: a pipe that is mostly transparent has no silhouette, so a run of it
# read as a faint smear rather than as plumbing, and a wide band of near-invisible glass makes the
# thin line of liquid inside it look like a mistake rather than a fill. Two rows of window in four of
# casing is a pipe with a sight glass, which is the thing this is meant to be.
RAIL_ROWS = (0, 1, 4, 5)
WINDOW_ROWS = (2, 3)
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
                # Solid metal: the casing is what gives the pipe its silhouette, and it is the only
                # part of the shell that is not see-through.
                image[y, x] = rgba(tone(ROUND[offset], STEEL))
            elif x in BAND_COLUMNS:
                image[y, x] = rgba(RIVET if offset < 3 else RIVET_DARK)
            elif offset == WINDOW_ROWS[0]:
                # The lit edge of the glass, right under the casing.
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


def collar(brass):
    """The ring where a pipe clamps onto a machine, in two colours.

    Brass where the pipe draws out of that machine and steel where it feeds into it, so a glance down
    a run says which end is which without clicking anything. The Cable has one collar because power
    only ever travels one way through it; fluid does not, so this one has to be read.
    """
    ramp = (RIVET_DARK, RIVET, (0xE6, 0xC0, 0x74)) if brass else STEEL[:3] + (IRON.spec,)

    image = np.zeros((SIZE, SIZE, 4), np.uint8)
    for y in range(SIZE):
        for x in range(SIZE):
            # A groove every fourth pixel around the ring, which is what keeps it from reading as a
            # painted stripe.
            shade = ROUND[offset_at(y)] - (1 if x % 4 == 3 else 0)
            image[y, x] = rgba(tone(shade - 1, ramp))

    return image


def generate(out):
    Image.fromarray(collar(True)).save("%s/pipe_collar_draw.png" % out)
    print("%s/pipe_collar_draw.png  16x16  still" % out)

    Image.fromarray(collar(False)).save("%s/pipe_collar_feed.png" % out)
    print("%s/pipe_collar_feed.png  16x16  still" % out)

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
