package com.joaonf.mellifera.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.BeeBranch;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.config.CustomBees;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/// Which branch each species belongs to -- the twenty groupings the species file already
/// declares in its `// -- X branch --` comments, promoted to something the Apiarist Database
/// can navigate by.
///
/// A table of its own rather than a component on BeeSpecies. The branch is not part of what a
/// bee *is* -- no allele carries it, no mutation reads it, and a species behaves identically
/// whichever group it is filed under -- so putting it on the record would widen every one of
/// the 57 constructor calls with a field the engine never looks at. The cost of keeping it
/// out there is that nothing forces a new species to be filed, which is what [#validate] is
/// for: it fails at startup rather than letting a bee quietly vanish from the guide.
///
/// Declaration order is display order, and it starts at Honey on purpose: those are the bees
/// found in wild hives, so the first thing the list shows is the line a player actually
/// begins with.
public final class MelliferaBeeBranches {
    // -- Forestry -----------------------------------------------------------------------
    public static final BeeBranch HONEY = branch("honey",
        MelliferaBeeSpecies.FOREST, MelliferaBeeSpecies.MEADOWS,
        MelliferaBeeSpecies.COMMON, MelliferaBeeSpecies.CULTIVATED);

    public static final BeeBranch NOBLE = branch("noble",
        MelliferaBeeSpecies.NOBLE, MelliferaBeeSpecies.MAJESTIC, MelliferaBeeSpecies.IMPERIAL);

    public static final BeeBranch INDUSTRIOUS = branch("industrious",
        MelliferaBeeSpecies.DILIGENT, MelliferaBeeSpecies.UNWEARY, MelliferaBeeSpecies.INDUSTRIOUS);

    public static final BeeBranch HEROIC = branch("heroic",
        MelliferaBeeSpecies.STEADFAST, MelliferaBeeSpecies.VALIANT, MelliferaBeeSpecies.HEROIC);

    public static final BeeBranch INFERNAL = branch("infernal",
        MelliferaBeeSpecies.SINISTER, MelliferaBeeSpecies.FIENDISH, MelliferaBeeSpecies.DEMONIC);

    public static final BeeBranch AUSTERE = branch("austere",
        MelliferaBeeSpecies.MODEST, MelliferaBeeSpecies.FRUGAL, MelliferaBeeSpecies.AUSTERE);

    public static final BeeBranch TROPICAL = branch("tropical",
        MelliferaBeeSpecies.TROPICAL, MelliferaBeeSpecies.EXOTIC, MelliferaBeeSpecies.EDENIC);

    public static final BeeBranch END = branch("end",
        MelliferaBeeSpecies.ENDED, MelliferaBeeSpecies.SPECTRAL, MelliferaBeeSpecies.PHANTASMAL);

    public static final BeeBranch FROZEN = branch("frozen",
        MelliferaBeeSpecies.WINTRY, MelliferaBeeSpecies.ICY, MelliferaBeeSpecies.GLACIAL);

    public static final BeeBranch VENGEFUL = branch("vengeful",
        MelliferaBeeSpecies.VINDICTIVE, MelliferaBeeSpecies.VENGEFUL, MelliferaBeeSpecies.AVENGING);

    public static final BeeBranch FESTIVE = branch("festive",
        MelliferaBeeSpecies.LEPORINE, MelliferaBeeSpecies.MERRY,
        MelliferaBeeSpecies.TIPSY, MelliferaBeeSpecies.TRICKY);

    public static final BeeBranch AGRARIAN = branch("agrarian",
        MelliferaBeeSpecies.RURAL, MelliferaBeeSpecies.FARMERLY, MelliferaBeeSpecies.AGRARIAN);

    public static final BeeBranch BOGGY = branch("boggy",
        MelliferaBeeSpecies.MARSHY, MelliferaBeeSpecies.MIRY, MelliferaBeeSpecies.BOGGY);

    public static final BeeBranch MONASTIC = branch("monastic",
        MelliferaBeeSpecies.MONASTIC, MelliferaBeeSpecies.SECLUDED, MelliferaBeeSpecies.HERMITIC);

    // -- Extra Bees ---------------------------------------------------------------------
    // Binnie's own branch names and memberships, which is why Mineral holds only Lapis while
    // the species called Mineral sits in Rocky: upstream files them that way and renaming
    // them here would make this table disagree with every Extra Bees wiki page.
    /// Coal last, after the Rock -> Stone -> Granite -> Mineral ladder rather than inside it:
    /// it is bred at Stone's tier but leads nowhere, and threading a dead end through the
    /// middle of a chain would make the group read as four steps when it is really three
    /// steps and a turning.
    public static final BeeBranch ROCKY = branch("rocky",
        MelliferaBeeSpecies.ROCK, MelliferaBeeSpecies.STONE,
        MelliferaBeeSpecies.GRANITE, MelliferaBeeSpecies.MINERAL, MelliferaBeeSpecies.COAL);

    public static final BeeBranch METALLIC = branch("metallic",
        MelliferaBeeSpecies.COPPER, MelliferaBeeSpecies.IRON);

    public static final BeeBranch PRECIOUS = branch("precious",
        MelliferaBeeSpecies.GOLD);

    public static final BeeBranch MINERAL = branch("mineral",
        MelliferaBeeSpecies.LAPIS);

    public static final BeeBranch GEMSTONE = branch("gemstone",
        MelliferaBeeSpecies.EMERALD, MelliferaBeeSpecies.DIAMOND);

    public static final BeeBranch ENERGETIC = branch("energetic",
        MelliferaBeeSpecies.EXCITED, MelliferaBeeSpecies.ENERGETIC, MelliferaBeeSpecies.ECSTATIC);

    /// Where a species that names no branch, or an unknown one, ends up.
    private static final String CUSTOM_KEY = "branch.mellifera.custom";

    private static final List<BeeBranch> BUILT_IN = List.of(
        HONEY, NOBLE, INDUSTRIOUS, HEROIC, INFERNAL, AUSTERE, TROPICAL, END, FROZEN, VENGEFUL,
        FESTIVE, AGRARIAN, BOGGY, MONASTIC,
        ROCKY, METALLIC, PRECIOUS, MINERAL, GEMSTONE, ENERGETIC);

    /// Grown at common setup by whatever the built-in table did not account for -- see
    /// [#adopt]. Replaced wholesale rather than mutated, so a reader is never iterating a
    /// list that is being appended to.
    private static List<BeeBranch> all = BUILT_IN;

    private static Map<Identifier, BeeBranch> bySpecies = index(BUILT_IN);

    public static List<BeeBranch> all() {
        return all;
    }

    private MelliferaBeeBranches() {}

    /// Ids rather than holders: a DeferredHolder knows its id from the moment it is created,
    /// so this table is complete long before the registry itself is populated -- which is
    /// what lets it be a set of static finals like everything else in this package.
    @SafeVarargs
    private static BeeBranch branch(String name, DeferredHolder<BeeSpecies, BeeSpecies>... members) {
        List<Identifier> species = new ArrayList<>(members.length);
        for (DeferredHolder<BeeSpecies, BeeSpecies> member : members) {
            species.add(member.getId());
        }

        return new BeeBranch("branch.mellifera." + name, species);
    }

    private static Map<Identifier, BeeBranch> index(List<BeeBranch> branches) {
        Map<Identifier, BeeBranch> indexed = new HashMap<>();
        for (BeeBranch branch : branches) {
            for (Identifier id : branch.species()) {
                BeeBranch previous = indexed.put(id, branch);
                if (previous != null) {
                    throw new IllegalStateException(id + " is filed under two branches: "
                        + previous.translationKey() + " and " + branch.translationKey());
                }
            }
        }

        return Map.copyOf(indexed);
    }

    public static @Nullable BeeBranch of(Identifier species) {
        return bySpecies.get(species);
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(MelliferaBeeBranches::validate);
    }

    /// Files every registered species into a branch, inventing the ones the table does not
    /// name.
    ///
    /// This used to throw for an unfiled species, on the grounds that a bee the Apiarist
    /// Database never lists is worse than a startup that names it. That was right while every
    /// species was declared in Java a few lines from this table -- an unfiled bee could only
    /// be an omission somebody could fix. It stopped being right the moment a player could add
    /// a bee from `config/mellifera/custom_bees/`: the same check would turn "you wrote a
    /// custom bee" into "your game does not start", which is an absurd thing to do to someone
    /// for using a documented feature.
    ///
    /// So the unfiled are gathered into their own branch instead. A custom bee may name any
    /// branch it likes -- including a built-in one, which puts it in among the Forestry bees --
    /// and anything that names none, or names one that does not exist, lands here.
    ///
    /// Still at common setup, because that is the first point the registry is populated, and
    /// custom bees are registered before it.
    private static void validate(FMLCommonSetupEvent event) {
        List<Identifier> unfiled = new ArrayList<>();
        for (Identifier id : MelliferaBeeSpecies.REGISTRY.keySet()) {
            if (!bySpecies.containsKey(id)) {
                unfiled.add(id);
            }
        }

        // The other direction is a typo rather than an omission -- a branch naming a species
        // that was renamed or removed -- and it costs one pass to say so by name.
        for (Map.Entry<Identifier, BeeBranch> entry : bySpecies.entrySet()) {
            if (!MelliferaBeeSpecies.REGISTRY.containsKey(entry.getKey())) {
                Mellifera.LOGGER.warn("branch {} names an unregistered species {}",
                    entry.getValue().translationKey(), entry.getKey());
            }
        }

        if (!unfiled.isEmpty()) {
            adopt(unfiled);
        }
    }

    /// Files the strays: into the branch each one asked for when that branch exists, and into
    /// a branch of their own otherwise -- at the end of the list, where a group nobody planned
    /// belongs.
    ///
    /// Joining a built-in branch is worth supporting rather than herding every custom bee into
    /// one bucket. A bee whose whole idea is "another step after Imperial" belongs among the
    /// Noble bees in the guide, and the alternative is a player being told their bee is
    /// "Custom" no matter what it is.
    private static void adopt(List<Identifier> unfiled) {
        Map<String, List<Identifier>> requested = new LinkedHashMap<>();
        for (Identifier id : unfiled) {
            requested.computeIfAbsent(CustomBees.branchKeyOf(id).orElse(CUSTOM_KEY), key -> new ArrayList<>()).add(id);
        }

        List<BeeBranch> grown = new ArrayList<>();
        List<Identifier> leftovers = new ArrayList<>(requested.getOrDefault(CUSTOM_KEY, List.of()));

        for (BeeBranch branch : all) {
            List<Identifier> joining = requested.get(branch.translationKey());
            if (joining == null) {
                grown.add(branch);
                continue;
            }

            List<Identifier> members = new ArrayList<>(branch.species());
            members.addAll(joining);
            grown.add(new BeeBranch(branch.translationKey(), members));
        }

        // Anything that named a branch nobody has heard of joins the leftovers rather than
        // vanishing -- a typo in one field should cost the bee its grouping, not its listing.
        requested.forEach((key, ids) -> {
            if (!key.equals(CUSTOM_KEY) && grown.stream().noneMatch(branch -> branch.translationKey().equals(key))) {
                Mellifera.LOGGER.warn("{} asked for branch {}, which does not exist", ids, key);
                leftovers.addAll(ids);
            }
        });

        if (!leftovers.isEmpty()) {
            grown.add(new BeeBranch(CUSTOM_KEY, leftovers));
        }

        Mellifera.LOGGER.info("filed {} unbranched species: {} under {}", unfiled.size(), leftovers.size(), CUSTOM_KEY);
        all = List.copyOf(grown);
        bySpecies = index(all);
    }
}
