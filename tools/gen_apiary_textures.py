"""Generates the Apiary block textures: the four side faces of a tower, plus lid and floor.

    python tools/gen_apiary_textures.py src/main/resources/assets/mellifera/textures/block

Not part of the Gradle build, and kept for the same reason as gen_honey_textures.py and
gen_machine_textures.py: the PNGs are otherwise unmaintainable binary blobs.

The palette, the grain field and the light direction are imported from gen_machine_textures
rather than restated here. That is the point of the file: the Apiary is the block the machines
stand next to, and the two sets have to be the same wood cut from the same tree. What they must
not share is their vocabulary -- the machines are iron panels recessed into a case, and if the
Apiary got one of those it would stop being a hive. So it is built from the other half of the
same workshop: box joints at the corners, a handhold groove, a lid that overhangs, an entrance
with a landing board, and brass only where a real hive has metal.

ApiaryBlock's blockstate picks single/bottom/middle/top per block, and the side faces are
authored so that a column of them is one tall hive rather than a pile of boxes: there is no rim,
no seam and no shadow at a join, and the corner box joints run through it unbroken. A player
looking at a three-high Apiary should not be able to say where one block ends.

That costs more than it looks. ApiaryBlock.partAt only asks whether there is an Apiary above
and below, so MIDDLE repeats as many times as the player stacks -- MAX_LEVELS caps how many
levels *work*, not how many blocks sit on each other -- and a two-high tower is BOTTOM directly
under TOP with no middle at all. So every upper edge has to meet every lower edge, and the
middle has to meet itself. The way that is paid for is by having nothing at the edges to match:
the grain is per-pixel flecks and one-row fibre dashes, with no feature that spans a row
boundary, so any row can follow any other and there is nothing to line up. Everything the eye
can catch -- door, handhold, vent, lid -- sits well inside its block.

The per-part grain phase below is free for the same reason. Rolling white noise cannot break a
join, because there was never a correlation across it to break.

Every face is written twice, as `<name>.png` and `<name>_trim.png` -- see Face. That is what
lets a dyed Apiary keep its brass and its bees. Faces with no hardware on them get no trim file
at all, and their model is a single cube.

There is no lit variant: the Apiary has no `working` blockstate, and the bees flying in and out
are entities drawn by HiveBeeRenderer. The two amber pixels on the landing board are the only
life the texture itself carries, and they are there because a hive with nothing at the door
reads as an empty box.
"""
import sys

import numpy as np
from PIL import Image

from gen_machine_textures import CAVITY_DEEP, GRAIN, SIZE, WOOD, put, rgba

# A whole board face, unlike the machines' two-pixel rails, so the grain has room to be fibre
# rather than speckle: a fine per-pixel field for the flecks, and dashes along a row for the
# run of the wood. Both are read off the shared grain so the Apiary and the machines are
# visibly the same timber.
FLECK_DARK, FLECK_LIGHT = 0.12, 0.90
FIBRE = GRAIN[:, 0]

# Bee gold. Not one of the machine accents: this is the only warm point on the block and it
# should read as an animal, not as a running mechanism.
BEE = (0xE8, 0xC3, 0x4A)

# The machines' RIVET sits on a dark iron panel, where it reads as brass. On bare wood it is
# within a few values of WOOD[5] and disappears, so the hive's ironmongery gets its own pair --
# lighter, and always drawn against a dark outline rather than straight onto the board.
BRASS = (0xE4, 0xB8, 0x56)
BRASS_DARK = (0x7C, 0x5C, 0x24)

BOARD = 3  # Neutral step in WOOD for a face pointing at the viewer.

# How far the shared grain is rolled for each super of a tower. Coprime with the finger-joint
# period so the two never line up into a repeat, and never a multiple of SIZE, which would be
# no roll at all. The single keeps the bottom's draw: it is the block a player builds first and
# there is nothing above it to differ from.
GRAIN_BOTTOM, GRAIN_MIDDLE, GRAIN_TOP = 0, 5, 11

# The wood layer is written neutral -- greys -- and gets its colour from the tint, including
# when it is undyed. That is the only way a blue Apiary can be blue.
#
# A tint multiplies. Multiplying this mod's board tone (#A98649, warm, and with barely any blue
# in it at all) by a blue dye is arithmetic, not taste: #3C44AA came out #5B4B3A, which is
# brown. Lightening the dye first does not help, it only pulls the result back towards the
# wood. Nothing done to the dye can put blue into a texel that has none.
#
# So the boards are stored as luminance, scaled so the lightest step is white, and UNDYED_TAN
# is the tint that multiplies them back into the wood everyone has seen up to now. Undyed looks
# exactly as it did; every dye now lands on a neutral base and reads as itself.
#
# Both constants are mirrored in ApiaryBlock.Tint, which is what the game actually uses. They
# live in both places because this file's preview is only worth looking at if it lies about
# nothing.
# Least squares over the whole WOOD ramp rather than a tint pinned to the middle step: pinning
# reproduced the mid board exactly and left the lightest tone twenty-one off in blue, which is
# the lid, the one place the eye goes. Fitted, no step is more than twelve out and most are
# under six -- below what a player can see on a 16x16 sprite at arm's length.
UNDYED_TAN = (0xE0, 0xB3, 0x66)

# A gentle lift towards white before multiplying. The neutral base already costs about a third
# of the value, so a raw dye lands darker than the paint pot suggests; this puts some of it
# back without washing the colour out.
PAINT_MIX = 0.25


def luminance(color):
    return 0.2126 * color[0] + 0.7152 * color[1] + 0.0722 * color[2]


# The lightest board tone becomes white, which fixes the scale for every other step.
WHITE_POINT = luminance(WOOD[len(WOOD) - 1])


def neutral(color):
    """A board colour as the grey the wood layer actually stores."""
    value = min(255, max(0, int(round(255.0 * luminance(color) / WHITE_POINT))))
    return (value, value, value)


class Face:
    """One face as two layers: the wood, which a dye tints, and the trim, which it must not.

    `tintindex` in a block model is per face, not per pixel. A single-layer texture would take
    the dye across the brass catch and the two bees on the landing board as well as the boards
    -- a blue hive with blue bees on the step, which is worse than no dye at all. So the model
    draws the wood cube and then this trim cube at the same coordinates, which is exactly how
    vanilla's grass block lays its overlay over its side texture.

    The trim starts fully transparent and nothing has to declare a render type for it: the
    renderer picks the layer off the sprite's own alpha, in BakedQuad.MaterialInfo.of by way of
    ChunkSectionLayer.byTransparency. Opaque wood lands in SOLID, the trim lands in CUTOUT.

    The wood layer is never cut away under the trim. It has to stay a closed opaque cube, and a
    transparent hole in it would be a hole into the block -- the trim covers, it does not fill.
    """

    def __init__(self, base):
        self.wood = base
        self.trim = np.zeros((SIZE, SIZE, 4), np.uint8)

    def paint(self, x, y, color):
        """Wood: takes the dye. Written as the grey that multiplies back into `color` under the
        undyed tint, so every drawing call above can go on naming the board tone it means."""
        put(self.wood, x, y, neutral(color))

    def fit(self, x, y, color):
        """Hardware, a hole, or an animal: keeps its own colour whatever the hive is painted."""
        put(self.trim, x, y, color)

    def trimmed(self):
        return bool(self.trim[..., 3].any())



def board(level=BOARD, shift=0):
    """A plain wooden face with grain, before any hive parts are cut into it.

    `shift` rolls the shared grain field down by a few rows. The four side faces are the same
    board tone from the same field, so without it a three-high tower repeats one pattern three
    times and reads as a photocopy rather than as a run of timber. Rolling keeps every
    statistic identical -- same flecks, same fibre, same mean -- and only moves where they land.
    """
    grain = np.roll(GRAIN, shift, axis=0)
    fibre = np.roll(FIBRE, shift)

    img = np.zeros((SIZE, SIZE, 4), np.uint8)
    for y in range(SIZE):
        for x in range(SIZE):
            tone = level
            if grain[y, x] < FLECK_DARK:
                tone -= 1
            elif grain[y, x] > FLECK_LIGHT:
                tone += 1
            # Fibre: on about a fifth of the rows, the darker half of the pixels drops a step,
            # which comes out as broken dashes running with the grain instead of a stripe.
            if fibre[y] < 0.20 and grain[y, x] < 0.6:
                tone -= 1
            img[y, x] = rgba(neutral(WOOD[min(len(WOOD) - 1, max(0, tone))]))
    return Face(img)


FINGER = 4  # Rows per finger. Must divide SIZE, or the joint breaks at every block boundary.


def joints(face, overhang=0):
    """Box joints down both corners.

    The alternating blocks are the fingers of a corner joint seen end-on: end grain takes the
    light differently from a board face, so every other block steps a tone. It is the Apiary's
    equivalent of the machines' four brass rivets -- the mark that says these blocks belong to
    each other -- and it costs four pixels a row.

    Four rows per finger, so the pattern has a period of eight and SIZE is two whole periods of
    it. Three rows looked better on one texture in isolation and was wrong on a tower: sixteen
    is not a multiple of three, so the fingers restarted mid-joint at every block boundary and
    the corner of a stack came out as three unrelated columns.

    `overhang` shades the corners for the rows under a projecting lid, easing back to the plain
    joint tone within eight rows. Carrying that shadow the whole height was the single worst
    break in a tower: it left the crown's left edge some fifty luminance below the super under
    it, which is a different plank, not a shadow.
    """
    for y in range(SIZE):
        finger = (y // FINGER) % 2
        drop = 0
        if y < overhang:
            drop = 2 if y < overhang - 2 else 1

        def tone(index):
            return WOOD[min(len(WOOD) - 1, max(0, index - drop))]

        face.paint(0, y, tone(6 if finger else 4))
        face.paint(1, y, tone(5 if finger else 3))
        face.paint(SIZE - 2, y, tone(3 if finger else 1))
        face.paint(SIZE - 1, y, tone(2 if finger else 0))


def groove(face, top):
    """The handhold routed into a super, two pixels deep.

    Cut into the face, so it is the opposite way round from a raised part: the upper inner edge
    is in shadow and the lower one catches the light. All wood -- a routed groove is painted
    along with everything else, which is the tell that separates it from the doorway.
    """
    for x in range(3, SIZE - 3):
        face.paint(x, top, WOOD[0])
        face.paint(x, top + 1, WOOD[5])
    face.paint(2, top, WOOD[1])
    face.paint(SIZE - 3, top + 1, WOOD[3])


def entrance(face, slot):
    """The doorway and the board the bees land on.

    The landing board is the one part of the block that sticks out, so it is the one part lit
    on top and casting a shadow underneath -- the reverse of the groove above it, and the
    reason the two never get confused at sixteen pixels.

    The board is wood and takes the dye. The doorway does not: it is a hole, and a hole is not
    a colour a hive can be painted. Neither are the bees.
    """
    for x in range(3, SIZE - 3):
        face.fit(x, slot, CAVITY_DEEP)
        face.fit(x, slot + 1, CAVITY_DEEP)
    for x in range(2, SIZE - 2):
        face.paint(x, slot + 2, WOOD[6])
        face.paint(x, slot + 3, WOOD[0])
    face.paint(2, slot + 2, WOOD[5])
    face.paint(SIZE - 3, slot + 2, WOOD[4])

    # Comb just inside the door, then two bees on the board. Three pixels of life, which is as
    # much as a face this size will take before it turns into a picture of bees.
    face.fit(7, slot + 1, WOOD[6])
    face.fit(5, slot + 2, BEE)
    face.fit(10, slot + 2, BEE)


def lid(face):
    """The overhanging roof: two boards of lid, then the shadow it throws on the super below.

    A texture cannot hang past the edge of its block, so the overhang is drawn the other way
    round -- the lid runs the full width and everything under it is shaded in by one step (see
    `overhang` in `joints`), which reads as the box being narrower than the roof.
    """
    for x in range(SIZE):
        face.paint(x, 0, WOOD[6])
        face.paint(x, 1, WOOD[5])
        face.paint(x, 2, WOOD[0])
    face.paint(0, 0, WOOD[6])
    face.paint(SIZE - 1, 1, WOOD[3])


def hasp(face, top):
    """The brass clasp holding the lid down. The only metal on the front of the block, and
    deliberately small: a hive has one catch, not the four rivets a machine case has.

    The dark outline stays on the wood layer. It is the shadow the clasp throws, and a shadow
    on a painted board is the colour of the paint -- only the brass itself is hardware.
    """
    for y in range(top, top + 3):
        face.paint(6, y, WOOD[0])
        face.paint(9, y, WOOD[0])
    face.fit(7, top, BRASS)
    face.fit(8, top, BRASS_DARK)
    face.fit(7, top + 1, BRASS)
    face.fit(8, top + 1, BRASS_DARK)
    face.paint(7, top + 2, WOOD[0])
    face.paint(8, top + 2, WOOD[0])


# ---------------------------------------------------------------------------------- the faces

def front_single():
    """One block that has to be a whole hive: lid at the top, door at the bottom, catch between.

    The tightest of the six. Everything is on it because a single Apiary is what a player builds
    first and quite possibly never grows, so it cannot be the middle of something.
    """
    face = board()
    joints(face, overhang=8)
    lid(face)
    hasp(face, 3)
    # The handhold goes on too, even though it is the tightest face of the six: without it the
    # band between the catch and the door is the only dead space anywhere in the set, and it is
    # also the one part the single shares with a super rather than with a lid.
    groove(face, 7)
    entrance(face, 10)
    face.paint(SIZE - 1, SIZE - 1, WOOD[0])
    return face


def front_bottom():
    """The floor super of a tower: door, landing board, and the plinth it stands on.

    The plinth is the one horizontal band left in the set, and it is allowed because it is the
    bottom of the whole column -- there is ground under it, not another Apiary.
    """
    face = board(shift=GRAIN_BOTTOM)
    joints(face)
    entrance(face, 9)
    for x in range(1, SIZE - 1):
        face.paint(x, SIZE - 2, WOOD[2])
    return face


def front_middle():
    """A plain super, carrying nothing but the handhold.

    The one that has to survive being repeated: partAt gives MIDDLE to every block with an
    Apiary above and below it, so a six-high stack is four of these on top of each other. Its
    top and bottom rows are plain board for that reason, and the handhold sits at row 7 where it
    cannot come out as a stripe every sixteen pixels.

    The only face in the set with no hardware at all, so it writes no trim and its model is a
    single cube.
    """
    face = board(shift=GRAIN_MIDDLE)
    joints(face)
    groove(face, 7)
    return face


def front_top():
    """The crown: the overhanging lid, its catch, and a vent under it."""
    face = board(shift=GRAIN_TOP)
    joints(face, overhang=8)
    lid(face)
    hasp(face, 3)

    # Vent slot with a strip of mesh across it -- the reason a stack does not cook in summer,
    # and a second dark note low on the face so the lid is not the only thing happening. Slot
    # and mesh are both hardware: a screen is brass and the gap behind it is a gap, so a
    # lavender hive gets a lavender box with the same vent in it.
    for x in range(5, SIZE - 5):
        face.fit(x, 10, CAVITY_DEEP)
        face.fit(x, 11, BRASS_DARK if x % 2 else CAVITY_DEEP)
    face.paint(4, 10, WOOD[1])
    face.paint(SIZE - 5, 11, WOOD[4])
    return face


def top():
    """The lid from above: boards, a rim, the catch at the front edge and four nails."""
    face = board(BOARD + 1)
    for i in range(SIZE):
        face.paint(i, 0, WOOD[6])
        face.paint(0, i, WOOD[5])
        face.paint(i, SIZE - 1, WOOD[1])
        face.paint(SIZE - 1, i, WOOD[2])

    # Three boards laid across the lid. The seam is a dark line with a lit one under it, so the
    # roof reads as planks and not as a drawing of planks.
    for seam in (5, 10):
        for x in range(1, SIZE - 1):
            face.paint(x, seam, WOOD[0])
            face.paint(x, seam + 1, WOOD[6])

    face.fit(7, SIZE - 3, BRASS)
    face.fit(8, SIZE - 3, BRASS_DARK)
    face.paint(6, SIZE - 3, WOOD[0])
    face.paint(9, SIZE - 3, WOOD[0])
    for x, y in ((2, 2), (SIZE - 3, 2), (2, SIZE - 3), (SIZE - 3, SIZE - 3)):
        face.fit(x, y, BRASS_DARK)
    return face


def stand():
    """Plain upright boards, for the trestle the hive stands on when it is on scaffolding.

    Its own face rather than a corner of an existing one. The stand's posts are two pixels wide,
    and every two-pixel strip of a side face is a box joint -- so mapping them onto one gave four
    uprights made of alternating joint blocks, which read as neither hive nor scaffolding. There
    is no clean two-pixel column anywhere on those textures, and the handhold groove crosses the
    middle of the one face that has no joints, so a full-height post could not avoid it either.

    The grain is turned a quarter: these are uprights, and wood in an upright runs up it. It is
    the same field as the boards, transposed, so it is still the same timber.
    """
    face = board(BOARD, shift=GRAIN_TOP)
    face.wood = np.transpose(face.wood, (1, 0, 2)).copy()
    return face


def bottom():
    """The screened floor: a mesh panel in a frame, on four feet.

    Almost never seen, which is exactly why it is the cheapest of the six -- but a plain plank
    square here would be the one face that admits the block is a cube with pictures on it. No
    hardware, so no trim: the underside of a painted hive is painted.
    """
    face = board(BOARD - 1)
    for i in range(SIZE):
        face.paint(i, 0, WOOD[4])
        face.paint(0, i, WOOD[4])
        face.paint(i, SIZE - 1, WOOD[0])
        face.paint(SIZE - 1, i, WOOD[0])

    for y in range(4, SIZE - 4):
        for x in range(4, SIZE - 4):
            face.paint(x, y, WOOD[1] if (x + y) % 2 else WOOD[0])
    for i in range(3, SIZE - 3):
        face.paint(i, 3, WOOD[5])
        face.paint(3, i, WOOD[5])
        face.paint(i, SIZE - 4, WOOD[1])
        face.paint(SIZE - 4, i, WOOD[1])

    for x, y in ((1, 1), (SIZE - 2, 1), (1, SIZE - 2), (SIZE - 2, SIZE - 2)):
        face.paint(x, y, WOOD[1] if y == 1 else WOOD[0])
    return face


FACES = {
    "apiary_front": front_single,
    "apiary_front_bottom": front_bottom,
    "apiary_front_middle": front_middle,
    "apiary_front_top": front_top,
    "apiary_top": top,
    "apiary_bottom": bottom,
    "apiary_stand": stand,
}


def generate(out):
    for name, build in FACES.items():
        face = build()
        Image.fromarray(face.wood).save("%s/%s.png" % (out, name))
        print("%s/%s.png  16x16" % (out, name))
        if face.trimmed():
            Image.fromarray(face.trim).save("%s/%s_trim.png" % (out, name))
            print("%s/%s_trim.png  16x16  trim" % (out, name))


# A handful of the vanilla dyes, straight off DyeColor.getTextureDiffuseColor, for the preview.
PREVIEW_DYES = [
    ("undyed", None),
    ("red", 0xB02E26),
    ("light_blue", 0x3AB3DA),
    ("blue", 0x3C44AA),
    ("lime", 0x80C71F),
    ("purple", 0x8932B8),
    ("white", 0xF9FFFE),
]


def painted(face, dye):
    """What the game will draw: the neutral wood multiplied by the tint, then the trim over it.

    Undyed is not a special case in the renderer -- it is the same multiply against
    UNDYED_TAN -- and it is not one here either, which is the whole point of checking the
    preview: if the top row does not look like the wood from before this feature existed, the
    constant is wrong.
    """
    if dye is None:
        paint = np.array(UNDYED_TAN, dtype=float)
    else:
        paint = np.array([(dye >> 16) & 0xFF, (dye >> 8) & 0xFF, dye & 0xFF], dtype=float)
        paint = paint + (255.0 - paint) * PAINT_MIX

    wood = np.asarray(Image.fromarray(face.wood).convert("RGB"), dtype=float) * paint / 255.0

    out = Image.fromarray(np.uint8(np.clip(wood, 0, 255))).convert("RGBA")
    out.alpha_composite(Image.fromarray(face.trim))
    return out


def preview(out, scale=8):
    """Every dye down the page, and the towers that catch a bad join across it.

    Four-high first, because that is the case that catches one: it puts a middle on a middle,
    which is the one pairing a three-high tower never shows. Two-high next, where BOTTOM meets
    TOP with nothing between them.
    """
    columns = [
        [front_top(), front_middle(), front_middle(), front_bottom()],
        [front_top(), front_bottom()],
        [front_single()],
        [top()],
        [bottom()],
        [stand()],
    ]

    cell = SIZE * scale
    height = max(len(column) for column in columns) * cell
    width = len(columns) * (cell + 10) + 10
    canvas = Image.new("RGB", (width, len(PREVIEW_DYES) * (height + 14) + 10), (46, 44, 42))

    for row, (_, dye) in enumerate(PREVIEW_DYES):
        top_y = 10 + row * (height + 14)
        for x, column in enumerate(columns):
            for y, face in enumerate(column):
                tile = painted(face, dye).convert("RGB").resize((cell, cell), Image.NEAREST)
                canvas.paste(tile, (10 + x * (cell + 10), top_y + y * cell))

    canvas.save(out + "/preview_apiary.png")
    print("%s/preview_apiary.png" % out)


if __name__ == "__main__":
    generate(sys.argv[1] if len(sys.argv) > 1 else ".")
    if len(sys.argv) > 2:
        preview(sys.argv[2])
