package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// A mated queen remembers both her own genome and the drone's she mated with -- that
/// second genome is what mutation checks against at death, and what a fresh drone
/// offspring can inherit from, even though it's never itself expressed while she's alive.
public record QueenGenomeData(BeeGenome own, BeeGenome mate) {
    /// Lazy for the same reason BeeGenome.defaultGenome() is: this class is loaded during
    /// data-component registration (MelliferaDataComponents references CODEC), which runs
    /// before the BeeSpecies registry that BeeGenome.pure() reads from is populated.
    public static QueenGenomeData defaultGenome() {
        return Default.INSTANCE;
    }

    private static final class Default {
        private static final QueenGenomeData INSTANCE =
            new QueenGenomeData(BeeGenome.defaultGenome(), BeeGenome.defaultGenome());
    }

    public static final Codec<QueenGenomeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BeeGenome.CODEC.fieldOf("own").forGetter(QueenGenomeData::own),
        BeeGenome.CODEC.fieldOf("mate").forGetter(QueenGenomeData::mate)
    ).apply(instance, QueenGenomeData::new));
}
