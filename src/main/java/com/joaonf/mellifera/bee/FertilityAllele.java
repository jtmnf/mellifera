package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/// How many offspring (drones, each an independent mutation roll) a queen leaves behind
/// when she dies. Also caps how many combs a single production pulse inserts.
///
/// Values and dominance from real Forestry (core/genetics/alleles/EnumAllele.Fertility,
/// LGPL v3): the two low tiers are dominant, so high fertility stays a recessive trait
/// that has to be bred for deliberately.
public enum FertilityAllele implements Allele, StringRepresentable {
    LOW("low", 1, true),
    NORMAL("normal", 2, true),
    HIGH("high", 3, false),
    MAXIMUM("maximum", 4, false);

    public static final Codec<FertilityAllele> CODEC = StringRepresentable.fromEnum(FertilityAllele::values);

    private final String name;
    private final int offspring;
    private final boolean dominant;

    FertilityAllele(String name, int offspring, boolean dominant) {
        this.name = name;
        this.offspring = offspring;
        this.dominant = dominant;
    }

    public int offspring() {
        return offspring;
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
