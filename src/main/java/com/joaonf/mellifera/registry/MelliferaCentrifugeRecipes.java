package com.joaonf.mellifera.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.CentrifugeRecipe;
import com.joaonf.mellifera.bee.ItemProduct;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/// What each comb yields in a Centrifuge. The 14 Forestry combs are transcribed from real
/// Forestry's `RecipeManagers.centrifugeManager.addRecipe(...)` calls (ForestryMC/
/// ForestryMC, mc-1.12, ModuleApiculture.java, LGPL v3) -- item sets and per-output chances
/// included -- and the eight resource combs from Binnie's Extra Bees (ForestryMC/Binnie,
/// master-MC1.12, EnumHoneyComb, LGPL v3), rebalanced for Vanilla items as noted inline.
///
/// Held as a Java table rather than datapack JSON, matching how this mod already declares
/// its other bee data (see MelliferaBeeMutations) and its non-datapack behaviours (see
/// fluid/HotSpringEffects). Outputs are Suppliers because this table is static data built
/// while MelliferaItems is still registering.
///
/// One faithful oddity kept on purpose: an Irradiated comb yields *nothing*. That is
/// genuinely what upstream does -- `addRecipe(20, IRRADIATED, ImmutableMap.of())` with an
/// empty output map -- and it is the joke of the Vengeful branch, not an omission here.
public final class MelliferaCentrifugeRecipes {
    /// Eight seconds a comb, which is the *unpowered* figure: these are ticks of progress, and
    /// a powered machine earns eight of them a tick (see MachineEnergy.POWERED_SPEED), so on FE
    /// this lands back on Forestry's own one-second-per-comb -- every upstream recipe is
    /// `addRecipe(20, ...)`, ticks not seconds.
    ///
    /// So the multiplier is what buys Forestry's speed rather than something faster than it,
    /// and the hand-run machine is slow enough that wiring power up is worth doing.
    private static final int STANDARD_TICKS = 160;

    /// Forestry spins silky propolis down in a quarter of the usual time.
    private static final int PROPOLIS_TICKS = STANDARD_TICKS / 4;

    private static final Map<Identifier, CentrifugeRecipe> BY_COMB = build();
    private static final Map<Item, CentrifugeRecipe> BY_ITEM = buildItems();

    /// Rows for combs invented in `config/mellifera/custom_bees/`, deliberately not part of
    /// BY_COMB -- see addCustom.
    private static Map<Identifier, CentrifugeRecipe> customByComb = Map.of();

    /// What the machine and the JEI page actually use: these start as the tables above and
    /// are replaced once at common setup with the same tables at the configured chances (see
    /// MelliferaOutputConfig). Kept apart from BY_COMB/BY_ITEM so the defaults survive --
    /// the config reads them to know what to write into a fresh file, and would otherwise be
    /// writing back its own previous output.
    private static Map<Identifier, CentrifugeRecipe> effectiveByComb = BY_COMB;
    private static Map<Item, CentrifugeRecipe> effectiveByItem = BY_ITEM;

    private MelliferaCentrifugeRecipes() {}

    /// Every comb recipe, for anything that wants to display the table rather than run it
    /// (see the JEI plugin).
    public static Map<Identifier, CentrifugeRecipe> allCombRecipes() {
        return effectiveByComb;
    }

    public static Map<Item, CentrifugeRecipe> allItemRecipes() {
        return effectiveByItem;
    }

    /// The transcribed tables, before any config is applied. Combs invented in a JSON file are
    /// not in here, which is what keeps them out of `centrifuge.toml` -- see addCustom.
    public static Map<Identifier, CentrifugeRecipe> defaultCombRecipes() {
        return BY_COMB;
    }

    /// Rows for combs invented in `config/mellifera/custom_bees/`, added at registration time
    /// by MelliferaCombTypes -- which is the only thing that knows which of them actually got
    /// registered.
    ///
    /// Held beside BY_COMB rather than merged into it, because the two answer to different
    /// files. `centrifuge.toml` is generated from BY_COMB and never prunes what it has already
    /// written, so a custom row in there would outlive the JSON that asked for it: delete the
    /// bee and the comb is gone from the game while its chances sit in a config file forever,
    /// naming an item nothing produces. A custom comb's chances belong in the one file that
    /// defines the comb, so deleting that file deletes all of it.
    ///
    /// The effective table is repointed here because at this point in startup it is still the
    /// defaults, and the config layer that would otherwise rebuild it does not run until common
    /// setup.
    public static void addCustom(Map<Identifier, CentrifugeRecipe> custom) {
        customByComb = Map.copyOf(custom);
        effectiveByComb = withCustom(BY_COMB);
    }

    public static Map<Item, CentrifugeRecipe> defaultItemRecipes() {
        return BY_ITEM;
    }

    /// Custom combs survive every rebuild of the effective table, since none of the callers'
    /// tables are built from a file that knows about them.
    ///
    /// The caller still wins on a shared key, which matters for exactly one caller: a client
    /// applying a server's chances (see MelliferaOutputSync) builds its table from
    /// allCombRecipes and so does carry custom rows, and on those the server is the authority
    /// -- the same rule as everywhere else in that class.
    public static void applyOverrides(Map<Identifier, CentrifugeRecipe> byComb, Map<Item, CentrifugeRecipe> byItem) {
        effectiveByComb = withCustom(byComb);
        effectiveByItem = byItem;
    }

    private static Map<Identifier, CentrifugeRecipe> withCustom(Map<Identifier, CentrifugeRecipe> base) {
        if (customByComb.isEmpty()) {
            return base;
        }

        Map<Identifier, CentrifugeRecipe> merged = new LinkedHashMap<>(customByComb);
        merged.putAll(base);
        return Map.copyOf(merged);
    }

    public static @Nullable CentrifugeRecipe forComb(@Nullable Identifier combType) {
        return combType == null ? null : effectiveByComb.get(combType);
    }

    /// Inputs that aren't combs at all. Silky propolis is the only source of silk wisps,
    /// and it is itself only obtainable by centrifuging a Silky comb -- so the machine has
    /// to accept its own output back as an input or that chain simply dead-ends.
    public static @Nullable CentrifugeRecipe forItem(Item item) {
        return effectiveByItem.get(item);
    }

    private static Map<Item, CentrifugeRecipe> buildItems() {
        return Map.of(
            MelliferaItems.SILKY_PROPOLIS.get(), new CentrifugeRecipe(PROPOLIS_TICKS, List.of(
                out(MelliferaItems.SILK_WISP, 0.60F),
                out(MelliferaItems.PROPOLIS, 0.10F))));
    }

    private static Map<Identifier, CentrifugeRecipe> build() {
        return Map.ofEntries(
            recipe(MelliferaCombTypes.HONEY,
                out(MelliferaItems.BEESWAX, 1.00F),
                out(MelliferaItems.HONEY_DROP, 0.90F)),

            recipe(MelliferaCombTypes.COCOA,
                out(MelliferaItems.BEESWAX, 1.00F),
                vanilla(Items.COCOA_BEANS, 1, 0.50F)),

            recipe(MelliferaCombTypes.SIMMERING,
                out(MelliferaItems.REFRACTORY_WAX, 1.00F),
                out(MelliferaItems.PHOSPHOR, 2, 0.70F)),

            recipe(MelliferaCombTypes.STRINGY,
                out(MelliferaItems.PROPOLIS, 1.00F),
                out(MelliferaItems.HONEY_DROP, 0.40F)),

            recipe(MelliferaCombTypes.DRIPPING,
                out(MelliferaItems.HONEYDEW, 1.00F),
                out(MelliferaItems.HONEY_DROP, 0.40F)),

            recipe(MelliferaCombTypes.FROZEN,
                out(MelliferaItems.BEESWAX, 0.80F),
                out(MelliferaItems.HONEY_DROP, 0.70F),
                vanilla(Items.SNOWBALL, 1, 0.40F),
                out(MelliferaItems.POLLEN, 0.20F)),

            recipe(MelliferaCombTypes.SILKY,
                out(MelliferaItems.HONEY_DROP, 1.00F),
                out(MelliferaItems.SILKY_PROPOLIS, 0.80F)),

            recipe(MelliferaCombTypes.PARCHED,
                out(MelliferaItems.BEESWAX, 1.00F),
                out(MelliferaItems.HONEY_DROP, 0.90F)),

            recipe(MelliferaCombTypes.MYSTERIOUS,
                out(MelliferaItems.PULSATING_PROPOLIS, 1.00F),
                out(MelliferaItems.HONEY_DROP, 0.40F)),

            // Deliberately empty -- see class javadoc.
            recipe(MelliferaCombTypes.IRRADIATED),

            recipe(MelliferaCombTypes.POWDERY,
                out(MelliferaItems.HONEY_DROP, 0.20F),
                out(MelliferaItems.BEESWAX, 0.20F),
                vanilla(Items.GUNPOWDER, 1, 0.90F)),

            recipe(MelliferaCombTypes.WHEATEN,
                out(MelliferaItems.HONEY_DROP, 0.20F),
                out(MelliferaItems.BEESWAX, 0.20F),
                vanilla(Items.WHEAT, 1, 0.80F)),

            recipe(MelliferaCombTypes.MOSSY,
                out(MelliferaItems.BEESWAX, 1.00F),
                out(MelliferaItems.HONEY_DROP, 0.90F)),

            recipe(MelliferaCombTypes.MELLOW,
                out(MelliferaItems.HONEYDEW, 0.60F),
                out(MelliferaItems.BEESWAX, 0.20F),
                vanilla(Items.QUARTZ, 1, 0.30F)),

            // -- Extra Bees resource combs -------------------------------------------
            // Base rows straight from Binnie's EnumHoneyComb (ForestryMC/Binnie,
            // master-MC1.12, LGPL v3): every ore comb calls copyProducts(STONE) first,
            // which is why beeswax 0.50 / honey drop 0.25 repeats on all seven of them.
            //
            // The *material* row is where this necessarily departs, and the departure is
            // deliberate rather than forced. Upstream hands out one full ore-dust per comb
            // (`tryAddProduct(IRON_DUST, 1.00f)`), because in a 1.12 pack that dust still
            // has to be smelted and sits inside an economy full of other ore sources. Here
            // it would be a free ingot per comb. So the yields below are cut to nuggets and
            // fractional chances, tuned so that at the species' own specialty rate a mature
            // apiary trades roughly one work cycle in two hundred for one ingot of iron --
            // slower than a player with a pickaxe, which is the point. See the per-comb
            // notes for the individual calls.
            recipe(MelliferaCombTypes.STONE,
                out(MelliferaItems.BEESWAX, 0.50F),
                out(MelliferaItems.HONEY_DROP, 0.25F)),

            // Coal is the one resource on this list that is deliberately not rationed. The
            // nugget-and-fractions treatment above exists because an ingot per comb would
            // undercut a pickaxe; coal has no nugget, no smelting step, and no scarcity worth
            // defending -- a player who wants coal has it. So a whole lump at even odds, which
            // makes the Coal bee a fuel supply rather than a novelty, and still leaves it
            // slower than actually mining.
            recipe(MelliferaCombTypes.COAL,
                out(MelliferaItems.BEESWAX, 0.50F),
                out(MelliferaItems.HONEY_DROP, 0.25F),
                vanilla(Items.COAL, 1, 0.50F)),

            // Copper has no Vanilla nugget, so the smallest honest unit is a raw chunk --
            // hence a far lower chance than iron's nugget for a comparable metal rate.
            // Still the most generous metal here, matching how cheap Vanilla copper is.
            recipe(MelliferaCombTypes.COPPER,
                out(MelliferaItems.BEESWAX, 0.50F),
                out(MelliferaItems.HONEY_DROP, 0.25F),
                vanilla(Items.RAW_COPPER, 1, 0.15F)),

            recipe(MelliferaCombTypes.IRON,
                out(MelliferaItems.BEESWAX, 0.50F),
                out(MelliferaItems.HONEY_DROP, 0.25F),
                vanilla(Items.IRON_NUGGET, 1, 0.80F)),

            // Gold's comb is already the rarest of the metals upstream (0.02 against iron's
            // 0.05), so the nugget itself can stay near-certain and the line still lands at
            // roughly half iron's throughput.
            recipe(MelliferaCombTypes.GOLD,
                out(MelliferaItems.BEESWAX, 0.50F),
                out(MelliferaItems.HONEY_DROP, 0.25F),
                vanilla(Items.GOLD_NUGGET, 1, 0.90F)),

            // Upstream gives six lapis per comb. Two at even odds keeps lapis the one
            // resource this line is actually good at without making enchanting free.
            recipe(MelliferaCombTypes.LAPIS,
                out(MelliferaItems.BEESWAX, 0.50F),
                out(MelliferaItems.HONEY_DROP, 0.25F),
                vanilla(Items.LAPIS_LAZULI, 2, 0.50F)),

            // Extra Bees pays these out in shards that craft up into a gem. Vanilla has no
            // shard item and adding one is a worse answer than dropping the whole gem at
            // shard-like odds, so that is what these two do.
            recipe(MelliferaCombTypes.EMERALD,
                out(MelliferaItems.BEESWAX, 0.50F),
                out(MelliferaItems.HONEY_DROP, 0.25F),
                vanilla(Items.EMERALD, 1, 0.12F)),

            // Still the slowest line in the mod: a 0.05 comb chance behind a 0.10 gem chance is
            // about two hundred apiary cycles per diamond. It was a thousand at the comb's old
            // 0.01, which asked more patience than the reward was worth.
            recipe(MelliferaCombTypes.DIAMOND,
                out(MelliferaItems.BEESWAX, 0.50F),
                out(MelliferaItems.HONEY_DROP, 0.25F),
                vanilla(Items.DIAMOND, 1, 0.10F)),

            // Redstone is the one resource cheap enough to leave near upstream's numbers
            // (beeswax 0.80, redstone 1.00, honey drop 0.50); only the dust is trimmed.
            recipe(MelliferaCombTypes.REDSTONE,
                out(MelliferaItems.BEESWAX, 0.80F),
                out(MelliferaItems.HONEY_DROP, 0.50F),
                vanilla(Items.REDSTONE, 1, 0.75F)));
    }

    private static Map.Entry<Identifier, CentrifugeRecipe> recipe(
        net.neoforged.neoforge.registries.DeferredHolder<com.joaonf.mellifera.bee.CombType, com.joaonf.mellifera.bee.CombType> comb,
        ItemProduct... outputs
    ) {
        return Map.entry(comb.getId(), new CentrifugeRecipe(STANDARD_TICKS, List.of(outputs)));
    }

    private static ItemProduct out(Supplier<? extends Item> item, float chance) {
        return out(item, 1, chance);
    }

    private static ItemProduct out(Supplier<? extends Item> item, int count, float chance) {
        return new ItemProduct(item::get, count, chance);
    }

    private static ItemProduct vanilla(Item item, int count, float chance) {
        return new ItemProduct(() -> item, count, chance);
    }
}
