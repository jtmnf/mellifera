package com.joaonf.mellifera.bee;

import java.util.Optional;

import net.minecraft.resources.Identifier;

/// One entry in the mutation table: breeding parentA with parentB (either order) has a
/// chance to produce result on the offspring's species chromosome instead of an ordinary
/// inherited species, gated by an extra condition on top of the always-checked pair match.
///
/// forcedEffect exists for the rare species whose whole point is to demonstrate a
/// BeeEffect (e.g. Marsh's hydration pulse): without it, a newly discovered species would
/// have to wait for an EffectAllele it has no way to have inherited, since nothing in the
/// starter set carries anything but EffectAllele.NONE. Left empty, the offspring's effect
/// chromosome comes from ordinary inheritance like every other trait.
public record BeeMutation(Identifier parentA, Identifier parentB, Identifier result, float baseChance, MutationCondition condition, Optional<EffectAllele> forcedEffect) {
    public boolean matchesParents(Identifier a, Identifier b) {
        return (parentA.equals(a) && parentB.equals(b)) || (parentA.equals(b) && parentB.equals(a));
    }
}
