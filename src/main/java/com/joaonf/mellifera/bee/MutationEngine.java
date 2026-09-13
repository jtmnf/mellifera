package com.joaonf.mellifera.bee;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/// Rolls whether a bred offspring's species chromosome mutates into a new species. Only
/// ever touches the species chromosome -- every other trait already came out of
/// BeeGenome.inherited normally. Takes the mutation table as a parameter rather than
/// reaching for a global list, so this engine has no dependency on which species/mutations
/// are actually registered.
public final class MutationEngine {
    private MutationEngine() {}

    public static BeeGenome tryMutate(ServerLevel level, BlockPos apiaryPos, RandomSource random, BeeGenome childGenome, List<BeeMutation> mutations) {
        return tryMutate(level, apiaryPos, random, childGenome, mutations, 1.0F, null);
    }

    /// `chanceMultiplier` is what a Mutagenic frame supplies. Clamped at 1.0 so stacking
    /// frames shortens the grind without ever making a mutation certain.
    public static BeeGenome tryMutate(ServerLevel level, BlockPos apiaryPos, RandomSource random, BeeGenome childGenome, List<BeeMutation> mutations, float chanceMultiplier) {
        return tryMutate(level, apiaryPos, random, childGenome, mutations, chanceMultiplier, null);
    }

    /// `objective` is the species the housing has been pointed at, or null for none. It
    /// stacks on top of the frame multiplier: a Mutagenic frame and an objective aimed at
    /// the same branch multiply together, still under the same 1.0 cap.
    public static BeeGenome tryMutate(ServerLevel level, BlockPos apiaryPos, RandomSource random, BeeGenome childGenome,
                                      List<BeeMutation> mutations, float chanceMultiplier, @Nullable MutationObjective objective) {
        Identifier speciesA = childGenome.species().active();
        Identifier speciesB = childGenome.species().inactive();
        int territoryRadius = childGenome.territory().active().radius();

        for (BeeMutation mutation : candidates(mutations, speciesA, speciesB, objective)) {
            // Redundant once candidates() has pre-filtered for an objective, but candidates()
            // hands back the whole table untouched when there is none.
            if (!mutation.matchesParents(speciesA, speciesB)) {
                continue;
            }

            if (!mutation.condition().matches(level, apiaryPos, territoryRadius)) {
                continue;
            }

            float chance = mutation.baseChance() * chanceMultiplier;
            if (objective != null) {
                chance *= objective.chanceMultiplier(mutation);
            }

            if (random.nextFloat() >= Math.min(1.0F, chance)) {
                continue;
            }

            // One of the two ordinary parent species stays on the chromosome, recessive,
            // alongside the newly discovered one -- matches how a real mutation's hidden
            // allele can still carry an ancestor species forward.
            Identifier survivingParent = random.nextBoolean() ? speciesA : speciesB;
            Chromosome<EffectAllele> effect = mutation.forcedEffect()
                .map(Chromosome::pure)
                .orElse(childGenome.effect());

            // Only the species and (when the cross forces one) the effect change. Everything else
            // the child inherited stands, the three foraging toggles included: a mutation is a new
            // species arriving on a bee that was already bred, not a fresh wild-type individual.
            return childGenome
                .withSpecies(new Chromosome<>(mutation.result(), survivingParent))
                .withEffect(effect);
        }

        return childGenome;
    }

    /// The mutations this pairing could produce, in the order they get rolled.
    ///
    /// Order is not cosmetic: this loop returns on the first mutation that passes, so when
    /// one pair has two possible results (Common + Cultivated gives Noble *or* Diligent,
    /// both at 0.10) whichever comes first is strictly favoured. Without an objective that
    /// is the mutation table's own order, exactly as before -- the list is returned
    /// untouched so nothing about existing breeding changes. With one, the candidates are
    /// re-sorted so the mutation closest to the objective is rolled first, which is what
    /// stops the bias from boosting a mutation that a sibling result would have shadowed.
    ///
    /// The sort is stable, so mutations the objective ranks equally keep the table's order
    /// between them.
    private static List<BeeMutation> candidates(List<BeeMutation> mutations, Identifier speciesA, Identifier speciesB,
                                                @Nullable MutationObjective objective) {
        if (objective == null) {
            return mutations;
        }

        List<BeeMutation> matching = new ArrayList<>();
        for (BeeMutation mutation : mutations) {
            if (mutation.matchesParents(speciesA, speciesB)) {
                matching.add(mutation);
            }
        }

        matching.sort(Comparator.comparingInt(objective::priority));
        return matching;
    }
}
