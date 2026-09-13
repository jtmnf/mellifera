"""Paints every machine window in the mod, and emits the geometry the screens draw against.

    python tools/gen_machine_guis.py src/main/resources/assets/mellifera src/main/java
    python tools/gen_machine_guis.py <assets> <java> <preview-dir>   # also writes preview_guis.png

WHAT THIS IS. The five machine windows were one file copied five ways: a frame, a player inventory,
and slots painted wherever the first machine happened to want them. This does to a window what
gen_machine_textures.py did to the blocks -- builds it out of named parts, under one rule about
where the light comes from -- and it does it for all five at once, so they cannot drift apart.

The frame and the player inventory are still lifted from the shared window and must be: those are
the family's, and a window that framed itself differently would read as another mod's. Everything
inside the panel is drawn here.

WHAT IS PAINTED AND WHAT IS NOT. Anything that never moves: the work plate, the slot recesses and
their bolts, the drive track, the tank and output housings. Anything that moves is drawn at runtime
by the screens -- progress, honey, energy, the Infuser's charge pips -- because art cannot animate.

WHY IT WRITES JAVA. A housing painted here and a bar drawn there are the same rectangle held in two
places, and the Carpenter's first pass proved how that ends: a tank two pixels taller than the plate
beside it, and nobody to notice until it was on screen. MachineGeometry.java is generated from the
table below, and the screens read it instead of carrying their own copies. Slot positions stay in
the menus -- those are gameplay, they existed before this file, and they are what the layout here is
fitted around rather than the other way round.
"""
import sys

import numpy as np
from PIL import Image

SIZE = 176

# The panel each window owns, measured off the shared frame: its border is a dark line at column 7
# and row 15 and a highlight at column 168 and row 79.
PANEL = (8, 16, 167, 78)

SLOT = 18

# Sampled from the shared art rather than invented, so a part drawn here and a part inherited from
# the frame cannot end up different woods.
WOOD = (0xB6, 0x9D, 0x77)
WOOD_DARK = (0x7E, 0x66, 0x47)
WOOD_LIGHT = (0xCA, 0xB5, 0x92)
SLOT_FACE = (0x6E, 0x5A, 0x3E)
PLATE_FACE = (0xAD, 0x94, 0x6F)

# Grain tones, one step either side of the wood. Deliberately much closer to it than the bevels: the
# first attempt grained with the bevel colours and the panel came out as sandpaper, with every
# one-pixel bevel lost in the speckle.
GRAIN_DARK = (0xA8, 0x90, 0x6C)
GRAIN_LIGHT = (0xC2, 0xAA, 0x84)

BRASS = (0xC9, 0xA0, 0x4E)
BRASS_DARK = (0x8A, 0x6A, 0x2E)

IRON = (0x50, 0x4D, 0x49)
IRON_LIGHT = (0x72, 0x6E, 0x68)
IRON_DARK = (0x33, 0x31, 0x2F)

CAVITY = (0x2A, 0x20, 0x12)
CAVITY_DEEP = (0x1D, 0x16, 0x0C)

GLASS_SPEC = (0x8E, 0x9E, 0xA6)
GLASS_SPEC_DIM = (0x6A, 0x76, 0x7C)

RNG = np.random.default_rng(20260811)

# ------------------------------------------------------------------------------------- layout
#
# Every window is one raised rectangle -- FRAME -- with everything set into it: the input wells, the
# drive track, the output grid, the tank. The first pass split each window into two boxes side by
# side, a work plate and a housing, and that is what made the outputs and the tank read as bolted-on
# extras rather than as parts of the machine. One surface, things recessed into it.
#
# Slot positions here MIRROR the menus -- CentrifugeMenu, SqueezerMenu, IsolatorMenu, InfuserMenu,
# CarpenterMenu -- and the wells are drawn one pixel outside them. They are duplicated rather than
# read because a menu is Java and this is a build-time script; if a menu moves a slot it moves here
# too, and the preview is where a mismatch shows up immediately.

# The one rectangle, the same in all five windows, inset three pixels inside the panel.
FRAME = (11, 18, 164, 76)

# Sub-housings -- the grid surround and the glass column -- all share these rows, so no two windows
# put their right-hand furniture at different heights.
HOUSING_TOP = 21
HOUSING_BOTTOM = 73

# The drive track is the same object in every machine: same length, same thickness, same row. Only
# where it starts changes, because the gap it crosses is a different width in each window. It used
# to be 37 pixels here, 32 there and 26 somewhere else, which is exactly the sort of thing that is
# invisible in one window and obvious the moment two are opened one after the other.
TRACK_WIDTH = 32
TRACK_HEIGHT = 5
TRACK_TOP = 45

MACHINES = {
    "centrifuge": {
        "slots": [(25, 40)],
        "grid": {"origin": (99, 21), "columns": 3, "rows": 3},
        "grid_housing": (95, HOUSING_TOP, 155, HOUSING_BOTTOM),
        "track_x": 52,
    },
    "squeezer": {
        "slots": [(25, 40)],
        "tank": (112, 24, 139, 70),
        "tank_housing": (108, HOUSING_TOP, 143, HOUSING_BOTTOM),
        "track_x": 52,
    },
    # Three rows, not two: the grid holds one cell per chromosome and there are eleven of those
    # since nocturnal, tolerant flyer and cave dwelling were added. Twelve cells, so the last one
    # sits empty -- which is better than the alternatives, all of which are a machine that stalls
    # with a serum it has nowhere to put.
    #
    # The extra row does not fit between HOUSING_TOP and HOUSING_BOTTOM, which were picked when
    # this was a two-row grid, so these two windows carry a taller housing of their own. It still
    # ends inside FRAME, which is what actually bounds a window.
    "isolator": {
        "slots": [(20, 28), (20, 50)],
        "grid": {"origin": (86, 20), "columns": 4, "rows": 3, "row_pitch": 18},
        "grid_housing": (82, 19, 160, 75),
        "track_x": 46,
    },
    "infuser": {
        "slots": [(20, 28), (20, 50)],
        "grid": {"origin": (86, 20), "columns": 4, "rows": 3, "row_pitch": 18},
        "grid_housing": (82, 19, 160, 75),
        "track_x": 46,
    },
    "carpenter": {
        "slots": [(25, 28), (25, 50), (89, 39)],
        "tank": (124, 24, 151, 70),
        "tank_housing": (120, HOUSING_TOP, 155, HOUSING_BOTTOM),
        "track_x": 50,
    },
    # The Engine borrows the Squeezer's layout exactly, and on purpose: one thing in on the left, a
    # tank on the right, the track between them. It is the machine with the least to show -- no
    # output slot at all, because what it makes leaves through the sides of the block -- and giving
    # it furniture of its own would only have been decoration.
    "engine": {
        "slots": [(25, 40)],
        "tank": (112, 24, 139, 70),
        "tank_housing": (108, HOUSING_TOP, 143, HOUSING_BOTTOM),
        "track_x": 52,
    },
}


def track_of(layout):
    left = layout["track_x"]
    return (left, TRACK_TOP, left + TRACK_WIDTH - 1, TRACK_TOP + TRACK_HEIGHT - 1)


# The window every one of these is cut from. Its frame and inventory are what make five windows one
# family; its panel is wiped and rebuilt.
SOURCE = "centrifuge.png"


def paint(image, box, color):
    left, top, right, bottom = box
    image[top:bottom + 1, left:right + 1] = (*color, 255)


def line_h(image, x0, x1, y, color):
    image[y, x0:x1 + 1] = (*color, 255)


def line_v(image, x, y0, y1, color):
    image[y0:y1 + 1, x] = (*color, 255)


def put(image, x, y, color):
    image[y, x] = (*color, 255)


def recess(image, box, face=SLOT_FACE, dark=WOOD_DARK, light=WOOD_LIGHT):
    """A hole in the surface: dark along the top and left inner edges, light along the others."""
    left, top, right, bottom = box
    paint(image, box, face)
    line_h(image, left, right - 1, top, dark)
    line_v(image, left, top, bottom - 1, dark)
    line_h(image, left + 1, right, bottom, light)
    line_v(image, right, top + 1, bottom, light)


def plate(image, box, face=PLATE_FACE, dark=WOOD_DARK, light=WOOD_LIGHT):
    """The same bevel inverted: a surface standing proud of the panel."""
    left, top, right, bottom = box
    paint(image, box, face)
    line_h(image, left, right - 1, top, light)
    line_v(image, left, top, bottom - 1, light)
    line_h(image, left + 1, right, bottom, dark)
    line_v(image, right, top + 1, bottom, dark)


def bolts(image, box, inset=3):
    """Four brass bolt heads, lit above and dull below.

    The block textures put four rivets on every face and it is half of what makes the machines read
    as one family of hardware. A window built out of the same parts carries them too.
    """
    left, top, right, bottom = box
    for x, y in ((left + inset, top + inset), (right - inset, top + inset),
                 (left + inset, bottom - inset), (right - inset, bottom - inset)):
        put(image, x, y, BRASS if y == top + inset else BRASS_DARK)


def grain(image, box, amount=0.06):
    """A scatter of slightly darker and lighter wood, so a large surface is not a flat fill."""
    left, top, right, bottom = box
    noise = RNG.random((bottom - top + 1, right - left + 1))

    for y in range(top, bottom + 1):
        for x in range(left, right + 1):
            value = noise[y - top, x - left]
            if value < amount:
                image[y, x] = (*GRAIN_DARK, 255)
            elif value > 1.0 - amount * 0.6:
                image[y, x] = (*GRAIN_LIGHT, 255)


def slot_well(image, slot_x, slot_y, pins=True):
    """A slot: the recess itself, and a brass pin above and below.

    The pins are what tell an empty slot from a hole in the plate. Vanilla leaves its slots bare
    because its windows are bare; these are machines, and a machine holds its fittings on with
    something.
    """
    left, top = slot_x - 1, slot_y - 1
    recess(image, (left, top, left + SLOT - 1, top + SLOT - 1))

    if not pins:
        return

    put(image, left + 1, top - 2, BRASS)
    put(image, left + SLOT - 2, top - 2, BRASS)
    put(image, left + 1, top + SLOT + 1, BRASS_DARK)
    put(image, left + SLOT - 2, top + SLOT + 1, BRASS_DARK)


def drive_track(image, box):
    """The channel a progress bar runs in, with a tooth every four pixels.

    Teeth rather than a plain groove: every one of these machines is a mechanism, and a toothed rack
    is what would drive one. It also gives the bar a scale to fill against.
    """
    left, top, right, bottom = box
    recess(image, box, face=CAVITY)

    for x in range(left + 2, right - 1, 4):
        line_v(image, x, top + 1, bottom - 1, IRON_DARK)
        put(image, x, top + 1, IRON_LIGHT)


def glass_column(image, box):
    """A tank: an iron frame around a dark glass box, with gauge ticks at the quarters.

    The fluid is not painted -- the screen fills the inner box at runtime -- so what is here is the
    empty vessel, which is the part that never changes.
    """
    left, top, right, bottom = box
    recess(image, box, face=CAVITY_DEEP, dark=IRON_DARK, light=IRON_LIGHT)

    height = bottom - top
    for quarter in range(1, 4):
        y = bottom - round(height * quarter / 4.0)
        line_h(image, left + 1, left + 3, y, IRON_LIGHT)
        line_h(image, right - 3, right - 1, y, IRON_DARK)

    put(image, left + 2, top + 2, GLASS_SPEC)
    put(image, left + 3, top + 2, GLASS_SPEC_DIM)
    put(image, left + 2, top + 3, GLASS_SPEC_DIM)


def build(frame, layout):
    image = frame.copy()

    paint(image, PANEL, WOOD)
    grain(image, PANEL)

    # One surface for the whole window, and everything else cut into it.
    plate(image, FRAME)
    grain(image, (FRAME[0] + 1, FRAME[1] + 1, FRAME[2] - 1, FRAME[3] - 1), amount=0.04)
    bolts(image, FRAME)

    for box_key in ("grid_housing", "tank_housing"):
        if box_key in layout:
            # Recessed rather than raised: the grid and the glass sit *in* the surface, which is what
            # makes them part of the machine instead of a second panel laid beside it.
            recess(image, layout[box_key], face=PLATE_FACE)
            bolts(image, layout[box_key], inset=2)

    for slot_x, slot_y in layout["slots"]:
        slot_well(image, slot_x, slot_y)

    grid = layout.get("grid")
    if grid is not None:
        origin_x, origin_y = grid["origin"]
        pitch_y = grid.get("row_pitch", SLOT)
        for row in range(grid["rows"]):
            for column in range(grid["columns"]):
                # No pins on a grid: at this density they merge into a dotted line and the cells stop
                # reading as separate wells.
                slot_well(image, origin_x + column * SLOT, origin_y + row * pitch_y, pins=False)

    if "tank" in layout:
        glass_column(image, layout["tank"])

    drive_track(image, track_of(layout))
    return image


# -------------------------------------------------------------------------------- generated java

JAVA_PACKAGE = "com.joaonf.mellifera.client"
JAVA_CLASS = "MachineGeometry"

JAVA_HEADER = '''package %s;

/// Where the moving parts of each machine window go.
///
/// GENERATED by tools/gen_machine_guis.py from the same table that paints the backgrounds. Do not
/// edit by hand: the numbers here are the insides of housings painted into the PNGs, and a screen
/// filling a rectangle the art does not have is exactly the mismatch this file exists to prevent.
///
/// Slot positions are *not* here. Those live in the menus, they are gameplay rather than decoration,
/// and the painted wells are fitted around them rather than the other way round.
public final class %s {
    private %s() {}

    /// A rectangle in window space: x and y from the window's top-left corner, then its size.
    public record Rect(int x, int y, int width, int height) {}
''' % (JAVA_PACKAGE, JAVA_CLASS, JAVA_CLASS)


def java_rect(name, box, inset=1):
    """The *inside* of a painted box: what a screen may fill without covering its bevel."""
    left, top, right, bottom = box
    return "    public static final Rect %s = new Rect(%d, %d, %d, %d);\n" % (
        name, left + inset, top + inset, right - left + 1 - inset * 2, bottom - top + 1 - inset * 2)


def emit_java(java_root):
    path = "%s/%s/%s.java" % (java_root, JAVA_PACKAGE.replace(".", "/"), JAVA_CLASS)
    lines = [JAVA_HEADER]

    lines.append(
        "\n    /// The raised rectangle every window is built inside. The energy column bolts itself to"
        "\n    /// the window's left edge across exactly these rows, so the two line up without either"
        "\n    /// knowing the other's numbers.\n")
    lines.append(java_rect("FRAME", FRAME, inset=0))

    for name, layout in MACHINES.items():
        upper = name.upper()
        lines.append("\n    // -- %s\n" % name)
        lines.append(java_rect("%s_TRACK" % upper, track_of(layout)))
        if "tank" in layout:
            lines.append(java_rect("%s_TANK" % upper, layout["tank"]))

    lines.append("}\n")

    with open(path, "w", encoding="utf-8", newline="\n") as java:
        java.write("".join(lines))

    print("%s" % path)


def generate(assets, java_root):
    folder = "%s/textures/gui/container" % assets

    # Read once and kept in memory: the file every window is cut from is also one of the windows
    # this writes, so reading it per machine would cut the last four out of the first one's output.
    # Only the panel is rebuilt, so re-running against an already-generated file is harmless.
    frame = np.array(Image.open("%s/%s" % (folder, SOURCE)).convert("RGBA"))

    for name, layout in MACHINES.items():
        image = build(frame, layout)
        Image.fromarray(image).save("%s/%s.png" % (folder, name))
        print("%s/%s.png" % (folder, name))

    emit_java(java_root)


def preview(assets, preview_dir, scale=3):
    folder = "%s/textures/gui/container" % assets
    names = list(MACHINES)
    tiles = [Image.open("%s/%s.png" % (folder, name)).convert("RGB") for name in names]

    canvas = Image.new("RGB", (len(tiles) * (SIZE + 4) + 4, SIZE + 8), (46, 44, 42))
    for index, tile in enumerate(tiles):
        canvas.paste(tile, (4 + index * (SIZE + 4), 4))

    canvas = canvas.resize((canvas.width * scale, canvas.height * scale), Image.NEAREST)
    canvas.save("%s/preview_guis.png" % preview_dir)
    print("%s/preview_guis.png" % preview_dir)


if __name__ == "__main__":
    assets_root = sys.argv[1] if len(sys.argv) > 1 else "."
    java_target = sys.argv[2] if len(sys.argv) > 2 else "."
    generate(assets_root, java_target)
    if len(sys.argv) > 3:
        preview(assets_root, sys.argv[3])
