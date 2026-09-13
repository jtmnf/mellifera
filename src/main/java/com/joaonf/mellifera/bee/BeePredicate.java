package com.joaonf.mellifera.bee;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.joaonf.mellifera.registry.MelliferaBeeMutations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.resources.Identifier;

/// "Is this stack a bee, and which one?", as something an advancement can say.
///
/// WHY IT EXISTS. An advancement for breeding has to be able to name a bee, and the two ways
/// Vanilla offers do not reach one. An item predicate can name an item, but every Princess in
/// the game is the same item; it can also match a component exactly, but a genome is eight
/// chromosomes of which only the first is being asked about, so an exact match would mean
/// writing out a whole bee and would then only match a bee with precisely those other seven.
///
/// So this is registered as a data component predicate -- the same extension point Vanilla's
/// own `minecraft:enchantments` and `minecraft:potion_contents` use -- and an advancement asks
/// for it by name:
///
/// ```json
/// { "predicates": { "mellifera:bee": { "min_tier": 1 } } }
/// ```
///
/// Both fields are optional and both must hold. With neither, this is simply "any bee", which
/// is worth having on its own: it is the one predicate that covers a Princess, a Drone and a
/// Queen at once without naming three items.
///
/// It reads the expressed species only. The recessive allele a bee is carrying is the more
/// interesting half of this mod, but it is not something a player has *reached* -- surfacing it
/// is, and that surfaces as an expressed species in the next generation.
public record BeePredicate(Optional<List<Identifier>> species, Optional<Integer> minTier) implements DataComponentPredicate {
    public static final Codec<BeePredicate> CODEC = RecordCodecBuilder.create(
        i -> i.group(
                Identifier.CODEC.listOf().optionalFieldOf("species").forGetter(BeePredicate::species),
                Codec.INT.optionalFieldOf("min_tier").forGetter(BeePredicate::minTier)
            )
            .apply(i, BeePredicate::new));

    public static final Type<BeePredicate> TYPE = new ConcreteType<>(CODEC);

    /// The tier table, and the mutation list it was computed from.
    ///
    /// One field rather than two so a reader can never see a table matched against the wrong
    /// list, and volatile because advancement triggers run on the server thread while the
    /// mutation table is built during registration on another.
    private record Tiers(List<BeeMutation> from, Map<Identifier, Integer> byId) {}

    private static volatile Tiers tiers = new Tiers(List.of(), Map.of());

    @Override
    public boolean matches(DataComponentGetter components) {
        Identifier expressed = BeeStacks.speciesOf(components);
        if (expressed == null) {
            return false;
        }

        if (species.isPresent() && !species.get().contains(expressed)) {
            return false;
        }

        return minTier.isEmpty() || tierOf(expressed) >= minTier.get();
    }

    /// How deep into the breeding tree a species sits -- 0 for anything no cross produces, so
    /// `min_tier: 1` is exactly "this bee came out of a mutation".
    ///
    /// Cached, because BeeProgression.tiers relaxes the whole mutation table to a fixed point
    /// and this runs on every inventory change. The table is replaced wholesale exactly once,
    /// when custom bees join it during registration (see MelliferaBeeMutations.all), so keeping
    /// the list it was computed from and comparing by identity is enough to notice: a different
    /// list means a different table, and nothing else produces one.
    private static int tierOf(Identifier species) {
        List<BeeMutation> mutations = MelliferaBeeMutations.all();

        Tiers cached = tiers;
        if (cached.from() != mutations) {
            cached = new Tiers(mutations, BeeProgression.tiers(mutations));
            tiers = cached;
        }

        return cached.byId().getOrDefault(species, 0);
    }
}
