package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// A diploid gene pair for one trait: an active (expressed) allele and an inactive
/// (hidden) one. Breeding two individuals can bring an inactive allele back to the
/// surface, which is the whole point of keeping both around instead of collapsing to a
/// single expressed value.
public record Chromosome<T>(T active, T inactive) {
    public static <T> Codec<Chromosome<T>> codec(Codec<T> alleleCodec) {
        return RecordCodecBuilder.create(instance -> instance.group(
            alleleCodec.fieldOf("active").forGetter(Chromosome::active),
            alleleCodec.fieldOf("inactive").forGetter(Chromosome::inactive)
        ).apply(instance, Chromosome::new));
    }

    /// A chromosome with the same allele on both sides -- what a freshly authored
    /// species/trait starts as before any breeding has happened.
    public static <T> Chromosome<T> pure(T allele) {
        return new Chromosome<>(allele, allele);
    }
}
