package com.joaonf.mellifera.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.BeeMutation;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;

/// Bees defined by the player, in `config/mellifera/custom_bees/`.
///
/// One JSON file per bee; the file name is the id, so `obsidian.json` becomes
/// `mellifera:obsidian`. See CustomBeeDefinition for the fields.
///
/// WHY THE CONFIG FOLDER AND NOT A DATAPACK. A datapack is the more Minecraft-shaped answer
/// and is where this should eventually also be readable from: it is per-world, it travels
/// with a modpack, and it survives `/reload`. It is also a worse place to put a file for the
/// person most likely to write one -- a single player who wants a bee in every world they
/// play, and who would otherwise have to install the same pack into each save. So this reads
/// the config folder, which is global, and the JSON shape is deliberately the shape a
/// datapack loader would want, so that reading both later costs a second source and no
/// migration for anything already written.
///
/// WHY REGISTRATION TIME. These become entries in the same registry the 58 built-ins live in,
/// which is frozen once startup finishes -- so they are read during RegisterEvent, before
/// anything can look a species up. That is the whole trick, and it is what keeps every other
/// part of the mod ignorant that custom bees exist: the guide, JEI, the tooltips and the
/// breeding engine all just see a registry with more bees in it than last time.
///
/// A file may also invent the comb its bee produces, rather than only naming one of the 23
/// built-ins -- see CustomCombDefinition, which is written inside the bee's `combs` list.
///
/// The one part of the mod that does know the difference is the chance config, and knowing it
/// is the point: `bees.toml` and `centrifuge.toml` are generated and never pruned, so a section
/// for anything defined here would outlive the file that defined it. Every chance a file in
/// this folder sets is edited in that file, which is also the only thing that has to be deleted
/// to undo it. See MelliferaOutputConfig.orderedSpecies.
///
/// The cost of that trick, stated plainly: editing one of these files needs a restart, and a
/// server and its clients must carry the same folder. A client missing a bee the server has
/// will show it by its id and render it grey rather than crash -- the species is stored in
/// item data as a plain Identifier -- but it will not be right. A comb the client is missing
/// degrades the same way, into Honey Comb's name and colours, because that is the fallback
/// MelliferaCombTypes.get has always had; what the comb actually spins down into stays the
/// server's decision either way.
public final class CustomBees {
    private static final String FOLDER = "custom_bees";
    private static final String EXAMPLE = "example_obsidian.json";

    /// Read once at RegisterEvent and kept, because the species and the mutations are wanted
    /// at two different moments in startup and reading the folder twice would let a file
    /// edited in between produce a bee whose own mutation names a species that never loaded.
    private static Map<Identifier, CustomBeeDefinition> loaded = Map.of();

    private CustomBees() {}

    /// The bees to register, keyed by the id their file name gives them.
    public static Map<Identifier, BeeSpecies> species() {
        loadOnce();

        Map<Identifier, BeeSpecies> species = new LinkedHashMap<>();
        loaded.forEach((id, definition) -> species.put(id, definition.toSpecies()));
        return species;
    }

    /// Every cross the loaded files declare. Parents naming a species that does not exist are
    /// dropped with a warning rather than registered: a mutation into an unknown parent is
    /// unreachable anyway, and leaving it in the table would put a dead row in the Apiarist
    /// Database's "Bred from" list, which is the one place a player goes to find out what is
    /// reachable.
    public static List<BeeMutation> mutations(java.util.function.Predicate<Identifier> speciesExists) {
        loadOnce();

        List<BeeMutation> mutations = new ArrayList<>();
        loaded.forEach((id, definition) -> {
            for (BeeMutation mutation : definition.toMutations(id)) {
                if (!speciesExists.test(mutation.parentA()) || !speciesExists.test(mutation.parentB())) {
                    Mellifera.LOGGER.warn("custom bee {} is bred from {} + {}, and one of those does not exist -- cross dropped",
                        id, mutation.parentA(), mutation.parentB());
                    continue;
                }
                mutations.add(mutation);
            }
        });

        return List.copyOf(mutations);
    }

    /// Every comb the loaded files invent, keyed by the id its definition gives it (see
    /// CustomCombDefinition for why they are written inside a bee).
    ///
    /// The first definition of an id wins. Two bees sharing a new comb is the expected way to
    /// do it -- one defines it, the others name it -- and a second *definition* of the same id
    /// is either a harmless copy-paste of an identical block or a genuine mistake. Both get the
    /// first copy; only the ones that actually disagree get a line in the log, since warning
    /// about an identical duplicate tells nobody anything.
    public static Map<Identifier, CustomCombDefinition> combs() {
        loadOnce();

        Map<Identifier, CustomCombDefinition> combs = new LinkedHashMap<>();
        loaded.forEach((bee, definition) -> {
            for (CustomCombDefinition comb : definition.newCombs()) {
                CustomCombDefinition existing = combs.putIfAbsent(comb.id(), comb);
                if (existing != null && !existing.equals(comb)) {
                    Mellifera.LOGGER.warn("comb {} is defined twice with different values -- {}'s copy is ignored,"
                        + " and a bee that only wants to produce it should name it instead of defining it",
                        comb.id(), bee);
                }
            }
        });

        return combs;
    }

    public static boolean isCustom(Identifier species) {
        return loaded.containsKey(species);
    }

    /// The branch a custom bee asked to be filed under, as a translation key, or empty if it
    /// named none. Whether that branch exists is not this class's business -- see
    /// MelliferaBeeBranches, which is the only thing that knows what branches there are.
    public static java.util.Optional<String> branchKeyOf(Identifier species) {
        CustomBeeDefinition definition = loaded.get(species);
        return definition == null
            ? java.util.Optional.empty()
            : definition.branch().map(name -> "branch.mellifera." + name);
    }

    private static synchronized void loadOnce() {
        if (!loaded.isEmpty()) {
            return;
        }

        Path folder = FMLPathsHolder.configDir().resolve(Mellifera.MODID).resolve(FOLDER);
        Map<Identifier, CustomBeeDefinition> definitions = new LinkedHashMap<>();

        try {
            Files.createDirectories(folder);
            writeExample(folder);
        } catch (IOException e) {
            Mellifera.LOGGER.error("could not prepare {} -- no custom bees will load", folder, e);
            loaded = Map.of();
            return;
        }

        try (Stream<Path> files = Files.list(folder)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().forEach(path -> {
                Identifier id = idOf(path);
                if (id == null) {
                    return;
                }

                CustomBeeDefinition definition = read(path);
                if (definition != null) {
                    definitions.put(id, definition);
                }
            });
        } catch (IOException e) {
            Mellifera.LOGGER.error("could not list {} -- no custom bees will load", folder, e);
        }

        if (!definitions.isEmpty()) {
            Mellifera.LOGGER.info("loaded {} custom bee(s): {}", definitions.size(), definitions.keySet());
        }
        loaded = Map.copyOf(definitions);
    }

    /// A file name has to survive being turned into a registry path, and the rules for that
    /// are narrower than the rules for a file name. Rejecting loudly beats a bee that is
    /// registered under a mangled id nothing else can refer to.
    private static Identifier idOf(Path path) {
        String name = path.getFileName().toString();
        String stem = name.substring(0, name.length() - ".json".length()).toLowerCase(Locale.ROOT);
        Identifier id = Identifier.tryBuild(Mellifera.MODID, stem);
        if (id == null) {
            Mellifera.LOGGER.warn("{} is not a usable bee id -- use lower-case letters, digits and underscores", name);
        }
        return id;
    }

    private static CustomBeeDefinition read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            return CustomBeeDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> Mellifera.LOGGER.error("{} is not a valid bee: {}", path.getFileName(), error))
                .orElse(null);
        } catch (IOException | RuntimeException e) {
            Mellifera.LOGGER.error("could not read {}", path, e);
            return null;
        }
    }

    /// Written once, and never again if it is deleted.
    ///
    /// A folder that appears empty teaches nobody the format, and pointing at documentation
    /// from a log line nobody reads is not better. This is a real, loaded bee -- Obsidian,
    /// bred from Rock and Sinister -- so the first thing a player sees is the thing working,
    /// and deleting the file removes the bee. A marker file records that it was placed, so
    /// somebody who deletes it does not get it back on the next launch.
    ///
    /// It produces one existing comb and one it invents, because those are the two things
    /// worth learning from this file and a bee that only did the easy one would leave the
    /// harder shape undocumented.
    private static void writeExample(Path folder) throws IOException {
        Path marker = folder.resolve(".example-written");
        if (Files.exists(marker) || Files.exists(folder.resolve(EXAMPLE))) {
            return;
        }

        Files.writeString(folder.resolve(EXAMPLE), """
            {
              "name": "Obsidian",
              "branch": "custom",

              "min_celsius": 0.0,
              "max_celsius": 60.0,
              "dominant": true,

              "primary_color": "#4B3A78",
              "glint": false,

              "combs": [
                { "comb": "mellifera:stone", "chance": 0.20 },
                {
                  "comb": {
                    "id": "obsidian",
                    "name": "Obsidian Comb",
                    "primary_color": "#363534",
                    "secondary_color": "#4B3A78",
                    "centrifuge": [
                      { "item": "mellifera:beeswax", "chance": 0.50 },
                      { "item": "mellifera:honey_drop", "chance": 0.25 },
                      { "item": "minecraft:obsidian", "count": 1, "chance": 0.05 }
                    ]
                  },
                  "chance": 0.10
                }
              ],

              "traits": {
                "speed": "slowest",
                "lifespan": "long",
                "fertility": "low",
                "tolerance": "both_2"
              },

              "mutations": [
                { "parents": ["mellifera:rock", "mellifera:sinister"], "chance": 0.06 }
              ]
            }
            """, StandardCharsets.UTF_8);

        Files.writeString(marker, "The example bee was placed here once. Delete example_obsidian.json to remove it;\n"
            + "this file stops it coming back on the next launch.\n", StandardCharsets.UTF_8);
    }

    /// FMLPaths is loader-side API; keeping the one call behind a holder means this class can
    /// be exercised by a test that has no loader.
    static final class FMLPathsHolder {
        private FMLPathsHolder() {}

        static Path configDir() {
            return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        }
    }
}
