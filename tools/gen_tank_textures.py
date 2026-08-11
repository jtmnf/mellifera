"""Generates the block textures for the Tank.

    python tools/gen_tank_textures.py src/main/resources/assets/mellifera/textures/block

Not part of the Gradle build, and kept for the same reason as gen_machine_textures.py: the PNGs
it writes are otherwise unmaintainable binary blobs.

The chassis, the rivets, the recess and the light direction all come straight out of
gen_machine_textures -- imported rather than copied, because the Tank is meant to read as one of
the machines and the only way to guarantee that is to build it out of the same parts. What is
different is the one thing that makes it a tank: where a machine has a lit panel with a mechanism
painted on it, this has a hole.

WHY THE WINDOW IS ACTUALLY TRANSPARENT. The fluid inside is not painted here -- it is drawn in the
world by TankRenderer, out of the fluid's own sprite, so a tank of water looks like water and a
tank of some other mod's fluid looks like that. That only works if the block's own texture lets
you see through it, so the 10x10 panel is written with alpha 0. The game picks a block's render
layer from the transparency of its sprites in this version, so nothing else has to be declared:
a texture with holes in it is enough to get a cutout block, and MelliferaBlocks marks the block
noOcclusion so the faces behind it are still drawn.

The glass itself is four pixels of specular in the upper left, the same cue the Isolator and the
Infuser use for the glass over their panels. At 16x16 anything more becomes a smear, and anything
tinted turns the fluid behind it the wrong colour.
"""
import sys

import numpy as np

from gen_machine_textures import (
    ACCENT, CAVITY_DEEP, IRON, PANEL, RIVET, RIVET_DARK, SIZE,
    blank_panel, compose, idle, put, still,
)

# The two-pixel highlight that says "there is glass here", and the dimmer pixel that follows it.
# Cool grey rather than white: white reads as a hole in the frame, not as a reflection.
SHEEN = (0xC4, 0xD2, 0xDA)
SHEEN_DIM = (0x8E, 0x9E, 0xA6)


def window_panel():
    """The 10x10 window: nothing at all, plus a reflection.

    Fully transparent rather than a dark cavity, because the cavity is exactly what a player is
    supposed to be able to see into. The sheen is drawn as two separate strokes -- a short one at
    the top left corner and a longer diagonal below it -- which at this size is what a pane of
    glass looks like and what one continuous line does not.
    """
    panel = np.zeros((PANEL, PANEL, 4), np.uint8)

    put(panel, 1, 1, SHEEN)
    put(panel, 2, 1, SHEEN_DIM)
    put(panel, 1, 2, SHEEN_DIM)

    for i in range(3):
        put(panel, 2 + i, 6 - i, SHEEN_DIM)

    return panel


def top_panel():
    """The lid: an iron collar around a filling port.

    Opaque, unlike the sides. A tank you can see down into from above is a bucket, and the fluid
    surface TankRenderer draws would then be visible edge-on through the lid at every angle. The
    port is also the one part of the block that says which way is in.
    """
    panel = blank_panel()

    for y in range(PANEL):
        for x in range(PANEL):
            put(panel, x, y, IRON.mid if (x + y) % 2 else IRON.shadow)

    centre = PANEL / 2.0
    for y in range(PANEL):
        for x in range(PANEL):
            radius = np.hypot(x + 0.5 - centre, y + 0.5 - centre)
            if radius > 3.6:
                continue
            if radius > 2.6:
                # The collar, lit from the upper left like every other round thing in this set.
                dx, dy = x + 0.5 - centre, y + 0.5 - centre
                lit = (-dx - dy) / max(radius, 0.001)
                put(panel, x, y, IRON.spec if lit > 0.6 else (IRON.light if lit > 0.0 else IRON.mid))
                continue

            put(panel, x, y, CAVITY_DEEP)

    # Four bolts holding the collar down, at the panel's corners rather than the block's -- the
    # chassis already has its own set two pixels further out.
    for x, y in ((1, 1), (PANEL - 2, 1), (1, PANEL - 2), (PANEL - 2, PANEL - 2)):
        put(panel, x, y, RIVET if y == 1 else RIVET_DARK)

    return panel


def bottom_panel():
    """The floor: the same iron as the lid with a drain in the middle of it.

    Deliberately not the machines' wooden underside. A tank is a vessel, and the one face a player
    sees when they mine it out from below should say the inside is metal.
    """
    panel = blank_panel()

    for y in range(PANEL):
        for x in range(PANEL):
            put(panel, x, y, IRON.shadow if (x + y) % 2 else IRON.mid)

    centre = PANEL / 2.0
    for y in range(PANEL):
        for x in range(PANEL):
            radius = np.hypot(x + 0.5 - centre, y + 0.5 - centre)
            if radius < 1.5:
                put(panel, x, y, CAVITY_DEEP)
            elif radius < 2.3:
                put(panel, x, y, IRON.light)

    for x, y in ((1, 1), (PANEL - 2, 1), (1, PANEL - 2), (PANEL - 2, PANEL - 2)):
        put(panel, x, y, RIVET if y == 1 else RIVET_DARK)

    return panel


def generate(out):
    # The accent is the Squeezer's copper, and only reaches the recess lip: the Tank is the
    # Squeezer's other half, and the two standing side by side should be visibly a pair.
    lip = idle(ACCENT["squeezer"])

    still("%s/tank.png" % out, compose(window_panel(), lip))
    still("%s/tank_top.png" % out, compose(top_panel()))
    still("%s/tank_bottom.png" % out, compose(bottom_panel()))


def preview(out, scale_to=9):
    """The three faces magnified, on a checkerboard so the transparent window reads as
    transparent rather than as black."""
    from PIL import Image

    lip = idle(ACCENT["squeezer"])
    faces = [compose(window_panel(), lip), compose(top_panel()), compose(bottom_panel())]

    cell = SIZE * scale_to
    canvas = Image.new("RGB", (len(faces) * (cell + 4) + 4, cell + 8), (46, 44, 42))
    for x, face in enumerate(faces):
        checks = Image.new("RGB", (SIZE, SIZE))
        for py in range(SIZE):
            for px in range(SIZE):
                checks.putpixel((px, py), (0xC0, 0x30, 0x90) if (px // 2 + py // 2) % 2 else (0x40, 0x10, 0x30))
        tile = Image.fromarray(face)
        checks.paste(tile.convert("RGB"), (0, 0), tile)
        canvas.paste(checks.resize((cell, cell), Image.NEAREST), (4 + x * (cell + 4), 4))

    canvas.save(out + "/preview_tank.png")
    print("%s/preview_tank.png" % out)


if __name__ == "__main__":
    generate(sys.argv[1] if len(sys.argv) > 1 else ".")
    if len(sys.argv) > 2:
        preview(sys.argv[2])
