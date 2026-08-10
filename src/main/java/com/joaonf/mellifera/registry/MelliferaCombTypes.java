package com.joaonf.mellifera.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.CentrifugeRecipe;
import com.joaonf.mellifera.bee.CombType;
import com.joaonf.mellifera.config.CustomBees;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

/// Every comb real Forestry actually produces, with both colours straight from its
/// EnumHoneyComb (LGPL v3).
///
/// That enum declares 17 constants, but three of them -- REDDENED, DARKENED and OMEGA --
/// have no bee that makes them and no centrifuge recipe anywhere in base Forestry; they
/// exist as hooks for Binnie's Extra Bees. Registering unobtainable items that do nothing
/// would be worse than leaving them out, so Forestry's contribution here is the complete
/// *functional* set: 14 combs, every one of which has both a producing species (see
/// MelliferaBeeSpecies) and a centrifuge recipe (see MelliferaCentrifugeRecipes).
///
/// The eight resource combs below come instead from Binnie's Extra Bees (ForestryMC/Binnie,
/// master-MC1.12, EnumHoneyComb, LGPL v3) and obey the same rule: each has a producing
/// species and a centrifuge recipe.
public final class MelliferaCombTypes {
    private static final ResourceKey<Registry<CombType>> REGISTRY_KEY =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Mellifera.MODID, "comb_type"));

    public static final DeferredRegister<CombType> TYPES = DeferredRegister.create(REGISTRY_KEY, Mellifera.MODID);

    public static final Registry<CombType> REGISTRY = TYPES.makeRegistry(builder -> {});

    // Honey branch.
    public static final DeferredHolder<CombType, CombType> HONEY =
        TYPES.register("honey", () -> new CombType("comb.mellifera.honey", 0xE8D56A, 0xFFA12B));

    // Heroic branch.
    public static final DeferredHolder<CombType, CombType> COCOA =
        TYPES.register("cocoa", () -> new CombType("comb.mellifera.cocoa", 0x674016, 0xFFB62B));

    // Infernal branch.
    public static final DeferredHolder<CombType, CombType> SIMMERING =
        TYPES.register("simmering", () -> new CombType("comb.mellifera.simmering", 0x981919, 0xFFB62B));

    // Industrious branch.
    public static final DeferredHolder<CombType, CombType> STRINGY =
        TYPES.register("stringy", () -> new CombType("comb.mellifera.stringy", 0xC8BE67, 0xBDA93E));

    // Frozen branch.
    public static final DeferredHolder<CombType, CombType> FROZEN =
        TYPES.register("frozen", () -> new CombType("comb.mellifera.frozen", 0xF9FFFF, 0xA0FFFF));

    // Noble branch.
    public static final DeferredHolder<CombType, CombType> DRIPPING =
        TYPES.register("dripping", () -> new CombType("comb.mellifera.dripping", 0xDC7613, 0xFFFF00));

    // Tropical branch.
    public static final DeferredHolder<CombType, CombType> SILKY =
        TYPES.register("silky", () -> new CombType("comb.mellifera.silky", 0x508907, 0xDDFF00));

    // Austere branch.
    public static final DeferredHolder<CombType, CombType> PARCHED =
        TYPES.register("parched", () -> new CombType("comb.mellifera.parched", 0xDCBE13, 0xFFFF00));

    // End branch.
    public static final DeferredHolder<CombType, CombType> MYSTERIOUS =
        TYPES.register("mysterious", () -> new CombType("comb.mellifera.mysterious", 0x161616, 0xE099FF));

    // Vengeful branch.
    public static final DeferredHolder<CombType, CombType> IRRADIATED =
        TYPES.register("irradiated", () -> new CombType("comb.mellifera.irradiated", 0xEAFFF3, 0xEEFF00));

    // Austere specialty (Austere itself yields these alongside Parched).
    public static final DeferredHolder<CombType, CombType> POWDERY =
        TYPES.register("powdery", () -> new CombType("comb.mellifera.powdery", 0xE4E4E4, 0xFFFFFF));

    // Agrarian branch.
    public static final DeferredHolder<CombType, CombType> WHEATEN =
        TYPES.register("wheaten", () -> new CombType("comb.mellifera.wheaten", 0xFEFF8F, 0xFFFFFF));

    // Boggy branch.
    public static final DeferredHolder<CombType, CombType> MOSSY =
        TYPES.register("mossy", () -> new CombType("comb.mellifera.mossy", 0x2A3313, 0x7E9939));

    // Monastic branch.
    public static final DeferredHolder<CombType, CombType> MELLOW =
        TYPES.register("mellow", () -> new CombType("comb.mellifera.mellow", 0x886000, 0xFFF960));

    // -- Extra Bees resource combs ------------------------------------------------------
    // Colours straight from Binnie's EnumHoneyComb (ForestryMC/Binnie, master-MC1.12,
    // LGPL v3), which stores them as decimals -- 9211025/13027020 for STONE, and so on.
    //
    // Every ore comb in that enum shares the same dark primary (3552564 = 0x363534) and
    // carries its material's colour on the secondary. That is deliberate upstream: the ore
    // combs read as one family of "rock with a vein in it", and it is the only thing that
    // keeps eight retinted copies of one sprite visually distinguishable. Reproduced rather
    // than prettified, because the alternative is eight combs nobody can tell apart.

    // Rocky branch -- the shared low-value filler comb every metal/gem bee also makes.
    public static final DeferredHolder<CombType, CombType> STONE =
        TYPES.register("stone", () -> new CombType("comb.mellifera.stone", 0x8C8C91, 0xC6C6CC));

    /// ADDITION. Not an Extra Bees comb -- see the Coal bee in MelliferaBeeSpecies for why it
    /// exists at all. It joins the ore-comb family rather than inventing a look of its own,
    /// so it keeps 0x363534 on the cell walls; the fill is the blue-black of Vanilla's coal
    /// rather than coal's true near-black, which against those walls would leave the two
    /// tints indistinguishable and the comb reading as one flat dark blob.
    public static final DeferredHolder<CombType, CombType> COAL =
        TYPES.register("coal", () -> new CombType("comb.mellifera.coal", 0x363534, 0x1D1D21));

    // Metallic branch.
    public static final DeferredHolder<CombType, CombType> COPPER =
        TYPES.register("copper", () -> new CombType("comb.mellifera.copper", 0x363534, 0xD16308));

    public static final DeferredHolder<CombType, CombType> IRON =
        TYPES.register("iron", () -> new CombType("comb.mellifera.iron", 0x363534, 0xA87058));

    // Precious branch.
    public static final DeferredHolder<CombType, CombType> GOLD =
        TYPES.register("gold", () -> new CombType("comb.mellifera.gold", 0x363534, 0xE6CC0B));

    // Mineral branch.
    public static final DeferredHolder<CombType, CombType> LAPIS =
        TYPES.register("lapis", () -> new CombType("comb.mellifera.lapis", 0x363534, 0x3D2CDB));

    // Gemstone branch.
    public static final DeferredHolder<CombType, CombType> EMERALD =
        TYPES.register("emerald", () -> new CombType("comb.mellifera.emerald", 0x363534, 0x1CFF03));

    public static final DeferredHolder<CombType, CombType> DIAMOND =
        TYPES.register("diamond", () -> new CombType("comb.mellifera.diamond", 0x363534, 0x7FBDFA));

    // Energetic branch.
    public static final DeferredHolder<CombType, CombType> REDSTONE =
        TYPES.register("redstone", () -> new CombType("comb.mellifera.redstone", 0xFA9696, 0xE61010));

    private MelliferaCombTypes() {}

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        modEventBus.addListener(MelliferaCombTypes::registerCustom);
    }

    /// Combs invented by a file in `config/mellifera/custom_bees/` go into this same registry,
    /// on the same event, one listener later than the DeferredRegister above -- the same trick
    /// custom species use, and for the same reason: the registry freezes when startup ends, and
    /// everything downstream of it (the comb item's name and tint, the Centrifuge, JEI, the
    /// creative tab) reads the registry without caring who filled it.
    ///
    /// An id already taken by a built-in keeps the built-in, and loses its centrifuge row along
    /// with it: registering over `mellifera:honey` would throw, and quietly letting the recipe
    /// through anyway would leave a file that failed to add a comb having silently rewritten
    /// what every Honey comb in the world spins down into.
    private static void registerCustom(RegisterEvent event) {
        event.register(REGISTRY_KEY, helper -> {
            Map<Identifier, CentrifugeRecipe> recipes = new LinkedHashMap<>();
            List<Identifier> registered = new ArrayList<>();

            CustomBees.combs().forEach((id, definition) -> {
                if (REGISTRY.containsKey(id)) {
                    Mellifera.LOGGER.warn("custom comb {} has the id of a built-in comb -- the built-in one is kept", id);
                    return;
                }

                helper.register(id, definition.toCombType());
                registered.add(id);
                definition.toRecipe().ifPresent(recipe -> recipes.put(id, recipe));
            });

            // Inside the same callback, so the machine's table is complete before anything can
            // spin a comb -- and kept out of the transcribed table, so these rows never reach
            // centrifuge.toml. See MelliferaCentrifugeRecipes.addCustom.
            MelliferaCentrifugeRecipes.addCustom(recipes);

            // Said out loud, like the bee loader's own line: a comb is otherwise the one thing
            // in these files that succeeds in silence, and somebody whose comb never appears
            // has no way to tell a rejected file from a bee that simply never rolled it.
            if (!registered.isEmpty()) {
                Mellifera.LOGGER.info("registered {} custom comb(s): {}", registered.size(), registered);
            }
        });
    }

    public static CombType get(Identifier id) {
        CombType type = REGISTRY.getValue(id);
        return type != null ? type : HONEY.get();
    }
}
