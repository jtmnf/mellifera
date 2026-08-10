"""Generates the block textures for the four machines: Centrifuge, Squeezer, Isolator, Infuser.

    python tools/gen_machine_textures.py src/main/resources/assets/mellifera/textures/block

Not part of the Gradle build, and kept for the same reason as gen_honey_textures.py: the PNGs
it writes are otherwise unmaintainable binary blobs. This file is what "the machine textures"
actually are, and retuning them means editing a palette or a panel function and running it
again.

What the previous set got wrong, and what this one is built to fix:

  Silhouette.  All four machines were the same wooden frame around the same recessed window
  with the same vertical bars in it, separated only by hue. At any distance a Centrifuge and
  an Isolator were the same block, and nothing on any of them said what the machine did. Here
  the chassis is deliberately shared -- it is what marks the four as one family, and as this
  mod's machines rather than some tech mod's -- but the panel inside it is a different
  mechanism per machine: a spinning drum, a press, a scanned gene strip, a filling funnel.

  Light direction.  The old frame was a symmetric outline with light and dark blobs scattered
  over it. Minecraft's convention, which every vanilla block follows, is a single light from
  the upper left: top and left faces catch it, bottom and right fall away. That is the whole
  reason a 16x16 square reads as a solid object, and it is applied here from one signed
  expression (see `chassis`) rather than pixel by pixel, so it cannot drift out of agreement
  between textures.

  Grain.  The old machines were flat fills -- ten colours in large uniform blocks -- while the
  Apiary and the hives beside them had wood grain. That mismatch is most of what made them
  read as placeholder art. One fixed grain field, generated once and shared by every texture
  here, breaks up the chassis without patterning it.

  Motion.  The old "on" frames recoloured the same bars at random, so a running machine
  flickered instead of working. Every animation here is a loop with a direction: the drum
  turns, the press descends and lifts, the scan line sweeps, the funnel drips. Frame counts
  divide their cycles exactly, so nothing jumps at the wrap.

The four accents are chosen so that the two machines that handle honey read warm (amber for
the Centrifuge, matching the fluid; copper for the Squeezer) and the two that handle genes read
as a pair pulling in opposite directions: teal for the Isolator, which takes a gene out, gold
for the Infuser, which puts one back.
"""
import sys
from collections import namedtuple

import numpy as np
from PIL import Image

SIZE = 16

# Eight frames per animation. Every cycle below -- three-spoke rotation, press stroke, scan
# sweep, drip -- is written as a function of frame/FRAMES so it closes exactly on the wrap.
FRAMES = 8

# The recessed panel: a 10x10 window inset three pixels, which leaves a two-pixel chassis rail
# plus its one-pixel lip on every side.
PANEL = 10
INSET = 3

# Fixed, so regenerating produces the same textures rather than a gratuitous asset diff.
RNG = np.random.default_rng(20260811)

# One grain field for the whole set. Shared rather than per-texture on purpose: the chassis is
# supposed to be the same object four times, and a different grain on each would quietly deny
# that even though no player could name why.
GRAIN = RNG.random((SIZE, SIZE))

Ramp = namedtuple("Ramp", "shadow mid light spec")

# Sampled off the Apiary and the wild hives so the machines sit in the same workshop as the
# rest of the mod. Ordered dark to light; the shading below indexes into it.
WOOD = [
    (0x4A, 0x37, 0x1B),
    (0x63, 0x4B, 0x26),
    (0x79, 0x5C, 0x2F),
    (0x92, 0x76, 0x3D),
    (0xA9, 0x86, 0x49),
    (0xBB, 0x93, 0x54),
    (0xD8, 0xB4, 0x72),
]
WOOD_NEUTRAL = 3

CAVITY = (0x2A, 0x20, 0x12)
CAVITY_DEEP = (0x1D, 0x16, 0x0C)

# The brass the machines are bolted together with. Four rivets on every face is the second half
# of the family signature, after the chassis itself.
RIVET = (0xC9, 0xA0, 0x4E)
RIVET_DARK = (0x8A, 0x6A, 0x2E)

# Cold iron for the working parts -- drum rim, press rails, gene rack. The old textures were
# wood and nothing else, which is why blocks that run on Forge Energy read as crates.
IRON = Ramp((0x33, 0x31, 0x2F), (0x50, 0x4D, 0x49), (0x72, 0x6E, 0x68), (0x9E, 0x9A, 0x92))

ACCENT = {
    "centrifuge": Ramp((0x6B, 0x3F, 0x0C), (0xB0, 0x6A, 0x14), (0xF0, 0xA8, 0x30), (0xFF, 0xDC, 0x9A)),
    "squeezer": Ramp((0x5E, 0x2E, 0x14), (0x9A, 0x4C, 0x22), (0xD8, 0x77, 0x33), (0xF6, 0xB4, 0x78)),
    "isolator": Ramp((0x18, 0x42, 0x3E), (0x2C, 0x77, 0x6E), (0x56, 0xC8, 0xBA), (0xB6, 0xF0, 0xE8)),
    "infuser": Ramp((0x5A, 0x46, 0x12), (0x9A, 0x7E, 0x2C), (0xE8, 0xC3, 0x4A), (0xFF, 0xE9, 0xA0)),
}

# How far the accent falls back when the machine is idle. Not zero: an idle machine still has
# brass and honey in it, it is only unlit. Pulling it to a flat grey instead would make the two
# states different blocks rather than one block switched off.
IDLE = 0.68


def scale(color, factor):
    return tuple(min(255, max(0, int(round(component * factor)))) for component in color)


def blend(under, over, amount):
    return tuple(int(round(u + (o - u) * amount)) for u, o in zip(under, over))


def idle(ramp):
    """The same ramp with the light knocked out of it, for the off state."""
    return Ramp(*(scale(tone, IDLE) for tone in ramp))


def rgba(color):
    return (color[0], color[1], color[2], 255)


def put(target, x, y, color):
    """Bounds-checked, so a shape can be written in its own coordinates and clipped by the
    panel edge instead of every caller carrying the arithmetic."""
    if 0 <= x < target.shape[1] and 0 <= y < target.shape[0]:
        target[y, x] = rgba(color)


def chassis():
    """The wooden case, identical on every face of every machine.

    The shading is one signed expression: a pixel on the top rail gets +2, on the left rail +1,
    on the bottom -2, on the right -1, and the corners get the sum, which is what produces a
    real cube corner rather than the four scattered blobs the old textures had. Two steps for
    the horizontal rails against one for the vertical is not symmetric on purpose -- a surface
    facing up catches more of an overhead light than one facing sideways.
    """
    img = np.zeros((SIZE, SIZE, 4), np.uint8)

    for y in range(SIZE):
        for x in range(SIZE):
            vertical = 1 if y <= 1 else (-1 if y >= SIZE - 2 else 0)
            horizontal = 1 if x <= 1 else (-1 if x >= SIZE - 2 else 0)
            level = WOOD_NEUTRAL + 2 * vertical + horizontal

            # Grain last, so it rides on top of the shading instead of competing with it. The
            # thresholds are deliberately far apart: about a fifth of the pixels move, which is
            # enough to kill the flatness and not enough to become speckle on a two-pixel rail.
            if GRAIN[y, x] < 0.10:
                level -= 1
            elif GRAIN[y, x] > 0.91:
                level += 1

            img[y, x] = rgba(WOOD[min(len(WOOD) - 1, max(0, level))])

    for x, y in ((1, 1), (SIZE - 2, 1), (1, SIZE - 2), (SIZE - 2, SIZE - 2)):
        put(img, x, y, RIVET if y == 1 else RIVET_DARK)

    return img


def recess(img, accent=None):
    """Cuts the window into a chassis and walls it.

    A hole lit from the upper left has its top and left inner walls in shadow and its bottom
    and right walls in the light -- the opposite of the block's outer faces, and the cue that
    says "into" rather than "onto". `accent`, when the machine is running, bleeds a little of
    its colour onto the lit walls, which is what makes the glow look like it is coming out of
    the panel instead of being painted inside it.
    """
    lip_dark = WOOD[0]
    lip_light = WOOD[5]
    if accent is not None:
        lip_light = blend(lip_light, accent.light, 0.34)
        lip_dark = blend(lip_dark, accent.shadow, 0.34)

    low, high = INSET - 1, INSET + PANEL
    for i in range(low, high + 1):
        put(img, i, low, lip_dark)
        put(img, low, i, lip_dark)
        put(img, i, high, lip_light)
        put(img, high, i, lip_light)

    for y in range(INSET, INSET + PANEL):
        for x in range(INSET, INSET + PANEL):
            img[y, x] = rgba(CAVITY)


def compose(panel, accent=None):
    """Chassis plus a finished 10x10 panel."""
    img = chassis()
    recess(img, accent)
    img[INSET:INSET + PANEL, INSET:INSET + PANEL] = panel
    return img


def blank_panel(deep_rows=()):
    panel = np.zeros((PANEL, PANEL, 4), np.uint8)
    panel[:, :] = rgba(CAVITY)
    for y in deep_rows:
        panel[y, :] = rgba(CAVITY_DEEP)
    return panel


def glass(panel):
    """Two pixels of specular in the upper left corner. It is the whole of "there is glass in
    front of this", and at 16x16 anything more becomes a smear."""
    put(panel, 1, 1, (0x8E, 0x9E, 0xA6))
    put(panel, 2, 1, (0x6A, 0x76, 0x7C))
    put(panel, 1, 2, (0x6A, 0x76, 0x7C))


# --------------------------------------------------------------------------------------- side

def centrifuge_side(frame, ramp, running):
    """A drum seen end-on, turning.

    Three spokes, and eight frames that carry them through exactly one third of a turn: because
    the spokes are indistinguishable, a third of a turn is a whole visual cycle, so the loop
    closes without the jump a full revolution in eight steps would give. Honey collects in the
    bottom of the drum and only rises while it is running.
    """
    panel = blank_panel()
    centre = PANEL / 2.0
    turn = 2.0 * np.pi / 3.0 * (frame / FRAMES)

    for y in range(PANEL):
        for x in range(PANEL):
            dx, dy = x + 0.5 - centre, y + 0.5 - centre
            radius = np.hypot(dx, dy)

            if radius > 4.7:
                continue
            if radius > 3.6:
                # The rim: iron, lit at the top left and dark at the bottom right like any
                # other round thing under this light. Two tones and a short specular arc, not
                # three even bands -- a one-pixel ring cut into thirds comes out as splotches.
                lit = (-dx - dy) / max(radius, 0.001)
                put(panel, x, y, IRON.spec if lit > 0.6 else (IRON.light if lit > 0.0 else IRON.mid))
                continue

            # The drum is darker than the rest of the cavity, which is what lets three
            # one-pixel spokes carry the whole shape at this size.
            put(panel, x, y, CAVITY_DEEP)

            # Honey thrown against the wall rather than pooled in the bottom. A pool competed
            # with the spokes for the middle of a ten-pixel circle and the two together read as
            # mud; a crescent lining the rim stays out of their way and is the better cue
            # anyway, since flinging the liquid outward is what the machine does.
            if radius > 2.7 and y + 0.5 > (4.4 if running else 6.4):
                put(panel, x, y, ramp.light if running else ramp.mid)
                continue

            if radius < 1.3:
                put(panel, x, y, IRON.spec if radius < 0.8 else IRON.light)
                continue

            # Spokes as arms of constant pixel width, measured perpendicular to the arm rather
            # than as an angular wedge: a wedge is wide near the hub and one pixel at the rim,
            # which at this size came out as three blobs instead of three spokes.
            for spoke in range(3):
                angle = turn + spoke * 2.0 * np.pi / 3.0
                along = dx * np.cos(angle) + dy * np.sin(angle)
                across = dx * -np.sin(angle) + dy * np.cos(angle)
                if along > 0.0 and abs(across) < 0.62:
                    put(panel, x, y, ramp.spec if running else ramp.light)
                    break

    return panel


def squeezer_side(frame, ramp, running):
    """A press: plate above, comb below, and a stroke that ends in a squirt.

    The stroke is a triangle wave rather than a sine -- the plate should slam and lift, not
    hover. The drips only exist in the two frames around the bottom of the stroke, so they read
    as caused by it.
    """
    panel = blank_panel()

    # Guide rails, so the plate is travelling in something.
    for y in range(PANEL):
        put(panel, 0, y, IRON.mid)
        put(panel, PANEL - 1, y, IRON.shadow)

    stroke = frame / FRAMES
    travel = 2.0 * stroke if stroke < 0.5 else 2.0 * (1.0 - stroke)
    plate = 1 + int(round(travel * 4)) if running else 1

    for y in range(PANEL - 3, PANEL):
        for x in range(1, PANEL - 1):
            comb = ramp.mid if (x + y) % 2 else ramp.shadow
            put(panel, x, y, comb)
    for x in range(1, PANEL - 1):
        put(panel, x, PANEL - 3, ramp.light if running else ramp.mid)

    for x in range(1, PANEL - 1):
        put(panel, x, plate, IRON.spec)
        put(panel, x, plate + 1, IRON.mid)
    put(panel, 4, plate - 1, IRON.light)
    put(panel, 5, plate - 1, IRON.light)

    if running and plate >= 4:
        for x, y in ((1, PANEL - 4), (PANEL - 2, PANEL - 4), (2, PANEL - 5)):
            put(panel, x, y, ramp.spec)

    return panel


def isolator_side(frame, ramp, running):
    """A gene strip behind glass with a reader sweeping it.

    The strip is a fixed ladder of ticks -- the same every frame, because it is the bee's
    genome and it is not changing. What moves is the one bright column crossing it, which is
    the machine doing the work, and it crosses in exactly FRAMES steps.
    """
    panel = blank_panel(deep_rows=(3, 4, 5, 6))

    for x in range(1, PANEL - 1):
        put(panel, x, 2, IRON.mid)
        put(panel, x, PANEL - 3, IRON.shadow)

    # The ladder: paired alleles, one row each, with gaps that make it read as data.
    for x in range(1, PANEL - 1):
        if x % 3 != 0:
            put(panel, x, 4, ramp.mid)
        if (x + 1) % 3 != 0:
            put(panel, x, 6, ramp.shadow)

    if running:
        column = 1 + int(frame / FRAMES * (PANEL - 2))
        for y in range(3, PANEL - 3):
            put(panel, column, y, ramp.light)
        put(panel, column, 4, ramp.spec)
        put(panel, column, 6, ramp.spec)
        # The extracted gene leaving downward, one pixel, in the frame after the reader passes.
        put(panel, column - 1 if column > 1 else PANEL - 2, PANEL - 2, ramp.light)

    glass(panel)
    return panel


def infuser_side(frame, ramp, running):
    """A funnel of serum over a socket, dripping into it.

    Deliberately the Isolator upside down in intent: the Isolator moves a bright pixel across
    and out, this one moves it down and in. The funnel is drawn as an outline rather than a
    solid so it reads as a vessel with something in it.
    """
    panel = blank_panel()

    for x in range(1, PANEL - 1):
        put(panel, x, 1, IRON.light)
    for x in range(2, PANEL - 2):
        put(panel, x, 2, ramp.mid)
    for x in range(3, PANEL - 3):
        put(panel, x, 3, ramp.light if running else ramp.mid)
    for x in range(4, PANEL - 4):
        put(panel, x, 4, ramp.spec if running else ramp.light)
    put(panel, 1, 1, IRON.spec)
    put(panel, 2, 2, IRON.mid)
    put(panel, PANEL - 3, 2, IRON.shadow)

    # The socket the bee sits in.
    for x in range(2, PANEL - 2):
        put(panel, x, PANEL - 2, IRON.mid)
    for x in range(3, PANEL - 3):
        put(panel, x, PANEL - 3, CAVITY_DEEP)
    put(panel, 2, PANEL - 3, IRON.light)
    put(panel, PANEL - 3, PANEL - 3, IRON.shadow)

    if running:
        drop = 5 + int(frame / FRAMES * 3.0) % 3
        put(panel, 4, drop, ramp.spec)
        put(panel, 5, drop, ramp.light)
        if drop >= 7:
            put(panel, 4, PANEL - 3, ramp.spec)
            put(panel, 5, PANEL - 3, ramp.light)

    glass(panel)
    return panel


# ---------------------------------------------------------------------------------------- top

def centrifuge_top(frame, ramp, running):
    """The rotor from above: four comb slots on a turning carrier.

    Four slots this time, so the loop is a quarter turn -- the same trick as the side, and it
    also means the top and the side are visibly the same machine turning without the two having
    to agree on an exact angle, which at this size nobody can read anyway.
    """
    panel = blank_panel()
    centre = PANEL / 2.0
    turn = 2.0 * np.pi / 4.0 * (frame / FRAMES)

    for y in range(PANEL):
        for x in range(PANEL):
            dx, dy = x + 0.5 - centre, y + 0.5 - centre
            radius = np.hypot(dx, dy)
            if radius > 4.7:
                continue
            if radius > 4.0:
                put(panel, x, y, IRON.light if dx + dy < 0 else IRON.mid)
                continue
            if radius < 1.1:
                put(panel, x, y, IRON.spec if running else IRON.light)
                continue

            angle = (np.arctan2(dy, dx) - turn) % (2.0 * np.pi)
            sector = int(angle / (np.pi / 2.0))
            within = angle - sector * (np.pi / 2.0)
            if within < 0.24 or within > np.pi / 2.0 - 0.24:
                put(panel, x, y, IRON.mid)
            else:
                # All four slots the same. Alternating them, or leaving two empty, gave the
                # rotor a two-fold shape that flipped rather than turned; four identical slots
                # with the dividers between them is what actually reads as rotation.
                put(panel, x, y, ramp.light if running else ramp.mid)

    return panel


def squeezer_top(frame, ramp, running):
    """The press head from above: a bolted plate with a screw in it.

    Descent is invisible from directly above, so the motion here is the screw: a cross that
    steps through a quarter turn, plus the plate breathing one tone as the stroke bottoms out.
    """
    panel = blank_panel()
    stroke = frame / FRAMES
    load = 2.0 * stroke if stroke < 0.5 else 2.0 * (1.0 - stroke)

    # A one-pixel gutter of comb all round the plate: the material being pressed, and the only
    # colour on an otherwise iron face.
    gutter = ramp.shadow if not running else blend(ramp.shadow, ramp.light, load)
    for i in range(PANEL):
        for j in range(PANEL):
            put(panel, i, j, gutter if (i + j) % 3 else ramp.mid)

    plate = IRON.mid if not running else blend(IRON.mid, IRON.light, load)
    for y in range(1, PANEL - 1):
        for x in range(1, PANEL - 1):
            put(panel, x, y, plate)
    # A solid plate, edge-lit. The earlier version dithered the whole face and the result read
    # as television static rather than as a flat piece of steel.
    for i in range(1, PANEL - 1):
        put(panel, i, 1, IRON.light)
        put(panel, 1, i, IRON.light)
        put(panel, i, PANEL - 2, IRON.shadow)
        put(panel, PANEL - 2, i, IRON.shadow)

    for x, y in ((2, 2), (PANEL - 3, 2), (2, PANEL - 3), (PANEL - 3, PANEL - 3)):
        put(panel, x, y, RIVET if y == 2 else RIVET_DARK)

    # The screw head, raised, with a slot that steps through a quarter turn. Descent is
    # invisible from directly above, so the screw is what carries the motion here.
    for y in range(4, 6):
        for x in range(4, 6):
            put(panel, x, y, IRON.spec)
    put(panel, 3, 4, IRON.light)
    put(panel, 6, 5, IRON.shadow)
    put(panel, 4, 3, IRON.light)
    put(panel, 5, 6, IRON.shadow)

    step = int(frame / FRAMES * 2.0) % 2
    if step == 0 or not running:
        put(panel, 4, 4, IRON.shadow)
        put(panel, 4, 5, IRON.shadow)
    else:
        put(panel, 4, 4, IRON.shadow)
        put(panel, 5, 5, IRON.shadow)

    return panel


def isolator_top(frame, ramp, running):
    """An iris over a lit core: the aperture the bee is read through.

    It opens and closes rather than spinning, because the side face already carries a sweep and
    two different motions on one block read as two machines.
    """
    panel = blank_panel()
    centre = PANEL / 2.0
    phase = frame / FRAMES
    # Never fully shut: an idle Isolator still has a bee slot you can see into, and closing the
    # iris to a single pixel made the off state read as a plain grey plate.
    aperture = 2.0 + (1.0 * (2.0 * phase if phase < 0.5 else 2.0 * (1.0 - phase)) if running else 0.0)

    for y in range(PANEL):
        for x in range(PANEL):
            dx, dy = x + 0.5 - centre, y + 0.5 - centre
            radius = max(abs(dx), abs(dy))
            if radius > 4.6:
                continue
            if radius > 3.4:
                put(panel, x, y, IRON.light if dx + dy < 0 else IRON.mid)
            elif radius > aperture:
                # Four blades meeting on the diagonals. The seams are what make this an
                # aperture; without them the ring was a grey square with a light in it.
                seam = abs(abs(dx) - abs(dy)) < 0.8
                put(panel, x, y, IRON.shadow if seam else (IRON.light if dx + dy < 0 else IRON.mid))
            elif radius > aperture - 1.0:
                put(panel, x, y, ramp.mid if running else ramp.shadow)
            else:
                # Light, not spec. A four-by-four block of the brightest teal made this the
                # loudest face in the set, and the Isolator is not the loudest machine.
                put(panel, x, y, ramp.light if running else ramp.mid)

    return panel


def infuser_top(frame, ramp, running):
    """The funnel mouth, with a ripple crossing the serum in it.

    The ripple expands outward from the centre and is a single ring, which is the least a still
    surface can do and still be a surface with something falling into it.
    """
    panel = blank_panel()
    centre = PANEL / 2.0
    ring = 0.6 + (frame / FRAMES) * 3.6

    for y in range(PANEL):
        for x in range(PANEL):
            dx, dy = x + 0.5 - centre, y + 0.5 - centre
            radius = np.hypot(dx, dy)
            if radius > 4.6:
                continue
            if radius > 3.7:
                put(panel, x, y, IRON.light if dx + dy < 0 else IRON.mid)
                continue
            tone = ramp.mid if not running else ramp.light
            if running and abs(radius - ring) < 0.55:
                tone = ramp.spec
            elif running and abs(radius - ring) < 1.1:
                tone = ramp.mid
            put(panel, x, y, tone)

    for x, y in ((3, 3), (PANEL - 4, 3), (3, PANEL - 4), (PANEL - 4, PANEL - 4)):
        put(panel, x, y, RIVET if y == 3 else RIVET_DARK)

    return panel


def bottom(ramp):
    """The underside: the same case with a bolted access plate on it. One design for all four,
    but written per machine so the Squeezer stops borrowing the Centrifuge's file."""
    panel = blank_panel()
    for y in range(PANEL):
        for x in range(PANEL):
            level = WOOD_NEUTRAL - 1
            if GRAIN[y + INSET, x + INSET] < 0.2:
                level -= 1
            elif GRAIN[y + INSET, x + INSET] > 0.85:
                level += 1
            put(panel, x, y, WOOD[min(len(WOOD) - 1, max(0, level))])

    for i in range(PANEL):
        put(panel, i, PANEL // 2, WOOD[0])
        put(panel, PANEL // 2, i, WOOD[0])
    for x, y in ((1, 1), (PANEL - 2, 1), (1, PANEL - 2), (PANEL - 2, PANEL - 2)):
        put(panel, x, y, RIVET if y == 1 else RIVET_DARK)

    put(panel, PANEL // 2, PANEL // 2, scale(ramp.mid, 0.7))
    return panel


MACHINES = {
    "centrifuge": (centrifuge_side, centrifuge_top),
    "squeezer": (squeezer_side, squeezer_top),
    "isolator": (isolator_side, isolator_top),
    "infuser": (infuser_side, infuser_top),
}

# The two faces run at different rates so a bank of machines does not tick in unison, and the
# tops are the slower of the two because a rotor read from above at speed turns into a blur.
SIDE_FRAMETIME = 2
TOP_FRAMETIME = 3


def still(path, image):
    Image.fromarray(image).save(path)
    print("%s  16x16  still" % path)


def animated(path, frames, frametime):
    sheet = np.concatenate(frames, axis=0)
    Image.fromarray(sheet).save(path)
    with open(path + ".mcmeta", "w", encoding="utf-8", newline="\n") as meta:
        meta.write('{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % frametime)
    print("%s  16x%d  %d frames  frametime %d" % (path, sheet.shape[0], len(frames), frametime))


def generate(out):
    for name, (side, top) in MACHINES.items():
        ramp = ACCENT[name]
        off = idle(ramp)

        still("%s/%s.png" % (out, name), compose(side(0, off, False)))
        still("%s/%s_top.png" % (out, name), compose(top(0, off, False)))
        still("%s/%s_bottom.png" % (out, name), compose(bottom(off)))

        animated("%s/%s_on.png" % (out, name),
                 [compose(side(f, ramp, True), ramp) for f in range(FRAMES)], SIDE_FRAMETIME)
        animated("%s/%s_top_on.png" % (out, name),
                 [compose(top(f, ramp, True), ramp) for f in range(FRAMES)], TOP_FRAMETIME)


def preview(out, scale_to=9):
    """Every face of every machine, off beside on, magnified. The only way to judge whether the
    four still read as one family and as four different jobs is to see them side by side."""
    rows = []
    for name, (side, top) in MACHINES.items():
        ramp = ACCENT[name]
        off = idle(ramp)
        row = [compose(side(0, off, False)), compose(top(0, off, False)), compose(bottom(off))]
        row += [compose(side(f, ramp, True), ramp) for f in range(0, FRAMES, 2)]
        row += [compose(top(f, ramp, True), ramp) for f in range(0, FRAMES, 2)]
        rows.append((name, row))

    cell = SIZE * scale_to
    width = max(len(row) for _, row in rows) * (cell + 4) + 4
    canvas = Image.new("RGB", (width, len(rows) * (cell + 4) + 4), (46, 44, 42))
    for y, (_, row) in enumerate(rows):
        for x, img in enumerate(row):
            tile = Image.fromarray(img).convert("RGB").resize((cell, cell), Image.NEAREST)
            canvas.paste(tile, (4 + x * (cell + 4), 4 + y * (cell + 4)))
    canvas.save(out + "/preview_machines.png")
    print("%s/preview_machines.png" % out)


if __name__ == "__main__":
    generate(sys.argv[1] if len(sys.argv) > 1 else ".")
    if len(sys.argv) > 2:
        preview(sys.argv[2])
