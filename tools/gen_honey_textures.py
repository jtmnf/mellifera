"""Generates the liquid-honey sprite sheets: water's look, in amber.

    python tools/gen_honey_textures.py src/main/resources/assets/mellifera/textures/block

Not part of the Gradle build. It is kept because the two PNGs it writes are otherwise
unmaintainable binary blobs: this file is what "the honey texture" actually is, and tuning the
fluid's look means editing the ramp or a cutoff here and running it again.

Nothing here is derived from Mojang's art -- the pattern is synthesised. What is borrowed is
water's *character*, measured off it rather than copied: 16x16 still, 32x32 flow, 32 frames
each, translucent, and, the part that matters most, almost flat. Vanilla water is a nearly
uniform sheet with thin dashes and a scatter of bright specks on it. Every earlier attempt at
this texture failed by being interesting -- a plaid, then swirls, then blobs. A liquid surface
is not interesting, and a 16x16 tile repeated across a lake punishes anything that is.

Three things make it read as honey rather than as orange water: the ramp is warmer and darker
than water's, it is a little more opaque, and it moves at half the speed (the frametimes
written into the .mcmeta files below).
"""
import sys

import numpy as np
from PIL import Image

# Fixed, so regenerating produces the same sheets rather than a gratuitous asset diff.
RNG = np.random.default_rng(20260810)

# Six amber steps with no blue in them. The first four sit close together and carry almost the
# whole surface; the last two are the specks. An earlier version tinted vanilla's blue-grey
# water sprite instead, which came out mud -- blue-grey times amber is arithmetic, not taste.
RAMP = np.array([
    (210, 130, 26),
    (216, 136, 30),
    (222, 142, 34),
    (228, 149, 40),
    (245, 178, 66),
    (255, 214, 128),
], dtype=np.uint8)

# Vanilla water is 180. Honey is thicker, so a little more opaque -- but still translucent,
# which is also what puts the fluid in the translucent render layer at all: FluidModel picks
# the layer off the sprite's own transparency (see MelliferaClient.onRegisterFluidModels).
ALPHA = 210

# Where the four base tones divide, weighted so the flat middle dominates.
BASE_CUTS = [45, 78, 94]

# How rare the two highlight tones are. Vanilla water's brightest texels are well under 1% of
# the sheet, and they are the whole reason a flat surface does not read as a painted wall.
SPARKLE_CUTS = [98.4, 99.6]
FLOW_SPARKLE_CUTS = [98.5, 99.6]


def periodic_noise(shape, falloff, cutoff):
    """Band-limited noise that tiles seamlessly on every axis, time included.

    Built in the frequency domain, so the periodicity is exact rather than approximate: every
    component that survives the filter is a whole number of cycles across the array, which is
    what lets a sheet tile across a lake and loop without a jump.

    `cutoff` is the highest frequency kept per axis, so it is what sets the shape of the
    features: a low cutoff across x with a high one down y gives the wide, thin dashes water
    has, and the reverse gives the vertical streaks of a flow.
    """
    freqs = [np.fft.fftfreq(n) * n for n in shape]
    grid = np.meshgrid(*freqs, indexing="ij")

    radius = np.zeros(shape)
    for f, scale in zip(grid, cutoff):
        radius += (f / scale) ** 2
    radius = np.sqrt(radius)

    with np.errstate(divide="ignore"):
        amplitude = np.where(radius == 0.0, 0.0, radius ** -falloff)
    amplitude[radius > 1.0] = 0.0

    spectrum = amplitude * np.exp(1j * RNG.uniform(0, 2 * np.pi, shape))
    field = np.real(np.fft.ifftn(spectrum))
    return (field - field.mean()) / field.std()


def shade(base, sparkle, sparkle_cuts):
    """Flat base tones from one field, specks from an independent finer one.

    Two fields rather than the top slice of one: taking the highlights off the base field puts
    every speck on the crest of a blob, so they clump into the same little motif in every tile
    and the repeat becomes obvious. Water's specks are scattered, and scattered is what an
    independent high-frequency field gives.
    """
    index = np.searchsorted(np.percentile(base, BASE_CUTS), base)
    thresholds = np.percentile(sparkle, sparkle_cuts)
    index = np.where(sparkle > thresholds[0], 4, index)
    index = np.where(sparkle > thresholds[1], 5, index)

    rgb = RAMP[index]
    alpha = np.full(rgb.shape[:-1] + (1,), ALPHA, dtype=np.uint8)
    return np.concatenate([rgb, alpha], axis=-1)


def still(size=16, frames=32):
    """A surface that drifts: wide, thin, slow. It never churns."""
    base = periodic_noise((frames, size, size), falloff=0.9, cutoff=(2.0, 7.0, 5.5))
    sparkle = periodic_noise((frames, size, size), falloff=0.4, cutoff=(3.0, 8.0, 5.0))
    return shade(base, sparkle, SPARKLE_CUTS)


def flow(size=32, frames=32):
    """Streaks stretched along the flow, scrolling one whole sheet per loop.

    A flow sprite has to read as *going somewhere*, which is the one thing the texture this
    replaces never did. Two layers scroll at different rates so it is not a single printed band
    being dragged past; both wrap a whole number of times in 32 frames, so the loop stays
    seamless.
    """
    slow = periodic_noise((1, size, size), falloff=0.7, cutoff=(1.0, 2.4, 11.0))[0]
    fast = periodic_noise((1, size, size), falloff=0.7, cutoff=(1.0, 1.6, 14.0))[0]
    specks = periodic_noise((1, size, size), falloff=0.4, cutoff=(1.0, 3.0, 9.0))[0]

    base = np.zeros((frames, size, size))
    sparkle = np.zeros((frames, size, size))
    for frame in range(frames):
        # Rolling axis 0 shifts the image down, which is the direction the fluid runs.
        base[frame] = (0.65 * np.roll(slow, frame * size // frames, axis=0)
                       + 0.35 * np.roll(fast, frame * 2 * size // frames, axis=0))
        sparkle[frame] = np.roll(specks, frame * size // frames, axis=0)

    return shade(base, sparkle, FLOW_SPARKLE_CUTS)


def write(sheet, path, frametime):
    frames, size = sheet.shape[0], sheet.shape[1]
    Image.fromarray(sheet.reshape(frames * size, size, 4)).save(path)
    with open(path + ".mcmeta", "w", encoding="utf-8", newline="\n") as meta:
        meta.write('{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % frametime)

    lum = 0.2126 * sheet[..., 0] + 0.7152 * sheet[..., 1] + 0.0722 * sheet[..., 2]
    print("%s  %dx%d  %d frames  frametime %d  lum %.0f-%.0f mean %.0f std %.1f"
          % (path, size, frames * size, frames, frametime,
             lum.min(), lum.max(), lum.mean(), lum.std()))


def contact(sheet, path, cols=8, scale=6):
    """Every frame side by side, for looking at what changed."""
    frames, size = sheet.shape[0], sheet.shape[1]
    rows = (frames + cols - 1) // cols
    canvas = Image.new("RGBA", (cols * (size * scale + 2), rows * (size * scale + 2)), (40, 40, 46, 255))
    for i in range(frames):
        tile = Image.fromarray(sheet[i]).resize((size * scale, size * scale), Image.NEAREST)
        canvas.alpha_composite(tile, ((i % cols) * (size * scale + 2), (i // cols) * (size * scale + 2)))
    canvas.convert("RGB").save(path)


def tiled(sheet, path, repeat=3, pixels=330):
    """One frame tiled, which is how the texture is actually seen and the only honest test of
    whether the pattern is quiet enough."""
    size = sheet.shape[1]
    tile = Image.fromarray(sheet[0])
    canvas = Image.new("RGBA", (size * repeat, size * repeat))
    for x in range(repeat):
        for y in range(repeat):
            canvas.paste(tile, (x * size, y * size))
    canvas.resize((pixels, pixels), Image.NEAREST).convert("RGB").save(path)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."

    s = still()
    f = flow()

    # Half of water's cadence on both sheets (water is 2 and 1). It is the cheapest viscosity
    # cue there is: nothing about the pixels says "thick", but a surface that crawls does.
    write(s, out + "/honey_still.png", frametime=4)
    write(f, out + "/honey_flow.png", frametime=2)

    if len(sys.argv) > 2:
        preview = sys.argv[2]
        contact(s, preview + "/preview_still.png")
        contact(f, preview + "/preview_flow.png")
        tiled(s, preview + "/tile_still.png")
        tiled(f, preview + "/tile_flow.png", repeat=2)
