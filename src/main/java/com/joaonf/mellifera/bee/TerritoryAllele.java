package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/// Radius, in blocks, of the apiary's working area. Feeds three unrelated subsystems --
/// the mutation block/biome scan, the periodic effect, and the flowering pass -- so it
/// stays meaningful even with only a handful of species in play.
///
/// Real Forestry (core/genetics/alleles/EnumAllele.Territory, LGPL v3) stores these as a
/// full x/y/z box -- AVERAGE(9,6,9), LARGE(11,8,11), LARGER(13,12,13), LARGEST(15,13,15).
/// This engine only ever scans a square radius, so each tier keeps the real box width and
/// halves it; the y extent is dropped rather than invented into something it isn't.
public enum TerritoryAllele implements Allele, StringRepresentable {
    AVERAGE("average", 9, true),
    LARGE("large", 11, false),
    LARGER("larger", 13, false),
    LARGEST("largest", 15, false);

    public static final Codec<TerritoryAllele> CODEC = StringRepresentable.fromEnum(TerritoryAllele::values);

    private final String name;
    private final int width;
    private final boolean dominant;

    TerritoryAllele(String name, int width, boolean dominant) {
        this.name = name;
        this.width = width;
        this.dominant = dominant;
    }

    public int radius() {
        return width / 2;
    }

    @Override
    public boolean dominant() {
        return dominant;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }
}
