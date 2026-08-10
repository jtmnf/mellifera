package com.joaonf.mellifera.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.CentrifugeRecipe;
import com.joaonf.mellifera.bee.CombProduct;
import com.joaonf.mellifera.bee.ItemProduct;
import com.joaonf.mellifera.network.SyncOutputChancesPayload;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaCentrifugeRecipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/// The second layer over MelliferaOutputConfig: while a client is connected to a server, the
/// server's numbers win.
///
/// MelliferaOutputConfig reads `config/mellifera/` on both sides and always has. That is
/// still what a dedicated server rolls against and what a client falls back to; this class
/// only decides which of the two a client is looking at right now. The desync it removes was
/// never a gameplay bug -- the drops were always the server's -- but a Database page and a
/// JEI tooltip promising 30% while the server pays 15% is worse than no number at all.
///
/// SINGLEPLAYER: nothing is sent and nothing is applied. The integrated server and the client
/// are one JVM sharing one set of static tables, so the values a client would "receive" are
/// the very values the server is reading, and installing them would mean the apiary's rolls
/// come from a round trip through a socket instead of from the file. Even a faithful round
/// trip is a needless place for the two to be able to disagree, so the send is skipped for
/// memory connections -- which is exactly the same-JVM test, and the reason a LAN world still
/// syncs the guests (real connections) while leaving the host alone.
///
/// DISCONNECT: the client restores its own tables, because the alternative is a player who
/// visited one server once seeing that server's chances in every singleplayer world
/// afterwards, with nothing on screen to explain where the numbers came from.
@EventBusSubscriber(modid = Mellifera.MODID)
public final class MelliferaOutputSync {
    /// The centrifuge tables as this side had them before a server spoke, kept because
    /// MelliferaCentrifugeRecipes takes a whole table at a time and MelliferaOutputConfig
    /// pushed its own copy there once at setup and did not keep one. Null when no server
    /// values are installed, which is also how "there is nothing to restore" is spelled.
    private static @Nullable Map<Identifier, CentrifugeRecipe> localCombRecipes;
    private static @Nullable Map<Item, CentrifugeRecipe> localItemRecipes;

    private MelliferaOutputSync() {}

    /// Play phase rather than the configuration phase, even though the values are known long
    /// before a player exists: a configuration-phase packet has to be answered before the
    /// client may finish joining, and nothing here is worth being able to hang a login.
    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.connection.getConnection().isMemoryConnection()) {
            return;
        }

        PacketDistributor.sendToPlayer(player, current());
    }

    /// This side's effective tables, reduced to the chances that are all a client needs.
    static SyncOutputChancesPayload current() {
        Map<Identifier, List<Float>> combs = new HashMap<>();
        Map<Identifier, List<Float>> products = new HashMap<>();

        for (Identifier species : MelliferaBeeSpecies.REGISTRY.keySet()) {
            combs.put(species, chancesOfCombs(MelliferaOutputConfig.combsOf(species)));

            // Most species have no non-comb drop at all, and an empty list here would mean
            // the same thing on both sides for the same reason the positional encoding
            // works: the drops are a Java table, so a species has them either way or neither.
            List<Float> drops = chancesOfItems(MelliferaOutputConfig.productsOf(species));
            if (!drops.isEmpty()) {
                products.put(species, drops);
            }
        }

        Map<Identifier, List<Float>> centrifugeCombs = new HashMap<>();
        MelliferaCentrifugeRecipes.allCombRecipes()
            .forEach((comb, recipe) -> centrifugeCombs.put(comb, chancesOfItems(recipe.outputs())));

        Map<Identifier, List<Float>> centrifugeItems = new HashMap<>();
        MelliferaCentrifugeRecipes.allItemRecipes()
            .forEach((item, recipe) -> centrifugeItems.put(BuiltInRegistries.ITEM.getKey(item), chancesOfItems(recipe.outputs())));

        return new SyncOutputChancesPayload(combs, products, centrifugeCombs, centrifugeItems);
    }

    /// Installs a server's chances over this client's own.
    ///
    /// Rebuilt against the local tables rather than against whatever is currently installed,
    /// so a second server's numbers can never be layered onto a first server's.
    public static void apply(SyncOutputChancesPayload payload) {
        restoreLocal();

        Map<Identifier, List<CombProduct>> combs = new HashMap<>();
        Map<Identifier, List<ItemProduct>> products = new HashMap<>();

        // Driven by the local registry, not by the payload's keys: a species this side does
        // not have has no table to hang the chances off, and looking one up by id would be
        // asking the payload to decide whether an unknown id is fatal.
        for (Identifier species : MelliferaBeeSpecies.REGISTRY.keySet()) {
            List<CombProduct> localCombs = MelliferaOutputConfig.combsOf(species);
            List<Float> combChances = payload.combs().get(species);
            if (aligned(localCombs, combChances, species)) {
                List<CombProduct> rebuilt = new ArrayList<>(localCombs.size());
                for (int i = 0; i < localCombs.size(); i++) {
                    rebuilt.add(new CombProduct(localCombs.get(i).comb(), combChances.get(i)));
                }
                combs.put(species, List.copyOf(rebuilt));
            }

            List<ItemProduct> localDrops = MelliferaOutputConfig.productsOf(species);
            List<Float> dropChances = payload.products().get(species);
            if (aligned(localDrops, dropChances, species)) {
                products.put(species, rebuild(localDrops, dropChances));
            }
        }

        MelliferaOutputConfig.applyServerValues(Map.copyOf(combs), Map.copyOf(products));

        localCombRecipes = MelliferaCentrifugeRecipes.allCombRecipes();
        localItemRecipes = MelliferaCentrifugeRecipes.allItemRecipes();

        Map<Identifier, CentrifugeRecipe> byComb = new HashMap<>(localCombRecipes);
        localCombRecipes.forEach((comb, recipe) -> {
            List<Float> chances = payload.centrifugeCombs().get(comb);
            if (aligned(recipe.outputs(), chances, comb)) {
                byComb.put(comb, new CentrifugeRecipe(recipe.processTicks(), rebuild(recipe.outputs(), chances)));
            }
        });

        Map<Item, CentrifugeRecipe> byItem = new HashMap<>(localItemRecipes);
        localItemRecipes.forEach((item, recipe) -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            List<Float> chances = payload.centrifugeItems().get(id);
            if (aligned(recipe.outputs(), chances, id)) {
                byItem.put(item, new CentrifugeRecipe(recipe.processTicks(), rebuild(recipe.outputs(), chances)));
            }
        });

        MelliferaCentrifugeRecipes.applyOverrides(Map.copyOf(byComb), Map.copyOf(byItem));
    }

    /// Back to `config/mellifera/` as this side read it.
    ///
    /// Also correct to call when nothing was ever applied, which is what the disconnect hook
    /// relies on -- it cannot tell a singleplayer world from a server that never spoke.
    public static void restoreLocal() {
        MelliferaOutputConfig.clearServerValues();

        if (localCombRecipes != null && localItemRecipes != null) {
            MelliferaCentrifugeRecipes.applyOverrides(localCombRecipes, localItemRecipes);
        }
        localCombRecipes = null;
        localItemRecipes = null;
    }

    private static List<Float> chancesOfCombs(List<CombProduct> outputs) {
        List<Float> chances = new ArrayList<>(outputs.size());
        for (CombProduct output : outputs) {
            chances.add(output.chance());
        }
        return chances;
    }

    private static List<Float> chancesOfItems(List<ItemProduct> outputs) {
        List<Float> chances = new ArrayList<>(outputs.size());
        for (ItemProduct output : outputs) {
            chances.add(output.chance());
        }
        return chances;
    }

    private static List<ItemProduct> rebuild(List<ItemProduct> local, List<Float> chances) {
        List<ItemProduct> rebuilt = new ArrayList<>(local.size());
        for (int i = 0; i < local.size(); i++) {
            rebuilt.add(new ItemProduct(local.get(i).item(), local.get(i).count(), chances.get(i)));
        }
        return List.copyOf(rebuilt);
    }

    /// Whether a received chance list can be laid over a local table position by position.
    ///
    /// A mismatch means the two sides' Java tables differ, which the channel's version check
    /// cannot catch -- it compares one version string, not 58 species. Keeping the local row
    /// and saying so beats installing a table where a chance has slid onto the wrong item.
    private static boolean aligned(List<?> local, @Nullable List<Float> chances, Identifier key) {
        if (chances == null) {
            return false;
        }
        if (chances.size() != local.size()) {
            Mellifera.LOGGER.warn("server sent {} chances for {} but this side has {} outputs -- keeping the local ones",
                chances.size(), key, local.size());
            return false;
        }
        return true;
    }
}
