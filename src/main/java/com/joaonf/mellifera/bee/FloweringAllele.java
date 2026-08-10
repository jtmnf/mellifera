package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/// Chance multiplier for the apiary's flowering pass to nudge a crop/bonemealable match
/// within the queen's territory toward its next growth stage.
///
/// Tiers and dominance from real Forestry (core/genetics/alleles/EnumAllele.Flowering,
/// LGPL v3), whose raw values are pollination rates (5..99). Kept here as a multiplier
/// against AVERAGE(20) so the numbers stay meaningful to this engine's flowering pass
/// rather than being carried over as unitless constants.
public enum FloweringAllele implements Allele, StringRepresentable {
    SLOWEST("slowest", 5, true),
    SLOWER("slower", 10, false),
    SLOW("slow", 15, false),
    AVERAGE("average", 20, false),
    FAST("fast", 25, false),
    FASTER("faster", 30, false),
    FASTEST("fastest", 35, false),
    MAXIMUM("maximum", 99, true);

    private static final float BASELINE = 20.0F;

    public static final Codec<FloweringAllele> CODEC = StringRepresentable.fromEnum(FloweringAllele::values);

    private final String name;
    private final int rate;
    private final boolean dominant;

    FloweringAllele(String name, int rate, boolean dominant) {
        this.name = name;
        this.rate = rate;
        this.dominant = dominant;
    }

    public float multiplier() {
        return rate / BASELINE;
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
