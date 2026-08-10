package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/// How many successful production pulses a queen has in her before she dies and is
/// replaced by a fresh princess/drone pair. Counted in pulses, not ticks, so it stays
/// meaningful regardless of the active SpeedAllele.
///
/// Tiers, values and dominance are transcribed from real Forestry
/// (core/genetics/alleles/EnumAllele.Lifespan, LGPL v3), where the short-to-shortened band
/// plus ELONGATED are dominant and the extremes at both ends are recessive.
public enum LifespanAllele implements Allele, StringRepresentable {
    SHORTEST("shortest", 10, false),
    SHORTER("shorter", 20, true),
    SHORT("short", 30, true),
    SHORTENED("shortened", 35, true),
    NORMAL("normal", 40, false),
    ELONGATED("elongated", 45, true),
    LONG("long", 50, false),
    LONGER("longer", 60, false),
    LONGEST("longest", 70, false);

    public static final Codec<LifespanAllele> CODEC = StringRepresentable.fromEnum(LifespanAllele::values);

    private final String name;
    private final int cycles;
    private final boolean dominant;

    LifespanAllele(String name, int cycles, boolean dominant) {
        this.name = name;
        this.cycles = cycles;
        this.dominant = dominant;
    }

    public int cycles() {
        return cycles;
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
