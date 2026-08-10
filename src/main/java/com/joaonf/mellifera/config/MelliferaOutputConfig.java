package com.joaonf.mellifera.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.BeeBranch;
import com.joaonf.mellifera.bee.CentrifugeRecipe;
import com.joaonf.mellifera.bee.CombProduct;
import com.joaonf.mellifera.bee.ItemProduct;
import com.joaonf.mellifera.registry.MelliferaBeeBranches;
import com.joaonf.mellifera.registry.MelliferaBeeProducts;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaCentrifugeRecipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

/// Every drop chance in the mod, in `config/mellifera/`.
///
/// Two files rather than one, because they answer different questions and a pack author
/// usually wants only one of them: `bees.toml` is what an Apiary yields, `centrifuge.toml`
/// is what those yields turn into. Together they cover the whole output chain -- every
/// species' combs, every species' side-drops, and every centrifuge result.
///
/// Deliberately not a NeoForge ModConfigSpec. A spec has to be built during mod construction
/// and its every path declared up front, and at that point the species registry is still
/// empty -- there is nothing to enumerate. Waiting until common setup and driving both files
/// off the registries themselves is what makes the config complete by construction: a species
/// added later shows up in the file on the next launch without anyone remembering to add it,
/// which for a mod whose whole content is 58 species is the difference between a config that
/// covers everything and one that covers whatever was current when it was written.
///
/// Both files cover what Java declares, and nothing a player wrote themselves. A bee or a comb
/// from `custom_bees/` carries its own chances in its own file -- see orderedSpecies for why
/// generating a section for one of those is worse than leaving it out.
///
/// The defaults in the Java tables stay the source of truth. This layer only ever replaces a
/// chance with one the file gives, so deleting the folder restores Forestry's numbers exactly.
///
/// MULTIPLAYER: the file is read on both sides, but a client's own copy is only what it falls
/// back to. A server sends its effective tables at login and they take precedence for as long
/// as that client is connected -- see MelliferaOutputSync, which owns that layer and puts the
/// local values back on disconnect. What actually drops was always the server's decision; the
/// sync is what makes the Apiarist Database and JEI agree with it, so the two folders no
/// longer have to be kept identical by hand.
public final class MelliferaOutputConfig {
    private static final String BEES_FILE = "bees.toml";
    private static final String CENTRIFUGE_FILE = "centrifuge.toml";

    /// Empty until common setup: anything asking before then gets the Java defaults, which is
    /// also what happens if the files fail to load at all.
    private static Map<Identifier, List<CombProduct>> combs = Map.of();
    private static Map<Identifier, List<ItemProduct>> products = Map.of();

    /// What the connected server says, or null when nobody is saying anything -- which is
    /// every dedicated server, every singleplayer world, and every client between logins.
    /// Only ever written on a client that is talking to a separate JVM (see
    /// MelliferaOutputSync), so no apiary's rolls can ever be reading a half-installed table.
    private static @Nullable Map<Identifier, List<CombProduct>> serverCombs;
    private static @Nullable Map<Identifier, List<ItemProduct>> serverProducts;

    private MelliferaOutputConfig() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(MelliferaOutputConfig::onCommonSetup);
    }

    /// Common setup is the first point every registry this reads from is populated. Enqueued
    /// rather than run on the parallel dispatch thread so the tables are published to the
    /// main thread before anything can consult them.
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MelliferaOutputConfig::load);
    }

    /// A species' comb table, with configured chances applied: the connected server's if
    /// there is one, otherwise this side's file, otherwise the species' own table -- which is
    /// Java's for a built-in bee and the JSON's for a custom one, since `bees.toml` has no
    /// section for those to override.
    public static List<CombProduct> combsOf(Identifier species) {
        if (serverCombs != null) {
            List<CombProduct> synced = serverCombs.get(species);
            if (synced != null) {
                return synced;
            }
        }

        List<CombProduct> configured = combs.get(species);
        return configured != null ? configured : MelliferaBeeSpecies.get(species).products();
    }

    /// A species' non-comb drops (royal jelly, pollen, ice shards...), with configured
    /// chances applied. Same precedence as combsOf.
    public static List<ItemProduct> productsOf(Identifier species) {
        if (serverProducts != null) {
            List<ItemProduct> synced = serverProducts.get(species);
            if (synced != null) {
                return synced;
            }
        }

        List<ItemProduct> configured = products.get(species);
        return configured != null ? configured : MelliferaBeeProducts.forSpecies(species);
    }

    /// Called only by MelliferaOutputSync, which is the one thing that knows whether a server
    /// is worth listening to.
    static void applyServerValues(Map<Identifier, List<CombProduct>> syncedCombs, Map<Identifier, List<ItemProduct>> syncedProducts) {
        serverCombs = syncedCombs;
        serverProducts = syncedProducts;
    }

    static void clearServerValues() {
        serverCombs = null;
        serverProducts = null;
    }

    /// The species in the order the file should read: branch by branch, and inside a branch in
    /// breeding order -- the same order the species file declares them and the Apiarist
    /// Database lists them.
    ///
    /// Driven off the branch table rather than off the registry, because a Registry's keySet
    /// is a HashMap's and comes out in hash order. The first version of this iterated the
    /// registry with a comment claiming it was registration order; the generated file opened
    /// on Frugal, Spectral, Rural, Merry, which is nobody's idea of an index.
    ///
    /// Anything the branch table has not filed still gets its section, appended at the end --
    /// this decides the shape of a config file and has no business dropping an entry over it.
    ///
    /// Bees written in `custom_bees/` are the one exception, and not for ordering reasons: this
    /// file is generated and never prunes what it has already written, so their sections would
    /// outlive the JSON that asked for them -- delete the bee and its chances sit here forever,
    /// under a species nothing can breed. Their chances belong in the file that defines the bee,
    /// where deleting it deletes all of it. Same rule as their combs (see
    /// MelliferaCentrifugeRecipes.addCustom), and the reason combsOf falls back to the species'
    /// own table for them, which is the JSON's values.
    private static List<Identifier> orderedSpecies() {
        List<Identifier> ordered = new ArrayList<>();
        for (BeeBranch branch : MelliferaBeeBranches.all()) {
            for (Identifier species : branch.species()) {
                if (MelliferaBeeSpecies.REGISTRY.containsKey(species) && !CustomBees.isCustom(species)) {
                    ordered.add(species);
                }
            }
        }

        for (Identifier species : MelliferaBeeSpecies.REGISTRY.keySet()) {
            if (!ordered.contains(species) && !CustomBees.isCustom(species)) {
                ordered.add(species);
            }
        }

        return ordered;
    }

    private static void load() {
        Path folder = FMLPaths.CONFIGDIR.get().resolve(Mellifera.MODID);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            Mellifera.LOGGER.error("could not create {} -- every chance stays at its default", folder, e);
            return;
        }

        loadBees(folder.resolve(BEES_FILE));
        loadCentrifuge(folder.resolve(CENTRIFUGE_FILE));
    }

    // ---------------------------------------------------------------- bees.toml

    private static void loadBees(Path file) {
        Map<Identifier, List<CombProduct>> loadedCombs = new HashMap<>();
        Map<Identifier, List<ItemProduct>> loadedProducts = new HashMap<>();

        try (CommentedFileConfig config = open(file)) {
            for (Identifier species : orderedSpecies()) {
                String section = key(species);

                List<CombProduct> defaults = MelliferaBeeSpecies.get(species).products();
                List<CombProduct> effective = new ArrayList<>(defaults.size());
                Set<String> used = new HashSet<>();
                for (CombProduct product : defaults) {
                    String name = key(product.comb());
                    float chance = used.add(name)
                        ? chance(config, List.of(section, "combs", name), product.chance())
                        : duplicate(file, section, name, product.chance());
                    effective.add(new CombProduct(product.comb(), chance));
                }
                loadedCombs.put(species, List.copyOf(effective));

                List<ItemProduct> defaultDrops = MelliferaBeeProducts.forSpecies(species);
                loadedProducts.put(species, itemChances(config, file, List.of(section, "drops"), defaultDrops));
            }

            config.save();
        } catch (Exception e) {
            Mellifera.LOGGER.error("could not read {} -- every bee chance stays at its default", file, e);
            return;
        }

        combs = Map.copyOf(loadedCombs);
        products = Map.copyOf(loadedProducts);
    }

    // ---------------------------------------------------------------- centrifuge.toml

    private static void loadCentrifuge(Path file) {
        Map<Identifier, CentrifugeRecipe> byComb = new HashMap<>();
        Map<Item, CentrifugeRecipe> byItem = new HashMap<>();

        try (CommentedFileConfig config = open(file)) {
            for (Map.Entry<Identifier, CentrifugeRecipe> entry : MelliferaCentrifugeRecipes.defaultCombRecipes().entrySet()) {
                byComb.put(entry.getKey(), recipe(config, file, List.of("combs", key(entry.getKey())), entry.getValue()));
            }

            // The handful of non-comb inputs the machine also takes -- silky propolis and
            // friends. Same shape, its own section so it cannot be confused for a comb.
            for (Map.Entry<Item, CentrifugeRecipe> entry : MelliferaCentrifugeRecipes.defaultItemRecipes().entrySet()) {
                Identifier id = BuiltInRegistries.ITEM.getKey(entry.getKey());
                byItem.put(entry.getKey(), recipe(config, file, List.of("inputs", key(id)), entry.getValue()));
            }

            config.save();
        } catch (Exception e) {
            Mellifera.LOGGER.error("could not read {} -- every centrifuge chance stays at its default", file, e);
            return;
        }

        MelliferaCentrifugeRecipes.applyOverrides(Map.copyOf(byComb), Map.copyOf(byItem));
    }

    private static CentrifugeRecipe recipe(
        CommentedFileConfig config, Path file, List<String> path, CentrifugeRecipe defaults
    ) {
        return new CentrifugeRecipe(defaults.processTicks(), itemChances(config, file, path, defaults.outputs()));
    }

    /// Reads one table of item id -> chance, writing back anything the file does not have.
    private static List<ItemProduct> itemChances(
        CommentedFileConfig config, Path file, List<String> path, List<ItemProduct> defaults
    ) {
        List<ItemProduct> effective = new ArrayList<>(defaults.size());
        Set<String> used = new HashSet<>();

        for (ItemProduct product : defaults) {
            Identifier id = BuiltInRegistries.ITEM.getKey(product.item().get());
            String name = key(id);

            List<String> full = new ArrayList<>(path);
            full.add(name);
            float chance = used.add(name)
                ? chance(config, full, product.chance())
                : duplicate(file, String.join(".", path), name, product.chance());

            effective.add(new ItemProduct(product.item(), product.count(), chance));
        }

        return List.copyOf(effective);
    }

    // ---------------------------------------------------------------- values

    /// One chance: whatever the file says, or the default written back into it.
    ///
    /// Always writes, which is what keeps the file complete -- a species or an output added
    /// in a later version appears in the existing file on the next launch instead of being
    /// silently unconfigurable. Out-of-range and non-numeric values are corrected in the file
    /// rather than only in memory, so the next edit starts from something valid.
    private static float chance(CommentedFileConfig config, List<String> path, float fallback) {
        Object raw = config.get(path);
        float value = fallback;

        if (raw instanceof Number number) {
            value = number.floatValue();
            if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
                Mellifera.LOGGER.warn("{} is {}, which is not a chance between 0 and 1 -- clamped",
                    String.join(".", path), raw);
                value = Math.clamp(Float.isFinite(value) ? value : fallback, 0.0F, 1.0F);
            }
        } else if (raw != null) {
            Mellifera.LOGGER.warn("{} is {}, which is not a number -- reset to {}",
                String.join(".", path), raw, fallback);
        }

        config.set(path, widen(value));
        config.setComment(path, " default " + fallback);
        return value;
    }

    /// float to double the long way round, because the short way is what a player would have
    /// to look at: `(double) 0.30F` is 0.30000001192092896, and TOML writes every digit of it.
    /// Going through the float's own shortest decimal representation writes `0.3`.
    private static double widen(float value) {
        return Double.parseDouble(Float.toString(value));
    }

    /// One output listed twice under the same item. Nothing in the tables does this today,
    /// and if something ever does, one TOML key cannot address both -- so the second copy
    /// keeps its default and says so rather than being quietly overwritten by the first.
    private static float duplicate(Path file, String section, String name, float fallback) {
        Mellifera.LOGGER.warn("{} lists {} twice under [{}] -- the repeat is not configurable and stays at {}",
            file.getFileName(), name, section, fallback);
        return fallback;
    }

    // ---------------------------------------------------------------- files

    private static CommentedFileConfig open(Path file) {
        CommentedFileConfig config = CommentedFileConfig.builder(file, TomlFormat.instance())
            .onFileNotFound(FileNotFoundAction.CREATE_EMPTY)
            .preserveInsertionOrder()
            .sync()
            .build();
        config.load();
        return config;
    }

    /// This mod's own ids lose their namespace, so the file reads `forest` and `honey` rather
    /// than `mellifera:forest`. Anything else keeps its full id -- Vanilla drops are
    /// `minecraft:sugar`, and an addon's species would stay unambiguous. Keys are built the
    /// same way on read and on write, so the two can never disagree.
    private static String key(Identifier id) {
        return Mellifera.MODID.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }
}
