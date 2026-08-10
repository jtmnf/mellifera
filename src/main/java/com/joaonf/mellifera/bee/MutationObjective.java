package com.joaonf.mellifera.bee;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

/// The breeding objective a housing has been pointed at: a species the player picked in the
/// GUI, turned into a per-mutation bias over the whole mutation table.
///
/// The point is that an objective names a *destination*, not a recipe. Asking for Imperial
/// while you are still sitting on Common should already help, because the useful thing to
/// breed right now is Noble -- so the bias has to know the shape of the tree between here
/// and there, not just the last step. That is what distanceTo() computes.
///
/// The bias has two halves, and both matter:
///
/// - `chanceMultiplier` scales a mutation's own chance. On-path mutations are boosted more
///   the closer their result sits to the objective; everything off-path is halved. That
///   penalty is deliberate -- it makes an objective a *choice* (focus one line, slow the
///   others) rather than a free global speed-up you would never turn off.
/// - `priority` reorders which mutation gets rolled first. MutationEngine returns on the
///   first mutation that both matches the parents and passes its roll, so when one pair has
///   two possible results (Common + Cultivated gives Noble *or* Diligent, both at 0.10) the
///   one earlier in the table always wins. Without reordering, an objective could boost a
///   mutation that never gets reached.
///
/// Instances are cached per objective species: the BFS walks the entire table and there is
/// no reason to redo it on every brood.
public final class MutationObjective {
    /// Multiplier by how many mutation steps the result still is from the objective --
    /// index 0 is the objective itself. Beyond the table's length the tail value applies,
    /// so a very deep line still gets a nudge all the way down.
    private static final float[] ON_PATH_BY_DISTANCE = {3.0F, 2.0F, 1.5F};
    private static final float ON_PATH_FAR = 1.25F;

    /// What a mutation that leads nowhere near the objective is scaled by.
    private static final float OFF_PATH = 0.5F;

    /// What steps() answers for a species no chain of mutations connects to the objective.
    public static final int UNREACHABLE = Integer.MAX_VALUE;

    private static final Map<Identifier, MutationObjective> CACHE = new ConcurrentHashMap<>();

    /// Which table the cached entries were built from. MutationEngine takes the mutation
    /// table as a parameter precisely so it is not welded to one global list, and a cache
    /// keyed on the objective alone would quietly hand back distances computed from a
    /// different tree -- so the cache is dropped whenever the table it was built from is
    /// not the one being asked about. In practice this fires once, on the first call.
    private static volatile @Nullable List<BeeMutation> cachedTable;

    /// Objectives already found to have nothing breeding into them, so canTarget() is a set
    /// lookup rather than a fresh walk of the table each time it is asked.
    private static final Set<Identifier> UNBREEDABLE = ConcurrentHashMap.newKeySet();

    private final Identifier objective;

    /// Steps from a species to the objective, following mutations forwards. Only holds the
    /// species that can actually reach it; anything absent is off-path.
    private final Map<Identifier, Integer> distanceToObjective;

    private MutationObjective(Identifier objective, Map<Identifier, Integer> distanceToObjective) {
        this.objective = objective;
        this.distanceToObjective = distanceToObjective;
    }

    /// Builds (or returns the cached) bias for `objective` over `mutations`.
    ///
    /// Returns null when the objective cannot be bred at all -- a hive-root species like
    /// Forest, or one of the loot-only bees (Ended, Steadfast, Valiant), none of which any
    /// mutation produces. Left alone, such an objective would apply the off-path penalty to
    /// the entire table with no upside anywhere, which is a trap rather than a choice: the
    /// player would be slowing their whole apiary down in exchange for nothing. Callers
    /// treat null as "no objective".
    public static @Nullable MutationObjective of(Identifier objective, List<BeeMutation> mutations) {
        if (cachedTable != mutations) {
            CACHE.clear();
            UNBREEDABLE.clear();
            cachedTable = mutations;
        }

        MutationObjective cached = CACHE.get(objective);
        if (cached != null) {
            return cached;
        }

        if (UNBREEDABLE.contains(objective)) {
            return null;
        }

        Map<Identifier, Integer> distances = buildDistances(objective, mutations);
        if (distances.size() <= 1) {
            // Only the objective itself, i.e. nothing breeds into it. Remembered so a hive
            // root left set as an objective does not re-walk the table on every brood.
            UNBREEDABLE.add(objective);
            return null;
        }

        MutationObjective built = new MutationObjective(objective, distances);
        CACHE.put(objective, built);
        return built;
    }

    /// Whether pointing a housing at this species would mean anything -- false for the hive
    /// roots and the loot-only bees, which no mutation produces.
    ///
    /// The GUI checks this before accepting a drop rather than letting the objective be set
    /// and quietly do nothing. An objective that is visibly set but inert is worse than one
    /// the tab refuses: the player would sit through generations blaming the odds.
    public static boolean canTarget(Identifier species, List<BeeMutation> mutations) {
        return of(species, mutations) != null;
    }

    /// Backwards breadth-first search from the objective.
    ///
    /// The edge that matters runs the opposite way to the obvious one: a species X is one
    /// step further out than species R if X is a *parent* of some mutation whose *result*
    /// is R. So the frontier expands from the objective back down the tree, and every
    /// species ends up labelled with the fewest mutations between it and the goal.
    ///
    /// A parent of a mutation producing the objective sits at distance 1 whether it is the
    /// rarer parent or the common one -- both are equally a step away, and there is no
    /// meaningful sense in which one is closer.
    private static Map<Identifier, Integer> buildDistances(Identifier objective, List<BeeMutation> mutations) {
        Map<Identifier, List<BeeMutation>> byResult = new HashMap<>();
        for (BeeMutation mutation : mutations) {
            byResult.computeIfAbsent(mutation.result(), key -> new ArrayList<>()).add(mutation);
        }

        Map<Identifier, Integer> distances = new HashMap<>();
        distances.put(objective, 0);

        Deque<Identifier> frontier = new ArrayDeque<>();
        frontier.add(objective);

        while (!frontier.isEmpty()) {
            Identifier current = frontier.poll();
            int next = distances.get(current) + 1;

            for (BeeMutation mutation : byResult.getOrDefault(current, List.of())) {
                for (Identifier parent : List.of(mutation.parentA(), mutation.parentB())) {
                    // BFS visits in non-decreasing distance order, so the first label a
                    // species gets is already its shortest -- never overwrite it.
                    if (distances.putIfAbsent(parent, next) == null) {
                        frontier.add(parent);
                    }
                }
            }
        }

        return Map.copyOf(distances);
    }

    public Identifier objective() {
        return objective;
    }

    /// How much this objective scales the given mutation's own chance.
    public float chanceMultiplier(BeeMutation mutation) {
        int distance = distanceTo(mutation.result());
        if (distance == UNREACHABLE) {
            return OFF_PATH;
        }

        return distance < ON_PATH_BY_DISTANCE.length ? ON_PATH_BY_DISTANCE[distance] : ON_PATH_FAR;
    }

    /// Sort key for the order mutations are rolled in: lower is rolled first. Ties keep the
    /// table's own order, because the sort MutationEngine runs is stable.
    public int priority(BeeMutation mutation) {
        return distanceTo(mutation.result());
    }

    /// Steps from `species` to the objective, or UNREACHABLE if no chain of mutations gets
    /// there. The objective itself is 0.
    public int steps(Identifier species) {
        return distanceToObjective.getOrDefault(species, UNREACHABLE);
    }

    /// How useful a bred bee is for reaching the objective: the better of its two species
    /// alleles, not just the expressed one.
    ///
    /// Counting the hidden allele matters and is not a technicality. A drone carrying the
    /// on-path species recessively passes it on exactly as readily as one expressing it --
    /// that is what a Recessive frame exists to surface -- so judging a bee by its active
    /// allele alone would throw away half the bees that are actually worth keeping.
    public int steps(BeeGenome genome) {
        return Math.min(steps(genome.species().active()), steps(genome.species().inactive()));
    }

    /// The mutation this pair should be aiming for: whichever of the crosses available to
    /// these two parents lands closest to the objective, or null if none of them is on the
    /// path at all. Drives the panel's status line -- the objective is otherwise invisible
    /// until several generations have passed.
    public @Nullable BeeMutation nextStep(Identifier parentA, Identifier parentB, List<BeeMutation> mutations) {
        BeeMutation best = null;
        int bestSteps = UNREACHABLE;

        for (BeeMutation mutation : mutations) {
            if (!mutation.matchesParents(parentA, parentB)) {
                continue;
            }

            int distance = steps(mutation.result());
            if (distance < bestSteps) {
                best = mutation;
                bestSteps = distance;
            }
        }

        return bestSteps == UNREACHABLE ? null : best;
    }

    /// Whether this pair has any cross at all, on the path or not -- what separates "these
    /// two lead somewhere else" from "these two do not breed into anything".
    public static boolean hasAnyCross(Identifier parentA, Identifier parentB, List<BeeMutation> mutations) {
        for (BeeMutation mutation : mutations) {
            if (mutation.matchesParents(parentA, parentB)) {
                return true;
            }
        }

        return false;
    }

    private int distanceTo(Identifier species) {
        return steps(species);
    }
}
