package com.joaonf.mellifera.client.special;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/// Builds a per-species bee texture by recolouring vanilla's own.
///
/// The client already has `textures/entity/bee/bee.png`, so this reads it back out of the
/// resource manager, repaints two regions of it, and registers the result as a dynamic
/// texture. Nothing is shipped and nothing is copied to disk -- the recolour is computed
/// on the player's own copy of Mojang's art.
///
/// Recolouring rather than tinting is the whole point. A tint multiplies the entire
/// submitted model, so it lands on the yellow as much as the brown and the bee ends up
/// looking washed over. Repainting texels lets the front of the bee stay exactly vanilla
/// while only the abdomen takes the species colour.
///
/// Each repainted texel keeps its own brightness -- `colour x luminance`, normalised so the
/// brightest texel in the region lands on the colour itself. That is what preserves the
/// shape: the dark bands stay dark because their luminance is low, so the abdomen reads as
/// striped in the species colour rather than as a flat patch of it.
public final class BeeTextures {
    private BeeTextures() {}

    private static final Identifier VANILLA = Identifier.withDefaultNamespace("textures/entity/bee/bee.png");
    private static final int GOLD = 0xF7C738;

    /// Texel rectangles, read off vanilla's 64x64 sheet. The body is one 7x7x10 box at
    /// texOffs(0,0), which Minecraft unwraps as down|up along the top and east|north|west|south
    /// in a row beneath. The face with the eyes is `north`, so the half of each side face
    /// furthest from it is the abdomen -- these are those halves, plus the whole `south`
    /// cap, which is the blunt end of the tail.
    private static final int[][] ABDOMEN = {
        {10, 0, 7, 5},    // down, tail half
        {17, 0, 7, 5},    // up, tail half
        {0, 10, 5, 7},    // east, tail half
        {22, 10, 5, 7},   // west, tail half
        {27, 10, 7, 7},   // south -- the whole tail cap
    };

    /// Both antennae, from texOffs(2,0) and texOffs(2,3) of a 1x2x3 box. They overlap by
    /// three rows on the sheet, so one rectangle covers both.
    private static final int[][] ANTENNAE = {
        {2, 0, 8, 8},
    };

    private static final Map<Long, Identifier> CACHE = new HashMap<>();

    /// @param color ARGB species colour applied to the abdomen
    /// @param goldAntennae whether to gild the antennae as well -- the queen's marking
    public static Identifier get(int color, boolean goldAntennae) {
        long key = (long) color << 1 | (goldAntennae ? 1 : 0);
        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        Identifier id = Identifier.fromNamespaceAndPath("mellifera",
            "bee_recolour/%08x_%s".formatted(color, goldAntennae ? "gold" : "plain"));
        NativeImage image = recolour(color, goldAntennae);
        if (image == null) {
            return VANILLA; // resources not readable -- fall back to the plain vanilla bee
        }

        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
        CACHE.put(key, id);
        return id;
    }

    private static NativeImage recolour(int color, boolean goldAntennae) {
        NativeImage image;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(VANILLA)) {
            image = NativeImage.read(in);
        } catch (IOException e) {
            return null;
        }

        paint(image, ABDOMEN, color);
        if (goldAntennae) {
            paint(image, ANTENNAE, GOLD);
        }
        return image;
    }

    private static void paint(NativeImage image, int[][] regions, int color) {
        float peak = peakLuminance(image, regions);
        if (peak <= 0.0F) {
            return;
        }

        int cr = color >> 16 & 0xFF;
        int cg = color >> 8 & 0xFF;
        int cb = color & 0xFF;

        for (int[] r : regions) {
            for (int y = r[1]; y < r[1] + r[3]; y++) {
                for (int x = r[0]; x < r[0] + r[2]; x++) {
                    int argb = image.getPixel(x, y);
                    int alpha = argb >>> 24;
                    if (alpha == 0) {
                        continue;
                    }
                    float scale = luminance(argb) / peak;
                    image.setPixel(x, y, alpha << 24
                        | clamp(cr * scale) << 16
                        | clamp(cg * scale) << 8
                        | clamp(cb * scale));
                }
            }
        }
    }

    /// Normalising against the brightest texel in the region is what stops every species
    /// coming out dark: vanilla's brightest yellow only reaches about 0.83 luminance, so
    /// scaling by raw luminance alone would never let a colour show at full strength.
    private static float peakLuminance(NativeImage image, int[][] regions) {
        float peak = 0.0F;
        for (int[] r : regions) {
            for (int y = r[1]; y < r[1] + r[3]; y++) {
                for (int x = r[0]; x < r[0] + r[2]; x++) {
                    int argb = image.getPixel(x, y);
                    if ((argb >>> 24) != 0) {
                        peak = Math.max(peak, luminance(argb));
                    }
                }
            }
        }
        return peak;
    }

    private static float luminance(int argb) {
        return (0.2126F * (argb >> 16 & 0xFF) + 0.7152F * (argb >> 8 & 0xFF) + 0.0722F * (argb & 0xFF)) / 255.0F;
    }

    private static int clamp(float v) {
        return Math.clamp((int) (v + 0.5F), 0, 255);
    }
}
