"""Generates the textures for the Fluid Pipe.

    python tools/gen_pipe_textures.py src/main/resources/assets/mellifera/textures/block
    python tools/gen_pipe_textures.py <textures> <preview-dir>

Not part of the Gradle build, and kept for the same reason as every other generator here: the PNGs
it writes are otherwise unmaintainable binary blobs.

FRAMED WINDOWS, NOT BANDS, which is the third attempt and the first one that survives a corner. The
earlier two drew the shell as rows: metal along the top and bottom of the round, glass between them.
That reads on a straight run and falls apart the moment the run turns, because "top" and "bottom"
are directions and a junction has none -- the core between two arms at right angles shows those bands
crossways, so the casing lands over the window and the pipe goes dark exactly where it bends.

So the sheet is drawn as *cells*: one for the junction and one for an arm, each a metal frame a pixel
thick with glass inside it. A frame has no direction. Every face of every piece is the same framed
window whichever way it is turned, a corner reads as a corner, and the liquid behind it shows through
all of them.

FIVE SHEETS. `pipe` is the shell, whose windows are holes. `pipe_liner` is the dark inside seen
through them when the pipe is idle. `pipe_fluid` is the liquid, drawn colourless so it can be tinted
at runtime to whatever is going through -- see PipeFluidTintSource. `pipe_collar_draw` and
`pipe_collar_feed` are the brass and steel rings that say which way a joint works.

THE CELLS the models cut out of these sheets, which must stay in step with the UVs there:
  the junction  columns 5-10, rows 5-10
  an arm        columns 0-5,  rows 5-10
"""
import sys

import numpy as np
from PIL import Image

from gen_machine_textures import IRON, RIVET, RIVET_DARK

SIZE = 16

# The two windows, as (left, top, size).
CELLS = ((5, 5, 6), (0, 5, 6))

# The window in the middle of a frame is a hole, not a pane.
#
# It was glass at alpha 30 and the liquid behind it never appeared, through three attempts at making
# the glass fainter and the sleeve fatter. A hole cannot be argued with: there is nothing in front of
# the liquid to render, blend, sort or tint away. What keeps an empty pipe from being see-through is
# the liner behind it -- a dark tube that is always there, drawn in solid geometry, which the liquid
# covers when there is any.
HOLE = (0, 0, 0, 0)

# The liner: the inside of the pipe, seen through the window when nothing is going through.
LINER = (0x22, 0x20, 0x1E)

# The liquid, from the lit top of the round down to its floor. Four rows, which is what a one-pixel
# frame leaves of a six-pixel cell.
LIQUID = (0xFF, 0xE2, 0xC0, 0x98)


def rgba(color, alpha=255):
    return (color[0], color[1], color[2], alpha)


def tone(index, ramp):
    return ramp[min(len(ramp) - 1, max(0, index))]


def frame(image, left, top, size):
    """One cell of casing: lit along its top and left, in shadow along its bottom and right.

    The same light every other block in this mod is lit by, applied to a ring rather than to a row,
    which is what lets it read the same way whichever face it lands on.
    """
    right = left + size - 1
    bottom = top + size - 1

    for i in range(size):
        image[top, left + i] = rgba(IRON.light)
        image[bottom, left + i] = rgba(IRON.shadow)
        image[top + i, left] = rgba(IRON.light)
        image[top + i, right] = rgba(IRON.shadow)

    # The corners, in the family's own brass.
    image[top, left] = rgba(RIVET)
    image[top, right] = rgba(RIVET_DARK)
    image[bottom, left] = rgba(RIVET_DARK)
    image[bottom, right] = rgba(RIVET_DARK)


def shell():
    """The casing and its glass, one framed cell per piece of the pipe."""
    image = np.zeros((SIZE, SIZE, 4), np.uint8)

    for left, top, size in CELLS:
        for y in range(top + 1, top + size - 1):
            for x in range(left + 1, left + size - 1):
                image[y, x] = HOLE

        frame(image, left, top, size)

    return image


def fluid():
    """The liquid inside, white so it can be tinted to any fluid."""
    image = np.zeros((SIZE, SIZE, 4), np.uint8)

    for left, top, size in CELLS:
        for row in range(size):
            # Shaded down the cell like a body of liquid seen from the side, and painted across the
            # whole cell rather than only the window: the sleeve is wider than the window, so its
            # edges sit behind the frame and must not be a different colour there.
            level = LIQUID[min(len(LIQUID) - 1, max(0, row - 1))]

            for column in range(size):
                ripple = 10 if (column + row) % 5 == 0 else 0
                value = min(255, level + ripple)
                image[top + row, left + column] = (value, value, value, 255)

    return image


def liner():
    """The inside of the pipe: what the window shows when there is nothing going through.

    Dark and plain on purpose. It is a backdrop, and anything with a pattern on it would compete with
    the liquid that is meant to be the thing you notice.
    """
    image = np.zeros((SIZE, SIZE, 4), np.uint8)

    for left, top, size in CELLS:
        for row in range(size):
            for column in range(size):
                shade = 1.0 - row * 0.06
                image[top + row, left + column] = rgba(tuple(int(c * shade) for c in LINER))

    return image


def collar(brass):
    """The ring where a pipe clamps onto a machine, in two colours.

    Brass where the pipe draws out of that machine and steel where it feeds into it, so a glance down
    a run says which end is which without clicking anything. The Cable has one collar because power
    only ever travels one way through it; fluid does not, so this one has to be read.
    """
    ramp = (RIVET_DARK, RIVET, (0xE6, 0xC0, 0x74)) if brass else (IRON.shadow, IRON.mid, IRON.light)

    image = np.zeros((SIZE, SIZE, 4), np.uint8)
    for y in range(SIZE):
        for x in range(SIZE):
            # A groove every fourth pixel around the ring, which keeps a plain band from reading as a
            # painted stripe, and the ring lit from its top like everything else here.
            shade = (2, 2, 1, 1, 0, 0)[y % 6] - (1 if x % 4 == 3 else 0)
            image[y, x] = rgba(tone(shade, ramp))

    return image


def generate(out):
    for name, image in (("pipe", shell()), ("pipe_fluid", fluid()), ("pipe_liner", liner()),
                        ("pipe_collar_draw", collar(True)), ("pipe_collar_feed", collar(False))):
        Image.fromarray(image).save("%s/%s.png" % (out, name))
        print("%s/%s.png  16x16  still" % (out, name))


def preview(textures, out, scale_to=14):
    """Both cells, empty and with liquid behind them, at the size the models actually show them.

    The liquid is tinted the way the game will tint it: white says nothing about whether the two
    sheets work together.
    """
    HONEY = (0xE0, 0xA5, 0x26)

    shell_sheet = Image.open("%s/pipe.png" % textures).convert("RGBA")
    fluid_sheet = Image.open("%s/pipe_fluid.png" % textures).convert("RGBA")
    rings = [Image.open("%s/pipe_collar_%s.png" % (textures, kind)).convert("RGBA")
             for kind in ("draw", "feed")]

    tinted = Image.fromarray(
        (np.array(fluid_sheet, np.float32) * np.array([*[c / 255.0 for c in HONEY], 1.0], np.float32))
        .astype(np.uint8))
    over = Image.alpha_composite(tinted, shell_sheet)

    tiles = []
    for left, top, size in CELLS:
        box = (left, top, left + size, top + size)
        tiles.append(shell_sheet.crop(box))
        tiles.append(over.crop(box))

    tiles += [ring.crop((0, 0, 2, 6)) for ring in rings]

    scaled = [t.resize((t.width * scale_to, t.height * scale_to), Image.NEAREST) for t in tiles]
    width = sum(t.width for t in scaled) + 4 * (len(scaled) + 1)
    height = max(t.height for t in scaled) + 8

    canvas = Image.new("RGBA", (width, height), (46, 44, 42, 255))
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
