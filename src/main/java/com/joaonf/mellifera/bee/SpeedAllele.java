package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/// Multiplier on how fast the production timer fills.
///
/// Tiers, values and dominance are transcribed from real Forestry
/// (ForestryMC/ForestryMC, mc-1.12, core/genetics/alleles/EnumAllele.Speed, LGPL v3).
/// Note the dominance pattern is Forestry's, not "slower is recessive": the slow tiers and
/// FAST are dominant, while NORMAL, FASTER and FASTEST are recessive -- which is why a
/// genuinely fast line takes deliberate breeding to fix rather than turning up by luck.
public enum SpeedAllele implements Allele, StringRepresentable {
    SLOWEST("slowest", 0.3F, true),
    SLOWER("slower", 0.6F, true),
    SLOW("slow", 0.8F, true),
    NORMAL("normal", 1.0F, false),
    FAST("fast", 1.2F, true),
    FASTER("faster", 1.4F, false),
    FASTEST("fastest", 1.7F, false);

    public static final Codec<SpeedAllele> CODEC = StringRepresentable.fromEnum(SpeedAllele::values);

    private final String name;
    private final float multiplier;
    private final boolean dominant;

    SpeedAllele(String name, float multiplier, boolean dominant) {
        this.name = name;
        this.multiplier = multiplier;
        this.dominant = dominant;
    }

    public float multiplier() {
        return multiplier;
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
