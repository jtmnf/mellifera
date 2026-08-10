package com.joaonf.mellifera.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.joaonf.mellifera.bee.BeeMutation;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.bee.EffectAllele;
import com.joaonf.mellifera.bee.MutationCondition;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.registries.DeferredHolder;

/// The complete mutation tree, transcribed from real Forestry (ForestryMC/ForestryMC,
/// mc-1.12, BeeDefinition.java, LGPL v3) for all 44 species -- pairs, chances and
/// biome/temperature/date gates included.
///
/// Forestry's chances are percentages (`registerMutation(parent, parent, 15)`); they are
/// carried over here as the 0..1 floats BeeMutation takes.
///
/// Two deliberate departures, both flagged at the entry that makes them:
///
/// - Forestry gates several branches on humidity as well as temperature. This engine has
///   no humidity axis, so those become temperature-only gates rather than inventing one.
/// - Boggy's forcedEffect (Hydration) is this mod's own addition -- real Forestry gives the
///   Boggy branch no effect at all.
///
/// Binnie's Extra Bees resource line hangs off the end of the same table; see
/// addResourceMutations().
public final class MelliferaBeeMutations {
    private static TagKey<Biome> biomeTag(String path) {
        return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", path));
    }

    private static final TagKey<Biome> IS_PLAINS = biomeTag("is_plains");
    private static final TagKey<Biome> IS_FOREST = biomeTag("is_forest");
    private static final TagKey<Biome> IS_NETHER = biomeTag("is_nether");

    // The six species Forestry finds ready-made in wild hives rather than breeding. Here
    // they are both: found in world-generated hives (see MelliferaBlocks' HIVE_* blocks) and
    // craftable as a fallback. Common and Cultivated cross any two of them.
    private static final List<DeferredHolder<BeeSpecies, BeeSpecies>> HIVE_ROOTS = List.of(
        MelliferaBeeSpecies.FOREST, MelliferaBeeSpecies.MEADOWS, MelliferaBeeSpecies.MARSHY,
        MelliferaBeeSpecies.MODEST, MelliferaBeeSpecies.TROPICAL, MelliferaBeeSpecies.WINTRY);

    /// The transcribed table, plus whatever `config/mellifera/custom_bees/` added to it.
    ///
    /// Not final, and read through all() rather than directly, because custom bees are loaded
    /// during registration and their crosses have to join this list before anything consults
    /// it. Replaced wholesale rather than mutated in place, so a reader mid-iteration is
    /// looking at a list that stays immutable underneath it.
    private static List<BeeMutation> all = buildAll();

    public static List<BeeMutation> all() {
        return all;
    }

    /// Appended once, at registration time -- see MelliferaBeeSpecies.registerCustom.
    public static void addCustom(List<BeeMutation> custom) {
        if (custom.isEmpty()) {
            return;
        }

        List<BeeMutation> merged = new ArrayList<>(all);
        merged.addAll(custom);
        all = List.copyOf(merged);
    }

    private MelliferaBeeMutations() {}

    private static List<BeeMutation> buildAll() {
        List<BeeMutation> mutations = new ArrayList<>();

        // -- Honey branch: the two bees every other line is built out of ----------------
        // Common: any two hive-root species crossed, 15% each.
        for (int i = 0; i < HIVE_ROOTS.size(); i++) {
            for (int j = i + 1; j < HIVE_ROOTS.size(); j++) {
                plain(mutations, HIVE_ROOTS.get(i), HIVE_ROOTS.get(j), MelliferaBeeSpecies.COMMON, 0.15F);
            }
        }

        // Cultivated: Common crossed with any hive-root species, 12% each.
        for (DeferredHolder<BeeSpecies, BeeSpecies> root : HIVE_ROOTS) {
            plain(mutations, MelliferaBeeSpecies.COMMON, root, MelliferaBeeSpecies.CULTIVATED, 0.12F);
        }

        // -- Noble branch --------------------------------------------------------------
        plain(mutations, MelliferaBeeSpecies.COMMON, MelliferaBeeSpecies.CULTIVATED, MelliferaBeeSpecies.NOBLE, 0.10F);
        plain(mutations, MelliferaBeeSpecies.NOBLE, MelliferaBeeSpecies.CULTIVATED, MelliferaBeeSpecies.MAJESTIC, 0.08F);
        plain(mutations, MelliferaBeeSpecies.NOBLE, MelliferaBeeSpecies.MAJESTIC, MelliferaBeeSpecies.IMPERIAL, 0.08F);

        // -- Industrious branch --------------------------------------------------------
        plain(mutations, MelliferaBeeSpecies.COMMON, MelliferaBeeSpecies.CULTIVATED, MelliferaBeeSpecies.DILIGENT, 0.10F);
        plain(mutations, MelliferaBeeSpecies.DILIGENT, MelliferaBeeSpecies.CULTIVATED, MelliferaBeeSpecies.UNWEARY, 0.08F);
        plain(mutations, MelliferaBeeSpecies.DILIGENT, MelliferaBeeSpecies.UNWEARY, MelliferaBeeSpecies.INDUSTRIOUS, 0.08F);

        // -- Heroic branch: Steadfast and Valiant have no mutation path upstream either;
        // they are chest loot (see the dungeon loot injection). Heroic is bred from them.
        gated(mutations, MelliferaBeeSpecies.STEADFAST, MelliferaBeeSpecies.VALIANT, MelliferaBeeSpecies.HEROIC,
            0.06F, new MutationCondition.RequiresBiomeTag(IS_FOREST));

        // -- Infernal branch: Nether-only ----------------------------------------------
        MutationCondition nether = new MutationCondition.RequiresBiomeTag(IS_NETHER);
        gated(mutations, MelliferaBeeSpecies.CULTIVATED, MelliferaBeeSpecies.MODEST, MelliferaBeeSpecies.SINISTER, 0.60F, nether);
        gated(mutations, MelliferaBeeSpecies.CULTIVATED, MelliferaBeeSpecies.TROPICAL, MelliferaBeeSpecies.SINISTER, 0.60F, nether);
        gated(mutations, MelliferaBeeSpecies.SINISTER, MelliferaBeeSpecies.CULTIVATED, MelliferaBeeSpecies.FIENDISH, 0.40F, nether);
        gated(mutations, MelliferaBeeSpecies.SINISTER, MelliferaBeeSpecies.MODEST, MelliferaBeeSpecies.FIENDISH, 0.40F, nether);
        gated(mutations, MelliferaBeeSpecies.SINISTER, MelliferaBeeSpecies.TROPICAL, MelliferaBeeSpecies.FIENDISH, 0.40F, nether);
        gated(mutations, MelliferaBeeSpecies.SINISTER, MelliferaBeeSpecies.FIENDISH, MelliferaBeeSpecies.DEMONIC, 0.25F, nether);

        // -- Austere branch: hot. Forestry gates these on HOT *or* HELLISH, so the band
        // runs from the low end of Modest's own range up through Nether heat.
        MutationCondition hot = new MutationCondition.RequiresClimate(25.0F, 70.0F);
        gated(mutations, MelliferaBeeSpecies.MODEST, MelliferaBeeSpecies.SINISTER, MelliferaBeeSpecies.FRUGAL, 0.16F, hot);
        gated(mutations, MelliferaBeeSpecies.MODEST, MelliferaBeeSpecies.FIENDISH, MelliferaBeeSpecies.FRUGAL, 0.10F, hot);
        gated(mutations, MelliferaBeeSpecies.MODEST, MelliferaBeeSpecies.FRUGAL, MelliferaBeeSpecies.AUSTERE, 0.08F, hot);

        // -- Tropical branch -----------------------------------------------------------
        plain(mutations, MelliferaBeeSpecies.AUSTERE, MelliferaBeeSpecies.TROPICAL, MelliferaBeeSpecies.EXOTIC, 0.12F);
        plain(mutations, MelliferaBeeSpecies.EXOTIC, MelliferaBeeSpecies.TROPICAL, MelliferaBeeSpecies.EDENIC, 0.08F);

        // -- End branch: Ended has no mutation path upstream either (it is End-structure
        // loot). Spectral and Phantasmal are bred from it, at brutal odds.
        plain(mutations, MelliferaBeeSpecies.HERMITIC, MelliferaBeeSpecies.ENDED, MelliferaBeeSpecies.SPECTRAL, 0.04F);
        plain(mutations, MelliferaBeeSpecies.SPECTRAL, MelliferaBeeSpecies.ENDED, MelliferaBeeSpecies.PHANTASMAL, 0.02F);

        // -- Frozen branch: icy/cold gated ---------------------------------------------
        MutationCondition icyCold = new MutationCondition.RequiresClimate(-20.0F, 10.0F);
        gated(mutations, MelliferaBeeSpecies.INDUSTRIOUS, MelliferaBeeSpecies.WINTRY, MelliferaBeeSpecies.ICY, 0.12F, icyCold);
        gated(mutations, MelliferaBeeSpecies.ICY, MelliferaBeeSpecies.WINTRY, MelliferaBeeSpecies.GLACIAL, 0.08F, icyCold);

        // -- Vengeful branch -----------------------------------------------------------
        plain(mutations, MelliferaBeeSpecies.MONASTIC, MelliferaBeeSpecies.DEMONIC, MelliferaBeeSpecies.VINDICTIVE, 0.04F);
        plain(mutations, MelliferaBeeSpecies.DEMONIC, MelliferaBeeSpecies.VINDICTIVE, MelliferaBeeSpecies.VENGEFUL, 0.08F);
        plain(mutations, MelliferaBeeSpecies.MONASTIC, MelliferaBeeSpecies.VINDICTIVE, MelliferaBeeSpecies.VENGEFUL, 0.08F);
        plain(mutations, MelliferaBeeSpecies.VENGEFUL, MelliferaBeeSpecies.VINDICTIVE, MelliferaBeeSpecies.AVENGING, 0.04F);

        // -- Monastic branch -----------------------------------------------------------
        plain(mutations, MelliferaBeeSpecies.MONASTIC, MelliferaBeeSpecies.AUSTERE, MelliferaBeeSpecies.SECLUDED, 0.12F);
        plain(mutations, MelliferaBeeSpecies.MONASTIC, MelliferaBeeSpecies.SECLUDED, MelliferaBeeSpecies.HERMITIC, 0.08F);

        // -- Agrarian branch: plains-tag gated -----------------------------------------
        MutationCondition plains = new MutationCondition.RequiresBiomeTag(IS_PLAINS);
        gated(mutations, MelliferaBeeSpecies.MEADOWS, MelliferaBeeSpecies.DILIGENT, MelliferaBeeSpecies.RURAL, 0.12F, plains);
        gated(mutations, MelliferaBeeSpecies.RURAL, MelliferaBeeSpecies.UNWEARY, MelliferaBeeSpecies.FARMERLY, 0.10F, plains);
        gated(mutations, MelliferaBeeSpecies.FARMERLY, MelliferaBeeSpecies.INDUSTRIOUS, MelliferaBeeSpecies.AGRARIAN, 0.06F, plains);

        // -- Boggy branch: Forestry gates these on WARM temperature (its damp humidity
        // requirement has no equivalent here). Boggy's Hydration is ours -- see javadoc.
        MutationCondition warm = new MutationCondition.RequiresClimate(15.0F, 35.0F);
        gated(mutations, MelliferaBeeSpecies.MARSHY, MelliferaBeeSpecies.NOBLE, MelliferaBeeSpecies.MIRY, 0.15F, warm);
        mutations.add(new BeeMutation(
            MelliferaBeeSpecies.MARSHY.getId(), MelliferaBeeSpecies.MIRY.getId(), MelliferaBeeSpecies.BOGGY.getId(),
            0.09F, warm, Optional.of(EffectAllele.HYDRATION)));

        // -- Festive branch: real-calendar gated, exactly as upstream --------------------
        gated(mutations, MelliferaBeeSpecies.MEADOWS, MelliferaBeeSpecies.FOREST, MelliferaBeeSpecies.LEPORINE,
            0.10F, new MutationCondition.RequiresDateRange(3, 29, 4, 15));
        gated(mutations, MelliferaBeeSpecies.WINTRY, MelliferaBeeSpecies.FOREST, MelliferaBeeSpecies.MERRY,
            0.10F, new MutationCondition.RequiresDateRange(12, 21, 12, 27));
        gated(mutations, MelliferaBeeSpecies.WINTRY, MelliferaBeeSpecies.MEADOWS, MelliferaBeeSpecies.TIPSY,
            0.10F, new MutationCondition.RequiresDateRange(12, 27, 1, 2));
        gated(mutations, MelliferaBeeSpecies.SINISTER, MelliferaBeeSpecies.COMMON, MelliferaBeeSpecies.TRICKY,
            0.10F, new MutationCondition.RequiresDateRange(10, 15, 11, 3));

        addResourceMutations(mutations);

        return List.copyOf(mutations);
    }

    /// Binnie's Extra Bees resource line (ForestryMC/Binnie, master-MC1.12,
    /// ExtraBeeDefinition's registerMutations(), LGPL v3). Upstream's chances are the same
    /// percentages Forestry uses, so they carry over the same way.
    ///
    /// The shape upstream gives this line is worth stating, because it is what makes it a
    /// *line* rather than a pile of ore bees: Mineral is a hard chokepoint -- every metal
    /// and every gem descends from it -- and Lapis is a second one in front of the gems.
    /// Nothing here shortcuts either.
    ///
    /// Two entries deviate, both because they name an Extra Bees species this mod has no
    /// business adding, and both flagged at the entry that makes them.
    private static void addResourceMutations(List<BeeMutation> mutations) {
        // -- Rocky branch --------------------------------------------------------------
        // DEVIATION. Upstream's Rocky bee has no mutation at all: Extra Bees generates rock
        // hives in the world and you find it. This mod's worldgen has six Forestry hives and
        // adding a seventh is a bigger change than this one, so Rocky is bred instead --
        // Diligent (the industrious digger) crossed with Modest (the desert/stone bee), gated
        // on exposed ore in the apiary's territory so it still wants a mine rather than a
        // meadow. Chance matches the branch's other early steps.
        gated(mutations, MelliferaBeeSpecies.DILIGENT, MelliferaBeeSpecies.MODEST, MelliferaBeeSpecies.ROCK,
            0.10F, new MutationCondition.RequiresBlockNearby(MelliferaBlockTags.ORES));

        plain(mutations, MelliferaBeeSpecies.DILIGENT, MelliferaBeeSpecies.ROCK, MelliferaBeeSpecies.STONE, 0.12F);
        plain(mutations, MelliferaBeeSpecies.UNWEARY, MelliferaBeeSpecies.STONE, MelliferaBeeSpecies.GRANITE, 0.10F);
        plain(mutations, MelliferaBeeSpecies.INDUSTRIOUS, MelliferaBeeSpecies.GRANITE, MelliferaBeeSpecies.MINERAL, 0.06F);

        // ADDITION. Coal exists in neither upstream, so nothing constrains where it hangs --
        // which makes it worth saying why it hangs *here*.
        //
        // Not off Mineral, where every other resource bee hangs. That chokepoint is upstream's
        // deliberate gate in front of the metals and the gems, and it costs Industrious crossed
        // with Granite at 6% to pass. Putting the cheapest ore in the game behind the most
        // expensive step in the branch would invert the whole line: coal would arrive after
        // iron. So it comes straight off Rock instead, at the same tier as Stone, which is
        // early enough for the fuel to be worth having when it lands.
        //
        // Forest for the other parent because coal is a buried forest -- the same kind of
        // literalism the Lapis cross already runs on. Ungated: Rock's own mutation already
        // made the player find exposed ore, and gating the child on it again would only mean
        // the apiary that bred the parent can breed the child.
        plain(mutations, MelliferaBeeSpecies.FOREST, MelliferaBeeSpecies.ROCK, MelliferaBeeSpecies.COAL, 0.12F);

        // -- Metallic branch: each metal is Mineral crossed with two specific hive roots,
        // so which ore bee you get first depends on which hives you happened to find.
        plain(mutations, MelliferaBeeSpecies.WINTRY, MelliferaBeeSpecies.MINERAL, MelliferaBeeSpecies.COPPER, 0.05F);
        plain(mutations, MelliferaBeeSpecies.MODEST, MelliferaBeeSpecies.MINERAL, MelliferaBeeSpecies.COPPER, 0.05F);
        plain(mutations, MelliferaBeeSpecies.MEADOWS, MelliferaBeeSpecies.MINERAL, MelliferaBeeSpecies.IRON, 0.05F);
        plain(mutations, MelliferaBeeSpecies.FOREST, MelliferaBeeSpecies.MINERAL, MelliferaBeeSpecies.IRON, 0.05F);

        // -- Precious branch: Majestic crossed with a base metal. Upstream also lists the
        // Nickel and Tungstate crossings; those two species aren't here (no Vanilla output),
        // so their two entries go with them and Gold keeps the Iron and Copper routes.
        plain(mutations, MelliferaBeeSpecies.MAJESTIC, MelliferaBeeSpecies.IRON, MelliferaBeeSpecies.GOLD, 0.02F);
        plain(mutations, MelliferaBeeSpecies.MAJESTIC, MelliferaBeeSpecies.COPPER, MelliferaBeeSpecies.GOLD, 0.02F);

        // -- Mineral branch ------------------------------------------------------------
        // DEVIATION. Upstream breeds Lapis from its Water bee, the root of an Aquatic branch
        // this mod doesn't have. Marshy stands in as the wettest species that does exist, and
        // the water requirement upstream expressed through that parent is kept literally
        // instead: the apiary has to have water in its territory. Chance is upstream's.
        gated(mutations, MelliferaBeeSpecies.MARSHY, MelliferaBeeSpecies.MINERAL, MelliferaBeeSpecies.LAPIS,
            0.05F, new MutationCondition.RequiresFluidNearby(FluidTags.WATER));

        // -- Gemstone branch: both gems come off Lapis, exactly as upstream.
        plain(mutations, MelliferaBeeSpecies.FOREST, MelliferaBeeSpecies.LAPIS, MelliferaBeeSpecies.EMERALD, 0.05F);
        plain(mutations, MelliferaBeeSpecies.CULTIVATED, MelliferaBeeSpecies.LAPIS, MelliferaBeeSpecies.DIAMOND, 0.05F);

        // -- Energetic branch: independent of the rock line, off Valiant instead.
        plain(mutations, MelliferaBeeSpecies.VALIANT, MelliferaBeeSpecies.CULTIVATED, MelliferaBeeSpecies.EXCITED, 0.10F);
        plain(mutations, MelliferaBeeSpecies.DILIGENT, MelliferaBeeSpecies.EXCITED, MelliferaBeeSpecies.ENERGETIC, 0.08F);
        plain(mutations, MelliferaBeeSpecies.EXCITED, MelliferaBeeSpecies.ENERGETIC, MelliferaBeeSpecies.ECSTATIC, 0.08F);
    }

    private static void plain(
        List<BeeMutation> mutations,
        DeferredHolder<BeeSpecies, BeeSpecies> parentA,
        DeferredHolder<BeeSpecies, BeeSpecies> parentB,
        DeferredHolder<BeeSpecies, BeeSpecies> result,
        float chance
    ) {
        gated(mutations, parentA, parentB, result, chance, new MutationCondition.None());
    }

    private static void gated(
        List<BeeMutation> mutations,
        DeferredHolder<BeeSpecies, BeeSpecies> parentA,
        DeferredHolder<BeeSpecies, BeeSpecies> parentB,
        DeferredHolder<BeeSpecies, BeeSpecies> result,
        float chance,
        MutationCondition condition
    ) {
        mutations.add(new BeeMutation(parentA.getId(), parentB.getId(), result.getId(), chance, condition, Optional.empty()));
    }
}
