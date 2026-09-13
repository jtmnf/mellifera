"""Generates the Beealyzer sprite.

    python tools/gen_tool_textures.py src/main/resources/assets/mellifera/textures/item

The Scoop is NOT here. It is hand-drawn and lives only as its PNG, and there is deliberately no
character map for it in this file: one would be a second copy of the art that overwrites the real
one the next time somebody runs this script.

Not part of the Gradle build. It is kept for the same reason as the other generators in this
folder: a 16x16 PNG is an unmaintainable binary blob in a diff, and this file is what the two
sprites actually are. Editing a row below and re-running is how they change.

It is drawn rather than synthesised, because at this size there is nothing to synthesise -- an
item icon is a hundred or so deliberate pixels. It is written as a character map so the shape is
legible in the source, which is the whole point of generating it here instead of pasting in
base64.

WHAT THE FIRST VERSION GOT WRONG, because the rule is worth writing down: it filled the shape
with one flat colour and ringed it in pure black. That is a sticker, not an object. The mod's
own items -- the Apiarist Database, the Frame, the ingots -- all do the same three things, and
so does this one now:

- Four or five shades per material, not one. A lit side, a body, a shadow side, and a highlight
  on the edge that faces the light. That is the whole of what makes 16 pixels look solid.
- The outline is the material's own darkest shade, never black. Black outlines flatten a sprite
  into a cartoon and make it read as foreign next to vanilla's items.
- Light comes from the top-left, on every sprite, without exception. Two items lit from
  different corners sitting next to each other in a hotbar look like two different mods.

Nothing here is derived from Mojang's art. The leather and the brass are the machines' own casing
colours (see gen_machine_textures.py, and the block textures themselves), so the book reads as
part of the same workshop.
"""
import sys

import numpy as np
from PIL import Image

TRANSPARENT = (0, 0, 0, 0)


def opaque(rgb):
    return ((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255)


# A ledger, not a gadget.
#
# The shape is the Apiarist Database's own, pixel for pixel: the same closed book seen from
# above-front, the same page block along the bottom-left edge. Deliberately the same, because
# the two are a pair -- one lists what a species is and the other reads what an individual is --
# and a set of tools should look like a set. What separates them is the leather, which here is
# the machines' own casing brown rather than the Database's red, and a brass inlay worked into
# the cover where the Database has a blind emboss.
#
# It glints in the hand as well, which no character map can express: that rides on an
# ENCHANTMENT_GLINT_OVERRIDE component, see MelliferaItems.
BEEALYZER = [
    "................",
    "........aaa.....",
    "......aabcda....",
    "....aabccccca...",
    "..aabcccRcccca..",
    "aabdcccccRcccda.",
    "abdccRccccRcccea",
    "aabcccRcccccref.",
    "aagbcccccceehgh.",
    "ibjgbdcceehgghae",
    ".ibjgbeehgghaeii",
    "..ibjghgghaeii..",
    "...ibjgheeii....",
    "....ibeeii......",
    ".....iii........",
    "................",
]

# The machine casing ramp, lifted off the block textures so the book and the Centrifuge standing
# next to it are the same brown (see gen_machine_textures.py, and the blocks themselves).
BEEALYZER_PALETTE = {
    "i": opaque(0x1D160C),  # outline: the casing's deepest shade, never black
    "a": opaque(0x2A2012),  # the cover's shadowed edge
    "b": opaque(0x4A371B),  # leather in shadow
    "d": opaque(0x634B26),  # leather, turning away from the light
    "c": opaque(0x795C2F),  # leather: the odd brown the machines are painted in
    "e": opaque(0x553A16),  # the deep crease along the spine
    "r": opaque(0x8A6A2E),  # brass, shadowed
    "R": opaque(0xC9A04E),  # brass inlay, the rivet colour off the machine casings
    "f": opaque(0x8A7A52),  # page block, bottom edge
    "h": opaque(0xA89768),  # pages in shadow
    "g": opaque(0xC9B98A),  # pages
    "j": opaque(0xE8DCB1),  # the top leaf, catching the light
}


def draw(rows, palette):
    size = len(rows)
    image = np.zeros((size, size, 4), dtype=np.uint8)

    for y, row in enumerate(rows):
        if len(row) != size:
            raise ValueError("row %d is %d wide, expected %d" % (y, len(row), size))
        for x, key in enumerate(row):
            image[y, x] = TRANSPARENT if key == "." else palette[key]

    return image


def generate(folder):
    for name, rows, palette in (
        ("beealyzer", BEEALYZER, BEEALYZER_PALETTE),
    ):
        path = "%s/%s.png" % (folder, name)
        Image.fromarray(draw(rows, palette)).save(path)
        print(path)


if __name__ == "__main__":
    generate(sys.argv[1] if len(sys.argv) > 1 else ".")
