package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/// A gene a bee either has or does not: nocturnal, tolerant flyer, cave dwelling.
///
/// One allele type shared by all three rather than three two-valued enums, because there is
/// nothing to tell apart -- they are the same yes/no with the same dominance, and what differs
/// between them is only which stop they lift. The tooltip still names them separately: allele
/// keys are `allele.mellifera.<trait>.<value>`, so "Nocturnal: Yes" and "Cave dwelling: No" are
/// their own strings and a translator is never handed a bare "yes" to place in three sentences.
///
/// NO is the dominant one, and that is the whole shape of these traits. A bee that cannot work in
/// the dark is the ordinary case, so the useful allele is the recessive one: crossing a nocturnal
/// line into a diurnal one buries the trait rather than spreading it, and getting it back out is
/// the work. It is also what makes the Recessive frame worth building -- before these genes, that
/// frame was a tool with very little to point at.
public enum ToggleAllele implements Allele, StringRepresentable {
    NO("no", true),
    YES("yes", false);

    public static final Codec<ToggleAllele> CODEC = StringRepresentable.fromEnum(ToggleAllele::values);

    private final String name;
    private final boolean dominant;

    ToggleAllele(String name, boolean dominant) {
        this.name = name;
        this.dominant = dominant;
    }

    public static ToggleAllele of(boolean value) {
        return value ? YES : NO;
    }

    public boolean isSet() {
        return this == YES;
    }

    @Override
    public boolean dominant() {
        return dominant;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
