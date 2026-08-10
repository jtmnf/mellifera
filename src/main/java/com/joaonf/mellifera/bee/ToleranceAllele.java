package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/// Widens a species' [min, max] Celsius band, checked against
/// EnvironmentTemperature.celsius(level, apiaryPos) rather than the biome id directly --
/// so a bee planted next to a furnace or up a mountain reads the same real temperature a
/// player would see on the HUD.
///
/// Tiers and dominance are real Forestry's (core/genetics/alleles/EnumAllele.Tolerance,
/// LGPL v3), including its direction split: UP_n only tolerates hotter, DOWN_n only
/// colder, BOTH_n either way. Forestry's steps are climate-enum notches; this engine's
/// climate is a real Celsius scale, so each step is worth STEP_CELSIUS degrees in the
/// direction(s) the tier allows.
public enum ToleranceAllele implements Allele, StringRepresentable {
    NONE("none", 0, 0, false),

    BOTH_1("both_1", 1, 1, true),
    BOTH_2("both_2", 2, 2, false),
    BOTH_3("both_3", 3, 3, false),
    BOTH_4("both_4", 4, 4, false),
    BOTH_5("both_5", 5, 5, false),

    UP_1("up_1", 0, 1, true),
    UP_2("up_2", 0, 2, false),
    UP_3("up_3", 0, 3, false),
    UP_4("up_4", 0, 4, false),
    UP_5("up_5", 0, 5, false),

    DOWN_1("down_1", 1, 0, true),
    DOWN_2("down_2", 2, 0, false),
    DOWN_3("down_3", 3, 0, false),
    DOWN_4("down_4", 4, 0, false),
    DOWN_5("down_5", 5, 0, false);

    /// One Forestry tolerance notch, in degrees Celsius.
    private static final float STEP_CELSIUS = 5.0F;

    public static final Codec<ToleranceAllele> CODEC = StringRepresentable.fromEnum(ToleranceAllele::values);

    private final String name;
    private final int downSteps;
    private final int upSteps;
    private final boolean dominant;

    ToleranceAllele(String name, int downSteps, int upSteps, boolean dominant) {
        this.name = name;
        this.downSteps = downSteps;
        this.upSteps = upSteps;
        this.dominant = dominant;
    }

    /// How far below the species' own minimum this bee still works, in degrees.
    public float widenBelow() {
        return downSteps * STEP_CELSIUS;
    }

    /// How far above the species' own maximum this bee still works, in degrees.
    public float widenAbove() {
        return upSteps * STEP_CELSIUS;
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
