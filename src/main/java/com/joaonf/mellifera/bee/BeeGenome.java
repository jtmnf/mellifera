package com.joaonf.mellifera.bee;

import java.util.function.Predicate;

import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/// The full set of a bee individual's genes: one chromosome per trait, each carrying an
/// active (expressed) and inactive (hidden) allele. Princess and Drone stacks carry one
/// of these directly; a Queen carries two (see QueenGenomeData), her own plus her mate's.
public record BeeGenome(
    Chromosome<Identifier> species,
    Chromosome<SpeedAllele> speed,
    Chromosome<LifespanAllele> lifespan,
    Chromosome<TerritoryAllele> territory,
    Chromosome<FertilityAllele> fertility,
    Chromosome<ToleranceAllele> tolerance,
    Chromosome<EffectAllele> effect,
    Chromosome<FloweringAllele> flowering) {

    /// A pure Forest genome -- the fallback for a stack carrying no BEE_GENOME component.
    ///
    /// Deliberately lazy: pure() reads the species' template out of the BeeSpecies registry,
    /// and this class is loaded during data-component registration (MelliferaDataComponents
    /// references CODEC), which happens before that registry is populated. Holding it in a
    /// nested class defers the lookup to first *use*, which is always runtime.
    public static BeeGenome defaultGenome() {
        return Default.INSTANCE;
    }

    private static final class Default {
        private static final BeeGenome INSTANCE = pure(MelliferaBeeSpecies.FOREST.getId());
    }

    public static final Codec<BeeGenome> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Chromosome.codec(Identifier.CODEC).fieldOf("species").forGetter(BeeGenome::species),
        Chromosome.codec(SpeedAllele.CODEC).fieldOf("speed").forGetter(BeeGenome::speed),
        Chromosome.codec(LifespanAllele.CODEC).fieldOf("lifespan").forGetter(BeeGenome::lifespan),
        Chromosome.codec(TerritoryAllele.CODEC).fieldOf("territory").forGetter(BeeGenome::territory),
        Chromosome.codec(FertilityAllele.CODEC).fieldOf("fertility").forGetter(BeeGenome::fertility),
        Chromosome.codec(ToleranceAllele.CODEC).fieldOf("tolerance").forGetter(BeeGenome::tolerance),
        Chromosome.codec(EffectAllele.CODEC).fieldOf("effect").forGetter(BeeGenome::effect),
        Chromosome.codec(FloweringAllele.CODEC).fieldOf("flowering").forGetter(BeeGenome::flowering)
    ).apply(instance, BeeGenome::new));

    /// A wild-type individual of a species: both alleles identical on every chromosome,
    /// set to that species' own starting template rather than a flat Normal/Average across
    /// the board. This is what makes a fresh Forest bee already breed well and flower
    /// slowly while a Cultivated one is fast but short-lived, exactly as in real Forestry --
    /// see BeeTemplate and each species' registration.
    public static BeeGenome pure(Identifier species) {
        BeeTemplate template = MelliferaBeeSpecies.get(species).template();
        return new BeeGenome(
            Chromosome.pure(species),
            Chromosome.pure(template.speed()),
            Chromosome.pure(template.lifespan()),
            Chromosome.pure(template.territory()),
            Chromosome.pure(template.fertility()),
            Chromosome.pure(template.tolerance()),
            Chromosome.pure(template.effect()),
            Chromosome.pure(template.flowering()));
    }

    /// Combines two parents into a child's genome, trait by trait -- see combine() for
    /// the actual Mendelian rule. Applied independently per trait, so a child can come out
    /// dominant on one trait and recessive on another even from two pure parents. Does not
    /// touch mutation; that's a separate pass over the species chromosome only, run by
    /// MutationEngine once this method returns.
    public static BeeGenome inherited(RandomSource random, BeeGenome parentA, BeeGenome parentB) {
        return inherited(random, parentA, parentB, FrameType.Inheritance.RANDOM);
    }

    /// `mode` is what Dominant/Recessive frames change: instead of each parent contributing
    /// a coin flip between its two alleles, it always contributes the expressed one
    /// (ACTIVE) or the hidden one (INACTIVE). Everything downstream -- dominance, the
    /// mutation roll -- is unchanged.
    public static BeeGenome inherited(RandomSource random, BeeGenome parentA, BeeGenome parentB, FrameType.Inheritance mode) {
        return new BeeGenome(
            combine(random, parentA.species, parentB.species, MelliferaBeeSpecies::dominant, mode),
            combine(random, parentA.speed, parentB.speed, Allele::dominant, mode),
            combine(random, parentA.lifespan, parentB.lifespan, Allele::dominant, mode),
            combine(random, parentA.territory, parentB.territory, Allele::dominant, mode),
            combine(random, parentA.fertility, parentB.fertility, Allele::dominant, mode),
            combine(random, parentA.tolerance, parentB.tolerance, Allele::dominant, mode),
            combine(random, parentA.effect, parentB.effect, Allele::dominant, mode),
            combine(random, parentA.flowering, parentB.flowering, Allele::dominant, mode));
    }

    /// One trait's worth of Mendelian inheritance: each parent contributes a random pick
    /// from its own active/inactive pair, then whichever of the two picks is dominant
    /// becomes the child's active allele. Both dominant or both recessive is a coin flip.
    private static <T> Chromosome<T> combine(RandomSource random, Chromosome<T> parentA, Chromosome<T> parentB,
                                             Predicate<T> dominant, FrameType.Inheritance mode) {
        T fromA = contribute(random, parentA, mode);
        T fromB = contribute(random, parentB, mode);

        boolean aDominant = dominant.test(fromA);
        boolean bDominant = dominant.test(fromB);

        if (aDominant != bDominant) {
            return aDominant ? new Chromosome<>(fromA, fromB) : new Chromosome<>(fromB, fromA);
        }

        return random.nextBoolean() ? new Chromosome<>(fromA, fromB) : new Chromosome<>(fromB, fromA);
    }

    private static <T> T contribute(RandomSource random, Chromosome<T> parent, FrameType.Inheritance mode) {
        return switch (mode) {
            case ACTIVE -> parent.active();
            case INACTIVE -> parent.inactive();
            case RANDOM -> random.nextBoolean() ? parent.active() : parent.inactive();
        };
    }
}
