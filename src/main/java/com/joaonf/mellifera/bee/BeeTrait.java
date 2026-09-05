package com.joaonf.mellifera.bee;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBeeSpecies;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/// The eight chromosomes of a BeeGenome, named and ordered, so machinery can walk a genome
/// trait by trait instead of hard-coding eight branches everywhere.
///
/// Declaration order *is* the Isolator's extraction order (see IsolatorBlockEntity's
/// cursor), and it deliberately matches the order BeeTooltip prints, so what comes out of
/// the machine lines up with what the player reads on the bee's tooltip.
///
/// Each constant also owns the colour its serum's liquid layer renders in (see
/// SerumTintSource). They are hand-picked to be eight clearly distinct hues rather than
/// derived from anything -- a hash of the trait name would give two near-identical blues
/// sooner or later, and the whole point of the colour is telling two serums apart at a
/// glance in a chest.
public enum BeeTrait implements StringRepresentable {
    SPECIES("species", 0xF2C14E),
    SPEED("speed", 0x4FA3E3),
    LIFESPAN("lifespan", 0x9B6BE0),
    FERTILITY("fertility", 0xE0619B),
    TERRITORY("territory", 0x5FBF60),
    TOLERANCE("tolerance", 0xE3703A),
    EFFECT("effect", 0x3FD4C0),
    FLOWERING("flowering", 0xD64545);

    public static final BeeTrait[] ALL = values();

    private final String name;
    private final int liquidColor;

    BeeTrait(String name, int liquidColor) {
        this.name = name;
        this.liquidColor = liquidColor;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }

    /// The chromosome's own name, with no value attached.
    ///
    /// A key of its own rather than reusing tooltip.mellifera.bee.<trait>, which is "Speed: %s" and
    /// needs a value to say anything. The JEI pages for the Isolator and the Infuser are about the
    /// gene rather than about one bee's copy of it, so they have nothing to put in that slot.
    public Component label() {
        return Component.translatable("trait.mellifera." + name);
    }

    /// Packed 0xRRGGBB, multiplied over the serum's white liquid mask.
    public int liquidColor() {
        return liquidColor;
    }

    public static @Nullable BeeTrait byName(String name) {
        for (BeeTrait trait : ALL) {
            if (trait.name.equals(name)) {
                return trait;
            }
        }

        return null;
    }

    /// The value a serum records for this trait on a given genome, as a plain string:
    /// the allele's serialized name, or the species Identifier for the species chromosome.
    ///
    /// Deliberately a string rather than the allele object: it is exactly what the matching
    /// allele Codec round-trips, so a future injector can turn it back into a real allele
    /// with `SpeedAllele.CODEC.parse(...)` without the serum item ever having to be generic
    /// over eight different allele types.
    public String activeValue(BeeGenome genome) {
        return switch (this) {
            case SPECIES -> genome.species().active().toString();
            case SPEED -> genome.speed().active().getSerializedName();
            case LIFESPAN -> genome.lifespan().active().getSerializedName();
            case FERTILITY -> genome.fertility().active().getSerializedName();
            case TERRITORY -> genome.territory().active().getSerializedName();
            case TOLERANCE -> genome.tolerance().active().getSerializedName();
            case EFFECT -> genome.effect().active().getSerializedName();
            case FLOWERING -> genome.flowering().active().getSerializedName();
        };
    }

    /// Writes a serum's value back onto a genome, the exact inverse of activeValue.
    ///
    /// Replaces the chromosome outright -- both alleles, not just the expressed one. Writing
    /// only the active half left the old allele sitting in the hidden slot, where the bee's
    /// tooltip prints it in brackets: a player who infused Fastest onto a slow bee got a bee
    /// that read "Fastest (Slowest)" and would throw Slowest again the moment it was bred.
    /// Infusing is a replacement, so the trait it replaces is gone.
    ///
    /// Returns the genome unchanged when the value does not parse, which is what happens to
    /// a serum whose allele has been renamed or removed between versions. Refusing quietly
    /// beats writing a default over a trait the player spent a machine cycle on.
    public BeeGenome applyTo(BeeGenome genome, String value) {
        return switch (this) {
            case SPECIES -> {
                Identifier id = Identifier.tryParse(value);
                yield id == null || MelliferaBeeSpecies.REGISTRY.getValue(id) == null
                    ? genome
                    : withSpecies(genome, id);
            }
            case SPEED -> replace(SpeedAllele.values(), value, allele -> new BeeGenome(
                genome.species(), Chromosome.pure(allele), genome.lifespan(), genome.territory(),
                genome.fertility(), genome.tolerance(), genome.effect(), genome.flowering()), genome);
            case LIFESPAN -> replace(LifespanAllele.values(), value, allele -> new BeeGenome(
                genome.species(), genome.speed(), Chromosome.pure(allele), genome.territory(),
                genome.fertility(), genome.tolerance(), genome.effect(), genome.flowering()), genome);
            case FERTILITY -> replace(FertilityAllele.values(), value, allele -> new BeeGenome(
                genome.species(), genome.speed(), genome.lifespan(), genome.territory(),
                Chromosome.pure(allele), genome.tolerance(), genome.effect(), genome.flowering()), genome);
            case TERRITORY -> replace(TerritoryAllele.values(), value, allele -> new BeeGenome(
                genome.species(), genome.speed(), genome.lifespan(), Chromosome.pure(allele),
                genome.fertility(), genome.tolerance(), genome.effect(), genome.flowering()), genome);
            case TOLERANCE -> replace(ToleranceAllele.values(), value, allele -> new BeeGenome(
                genome.species(), genome.speed(), genome.lifespan(), genome.territory(),
                genome.fertility(), Chromosome.pure(allele), genome.effect(), genome.flowering()), genome);
            case EFFECT -> replace(EffectAllele.values(), value, allele -> new BeeGenome(
                genome.species(), genome.speed(), genome.lifespan(), genome.territory(),
                genome.fertility(), genome.tolerance(), Chromosome.pure(allele), genome.flowering()), genome);
            case FLOWERING -> replace(FloweringAllele.values(), value, allele -> new BeeGenome(
                genome.species(), genome.speed(), genome.lifespan(), genome.territory(),
                genome.fertility(), genome.tolerance(), genome.effect(), Chromosome.pure(allele)), genome);
        };
    }

    private static BeeGenome withSpecies(BeeGenome genome, Identifier species) {
        return new BeeGenome(
            Chromosome.pure(species), genome.speed(), genome.lifespan(),
            genome.territory(), genome.fertility(), genome.tolerance(), genome.effect(), genome.flowering());
    }

    /// Finds the allele of `values` whose serialized name is `value` and hands it to
    /// `build`, or gives back `fallback` when nothing matches.
    private static <T extends StringRepresentable> BeeGenome replace(
        T[] values, String value, java.util.function.Function<T, BeeGenome> build, BeeGenome fallback) {
        for (T allele : values) {
            if (allele.getSerializedName().equals(value)) {
                return build.apply(allele);
            }
        }

        return fallback;
    }

    /// Reuses the same translation keys the bee tooltips already use, so a serum never
    /// disagrees with the bee it came out of. Species names live in the species registry;
    /// every other trait has an `allele.mellifera.<trait>.<serializedName>` key.
    public Component valueName(String value) {
        if (this == SPECIES) {
            Identifier id = Identifier.tryParse(value);
            return Component.translatable(
                id == null ? MelliferaBeeSpecies.FOREST.get().translationKey() : MelliferaBeeSpecies.get(id).translationKey());
        }

        return Component.translatable("allele.mellifera." + name + "." + value);
    }

    /// "Speed: Fast" -- the `tooltip.mellifera.bee.<trait>` key, label and value together.
    public Component describe(String value) {
        return Component.translatable("tooltip.mellifera.bee." + name, valueName(value));
    }
}
