package com.joaonf.mellifera.bee;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.Identifier;

/// How far into the breeding tree a species sits, so anything that lists bees can list them
/// in the order a player actually meets them instead of in registration order.
///
/// The measure is the earliest tier a species can be obtained at, and the subtlety is that
/// it depends on *both* parents: you cannot breed Imperial the moment you have Noble, you
/// need Majestic as well, so Imperial sits one step past the later of the two. Hence max()
/// over the pair rather than min() -- taking the nearer parent would rank a bee by the
/// easiest half of its recipe and put branch tops alongside the bees they are bred from.
///
/// Tier 0 is everything no mutation produces at all: the six hive roots found in the world,
/// and the loot-only species (Steadfast, Valiant, Ended). Those are exactly the bees a
/// player starts with, which is what makes them the right floor.
public final class BeeProgression {
    /// Species reached through no mutation this table knows about. Sorted last rather than
    /// first: a species that is unreachable is not an early one, it is one the table cannot
    /// explain, and burying it beats claiming it is a starter bee.
    private static final int UNREACHABLE = Integer.MAX_VALUE;

    /// Enough passes for the value to settle. Each pass can only lower a tier, and a tier
    /// can never exceed the number of species, so this cannot terminate early by accident.
    private static final int MAX_PASSES = 64;

    private BeeProgression() {}

    /// `species` ordered from the bees a player begins with to the ones at the ends of the
    /// branches. Ties keep the order they came in, which is the registration order --
    /// species are declared branch by branch, so a tier stays grouped by branch inside it.
    public static List<Identifier> sorted(Iterable<Identifier> species, List<BeeMutation> mutations) {
        Map<Identifier, Integer> tiers = tiers(mutations);

        List<Identifier> ordered = new ArrayList<>();
        species.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(id -> tiers.getOrDefault(id, UNREACHABLE)));
        return ordered;
    }

    /// Breeding tier per species: 0 for anything nothing breeds into, otherwise one past the
    /// later of the two parents of whichever cross reaches it soonest.
    ///
    /// Relaxed to a fixed point rather than walked once, because a single pass over the
    /// table would depend on the order the entries happen to be written in: a mutation whose
    /// parents are only tiered later in the same pass would be scored against their starting
    /// values. Repeating until nothing changes removes that dependency entirely.
    public static Map<Identifier, Integer> tiers(List<BeeMutation> mutations) {
        Set<Identifier> bred = new HashSet<>();
        for (BeeMutation mutation : mutations) {
            bred.add(mutation.result());
        }

        // Seed: a species some cross produces starts unreachable and gets relaxed down to
        // its real tier below; everything else is a bee the player already has access to.
        Map<Identifier, Integer> tiers = new HashMap<>();
        for (BeeMutation mutation : mutations) {
            for (Identifier id : List.of(mutation.parentA(), mutation.parentB(), mutation.result())) {
                tiers.put(id, bred.contains(id) ? UNREACHABLE : 0);
            }
        }

        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;

            for (BeeMutation mutation : mutations) {
                int a = tiers.getOrDefault(mutation.parentA(), UNREACHABLE);
                int b = tiers.getOrDefault(mutation.parentB(), UNREACHABLE);
                if (a == UNREACHABLE || b == UNREACHABLE) {
                    continue;
                }

                int candidate = Math.max(a, b) + 1;
                if (candidate < tiers.getOrDefault(mutation.result(), UNREACHABLE)) {
                    tiers.put(mutation.result(), candidate);
                    changed = true;
                }
            }

            if (!changed) {
                break;
            }
        }

        return Map.copyOf(tiers);
    }
}
