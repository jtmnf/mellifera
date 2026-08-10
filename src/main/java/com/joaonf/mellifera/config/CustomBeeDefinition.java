package com.joaonf.mellifera.config;

import java.util.List;
import java.util.Optional;

import com.joaonf.mellifera.bee.BeeMutation;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.bee.BeeTemplate;
import com.joaonf.mellifera.bee.CombProduct;
import com.joaonf.mellifera.bee.MutationCondition;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/// One bee written by hand, as it appears in `config/mellifera/custom_bees/*.json`.
///
/// A shape of its own rather than a codec straight onto BeeSpecies, because the two have
/// different audiences. BeeSpecies is nine positional components tuned for the Java table;
/// this is what someone types at midnight without the source open, so the temperature band
/// and the two colours are named objects, the traits are the seven allele names rather than
/// a template, and everything that has a sensible default may be left out. The only required
/// fields are the name and at least one comb.
///
/// Mutations live here too, in the same file as the bee they produce. Splitting them would
/// mean editing two files to add one bee -- and a bee with no mutation is one nothing can
/// breed, which is a strange default for a file whose whole purpose is adding content.
///
/// @param name shown in-game. Used as a translation key, so a resource pack can localise it
///             and anything without one simply displays the string as written
/// @param branch which group the Apiarist Database files it under, `custom` if omitted
/// @param minCelsius lowest working temperature, before ToleranceAllele widens it
/// @param maxCelsius highest working temperature
/// @param dominant whether this species' allele beats a recessive partner's
/// @param primaryColor abdomen colour, `"#RRGGBB"` or a raw integer. The only colour a species
///                     has: BeeTextures repaints the abdomen with it and leaves the rest of
///                     Vanilla's bee sheet alone
/// @param glint whether the item renders with the enchantment shimmer, as branch tops do
/// @param combs what an apiary yields, and how often -- existing combs by id, new ones
///              defined on the spot
/// @param traits the alleles it is born with; anything omitted takes Forestry's baseline
/// @param mutations crosses that produce this bee
public record CustomBeeDefinition(
    String name,
    Optional<String> branch,
    float minCelsius,
    float maxCelsius,
    boolean dominant,
    int primaryColor,
    boolean glint,
    List<CombEntry> combs,
    BeeTemplate traits,
    List<Cross> mutations
) {
    public static final Codec<CustomBeeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("name").forGetter(CustomBeeDefinition::name),
        Codec.STRING.optionalFieldOf("branch").forGetter(CustomBeeDefinition::branch),
        Codec.FLOAT.optionalFieldOf("min_celsius", 0.0F).forGetter(CustomBeeDefinition::minCelsius),
        Codec.FLOAT.optionalFieldOf("max_celsius", 30.0F).forGetter(CustomBeeDefinition::maxCelsius),
        Codec.BOOL.optionalFieldOf("dominant", false).forGetter(CustomBeeDefinition::dominant),
        ConfigCodecs.COLOR.optionalFieldOf("primary_color", 0xFFDC16).forGetter(CustomBeeDefinition::primaryColor),
        Codec.BOOL.optionalFieldOf("glint", false).forGetter(CustomBeeDefinition::glint),
        CombEntry.CODEC.listOf().fieldOf("combs").forGetter(CustomBeeDefinition::combs),
        BeeTemplate.CODEC.optionalFieldOf("traits", BeeTemplate.DEFAULT).forGetter(CustomBeeDefinition::traits),
        Cross.CODEC.listOf().optionalFieldOf("mutations", List.of()).forGetter(CustomBeeDefinition::mutations)
    ).apply(instance, CustomBeeDefinition::new));

    /// One line of the output table: which comb, and how often.
    ///
    /// `comb` is either an id -- `"mellifera:stone"`, any of the 23 built-ins or a comb another
    /// file invents -- or the definition of a comb that does not exist yet (see
    /// CustomCombDefinition). One field for both, rather than a second field beside it, because
    /// they are the same question answered two ways, and a shape with `comb` *and* `new_comb`
    /// invites a file that fills in both and means neither.
    ///
    /// @param comb the comb produced, named or defined
    /// @param chance per-pulse odds an Apiary yields it, before frames. Edited here rather than
    ///               in `bees.toml`, which covers only the bees declared in Java
    public record CombEntry(Either<Identifier, CustomCombDefinition> comb, float chance) {
        public static final Codec<CombEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.either(Identifier.CODEC, CustomCombDefinition.CODEC).fieldOf("comb").forGetter(CombEntry::comb),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(CombEntry::chance)
        ).apply(instance, CombEntry::new));

        public Identifier combId() {
            return comb.map(id -> id, CustomCombDefinition::id);
        }

        /// The comb this entry invents, or empty when it only names one.
        public Optional<CustomCombDefinition> definition() {
            return comb.right();
        }

        public CombProduct toProduct() {
            return new CombProduct(combId(), chance);
        }
    }

    /// One cross that produces this bee. Conditions -- biome, climate, date, nearby block or
    /// fluid -- are deliberately not exposed yet: they are a sealed hierarchy needing a
    /// dispatch codec of its own, and shipping half of one would mean a JSON field that
    /// silently accepts three of the five kinds.
    ///
    /// @param parents the two species crossed, in either order
    /// @param chance per-brood odds, before frames and objectives scale them
    public record Cross(List<Identifier> parents, float chance) {
        public static final Codec<Cross> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf(2, 2).fieldOf("parents").forGetter(Cross::parents),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(Cross::chance)
        ).apply(instance, Cross::new));
    }

    public BeeSpecies toSpecies() {
        return new BeeSpecies(name, minCelsius, maxCelsius, dominant, primaryColor,
            combs.stream().map(CombEntry::toProduct).toList(), traits, glint);
    }

    /// The combs this bee invents, in the order it lists them. A bee that only names existing
    /// combs returns nothing, which is the common case.
    public List<CustomCombDefinition> newCombs() {
        return combs.stream().flatMap(entry -> entry.definition().stream()).toList();
    }

    public List<BeeMutation> toMutations(Identifier result) {
        return mutations.stream()
            .map(cross -> new BeeMutation(cross.parents().get(0), cross.parents().get(1), result,
                cross.chance(), new MutationCondition.None(), Optional.empty()))
            .toList();
    }
}
