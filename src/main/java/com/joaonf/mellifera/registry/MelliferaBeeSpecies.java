package com.joaonf.mellifera.registry;

import java.util.List;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.config.CustomBees;
import com.joaonf.mellifera.bee.BeeTemplate;
import com.joaonf.mellifera.bee.CombProduct;
import com.joaonf.mellifera.bee.EffectAllele;
import com.joaonf.mellifera.bee.FertilityAllele;
import com.joaonf.mellifera.bee.FloweringAllele;
import com.joaonf.mellifera.bee.LifespanAllele;
import com.joaonf.mellifera.bee.SpeedAllele;
import com.joaonf.mellifera.bee.TerritoryAllele;
import com.joaonf.mellifera.bee.ToleranceAllele;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

/// A brand-new registry, not a Vanilla one -- built with the exact DeferredRegister +
/// register(modEventBus) idiom every other registry in this mod already uses.
/// DeferredRegister.makeRegistry() creates the backing Registry<BeeSpecies> inline, so no
/// NewRegistryEvent subscription is needed. Both client and server construct the same
/// entries at the same point in startup, so unlike a datapack registry this needs no sync.
///
/// All 44 of real Forestry's bees, transcribed from BeeDefinition.java (ForestryMC/
/// ForestryMC, mc-1.12 branch, LGPL v3): names, branch grouping, dominance, both body
/// colours, comb products with their real drop chances, and each species' starting allele
/// template -- followed by Binnie's Extra Bees resource line at the bottom of the file,
/// transcribed the same way from the same licence (see the comment there). Six of them -- Forest, Meadows, Marshy, Modest, Tropical and Wintry -- are the
/// "hive-spawn" roots found in the world rather than bred; see MelliferaBlocks' HIVE_* blocks
/// and their worldgen. The rest are reached by breeding (see MelliferaBeeMutations).
///
/// Two deliberate departures, both because this engine's climate model differs from
/// Forestry's: temperature is a real Celsius scale (EnvironmentTemperature) rather than a
/// six-notch enum, so each Forestry temperature class maps to a Celsius band; and there is
/// no humidity axis at all, so Forestry's HUMIDITY_TOLERANCE alleles have no home here and
/// are dropped rather than faked onto the temperature chromosome.
public final class MelliferaBeeSpecies {
    private static final ResourceKey<Registry<BeeSpecies>> REGISTRY_KEY =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Mellifera.MODID, "bee_species"));

    public static final DeferredRegister<BeeSpecies> SPECIES = DeferredRegister.create(REGISTRY_KEY, Mellifera.MODID);

    public static final Registry<BeeSpecies> REGISTRY = SPECIES.makeRegistry(builder -> {});

    // -- Honey branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> FOREST =
        SPECIES.register("forest", () -> new BeeSpecies(
            "bee.mellifera.forest", 0.0F, 30.0F, true, 0x19D0EC, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.HONEY.getId(), 0.30F)),
            BeeTemplate.DEFAULT.fertility(FertilityAllele.HIGH).flowering(FloweringAllele.SLOWER),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> MEADOWS =
        SPECIES.register("meadows", () -> new BeeSpecies(
            "bee.mellifera.meadows", 0.0F, 30.0F, true, 0xEF131E, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.HONEY.getId(), 0.30F)),
            BeeTemplate.DEFAULT.flowering(FloweringAllele.SLOWER),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> COMMON =
        SPECIES.register("common", () -> new BeeSpecies(
            "bee.mellifera.common", 0.0F, 30.0F, true, 0xB2B2B2, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.HONEY.getId(), 0.35F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> CULTIVATED =
        SPECIES.register("cultivated", () -> new BeeSpecies(
            "bee.mellifera.cultivated", 0.0F, 30.0F, true, 0x5734EC, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.HONEY.getId(), 0.40F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.FAST).lifespan(LifespanAllele.SHORTEST),
            false));

    // -- Noble branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> NOBLE =
        SPECIES.register("noble", () -> new BeeSpecies(
            "bee.mellifera.noble", 0.0F, 30.0F, false, 0xEC9A19, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.DRIPPING.getId(), 0.20F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.SHORT).flowering(FloweringAllele.SLOW),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> MAJESTIC =
        SPECIES.register("majestic", () -> new BeeSpecies(
            "bee.mellifera.majestic", 0.0F, 30.0F, true, 0x7F0000, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.DRIPPING.getId(), 0.30F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.NORMAL).lifespan(LifespanAllele.SHORTENED).fertility(FertilityAllele.MAXIMUM),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> IMPERIAL =
        SPECIES.register("imperial", () -> new BeeSpecies(
            "bee.mellifera.imperial", 0.0F, 30.0F, false, 0xA3E02F, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.DRIPPING.getId(), 0.20F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.NORMAL).effect(EffectAllele.BEATIFIC),
            true));

    // -- Industrious branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> DILIGENT =
        SPECIES.register("diligent", () -> new BeeSpecies(
            "bee.mellifera.diligent", 0.0F, 30.0F, false, 0xC219EC, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.STRINGY.getId(), 0.20F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.SHORT).flowering(FloweringAllele.SLOW),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> UNWEARY =
        SPECIES.register("unweary", () -> new BeeSpecies(
            "bee.mellifera.unweary", 0.0F, 30.0F, true, 0x19EC5A, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.STRINGY.getId(), 0.30F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.NORMAL).lifespan(LifespanAllele.SHORTENED),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> INDUSTRIOUS =
        SPECIES.register("industrious", () -> new BeeSpecies(
            "bee.mellifera.industrious", 0.0F, 30.0F, false, 0xFFFFFF, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.STRINGY.getId(), 0.20F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.NORMAL).flowering(FloweringAllele.FAST),
            true));

    // -- Heroic branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> STEADFAST =
        SPECIES.register("steadfast", () -> new BeeSpecies(
            "bee.mellifera.steadfast", 0.0F, 30.0F, false, 0x4D2B15, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.COCOA.getId(), 0.20F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.NORMAL),
            true));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> VALIANT =
        SPECIES.register("valiant", () -> new BeeSpecies(
            "bee.mellifera.valiant", 0.0F, 30.0F, true, 0x626BDD, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.COCOA.getId(), 0.30F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOW).lifespan(LifespanAllele.LONG),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> HEROIC =
        SPECIES.register("heroic", () -> new BeeSpecies(
            "bee.mellifera.heroic", 0.0F, 30.0F, false, 0xB3D5E4, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.COCOA.getId(), 0.40F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOW).lifespan(LifespanAllele.LONG).effect(EffectAllele.HEROIC),
            true));

    // -- Infernal branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> SINISTER =
        SPECIES.register("sinister", () -> new BeeSpecies(
            "bee.mellifera.sinister", 40.0F, 70.0F, false, 0xB3D5E4, 0x9A2323,
            List.of(new CombProduct(MelliferaCombTypes.SIMMERING.getId(), 0.45F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.NORMAL).effect(EffectAllele.AGGRESSIVE),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> FIENDISH =
        SPECIES.register("fiendish", () -> new BeeSpecies(
            "bee.mellifera.fiendish", 40.0F, 70.0F, true, 0xD7BEE5, 0x9A2323,
            List.of(new CombProduct(MelliferaCombTypes.SIMMERING.getId(), 0.55F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.NORMAL).lifespan(LifespanAllele.LONG).effect(EffectAllele.AGGRESSIVE),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> DEMONIC =
        SPECIES.register("demonic", () -> new BeeSpecies(
            "bee.mellifera.demonic", 40.0F, 70.0F, false, 0xF4E400, 0x9A2323,
            List.of(new CombProduct(MelliferaCombTypes.SIMMERING.getId(), 0.45F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.LONGER).effect(EffectAllele.IGNITION),
            true));

    // -- Austere branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> MODEST =
        SPECIES.register("modest", () -> new BeeSpecies(
            "bee.mellifera.modest", 25.0F, 45.0F, false, 0xC5BE86, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.PARCHED.getId(), 0.20F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.SHORT),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> FRUGAL =
        SPECIES.register("frugal", () -> new BeeSpecies(
            "bee.mellifera.frugal", 25.0F, 45.0F, true, 0xE8DCB1, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.PARCHED.getId(), 0.30F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.NORMAL).lifespan(LifespanAllele.LONG),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> AUSTERE =
        SPECIES.register("austere", () -> new BeeSpecies(
            "bee.mellifera.austere", 25.0F, 45.0F, false, 0xFFFAC2, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.PARCHED.getId(), 0.20F), new CombProduct(MelliferaCombTypes.POWDERY.getId(), 0.50F)),
            BeeTemplate.DEFAULT.lifespan(LifespanAllele.LONGER).tolerance(ToleranceAllele.DOWN_2).effect(EffectAllele.CREEPER),
            true));

    // -- Tropical branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> TROPICAL =
        SPECIES.register("tropical", () -> new BeeSpecies(
            "bee.mellifera.tropical", 15.0F, 35.0F, false, 0x378020, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.SILKY.getId(), 0.20F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.SHORT),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> EXOTIC =
        SPECIES.register("exotic", () -> new BeeSpecies(
            "bee.mellifera.exotic", 15.0F, 35.0F, true, 0x304903, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.SILKY.getId(), 0.30F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.NORMAL).lifespan(LifespanAllele.LONG),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> EDENIC =
        SPECIES.register("edenic", () -> new BeeSpecies(
            "bee.mellifera.edenic", 15.0F, 35.0F, false, 0x393D0D, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.SILKY.getId(), 0.20F)),
            BeeTemplate.DEFAULT.lifespan(LifespanAllele.LONGER).tolerance(ToleranceAllele.BOTH_2).effect(EffectAllele.EXPLORATION),
            true));

    // -- End branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> ENDED =
        SPECIES.register("ended", () -> new BeeSpecies(
            "bee.mellifera.ended", -10.0F, 10.0F, false, 0xE079FA, 0xD9DE9E,
            List.of(new CombProduct(MelliferaCombTypes.MYSTERIOUS.getId(), 0.30F)),
            BeeTemplate.DEFAULT,
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> SPECTRAL =
        SPECIES.register("spectral", () -> new BeeSpecies(
            "bee.mellifera.spectral", -10.0F, 10.0F, true, 0xA98BED, 0xD9DE9E,
            List.of(new CombProduct(MelliferaCombTypes.MYSTERIOUS.getId(), 0.50F)),
            BeeTemplate.DEFAULT.effect(EffectAllele.REANIMATION),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> PHANTASMAL =
        SPECIES.register("phantasmal", () -> new BeeSpecies(
            "bee.mellifera.phantasmal", -10.0F, 10.0F, false, 0xCC00FA, 0xD9DE9E,
            List.of(new CombProduct(MelliferaCombTypes.MYSTERIOUS.getId(), 0.40F)),
            BeeTemplate.DEFAULT.lifespan(LifespanAllele.LONGEST).effect(EffectAllele.RESURRECTION),
            true));

    // -- Frozen branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> WINTRY =
        SPECIES.register("wintry", () -> new BeeSpecies(
            "bee.mellifera.wintry", -20.0F, 0.0F, false, 0xA0FFC8, 0xDAF5F3,
            List.of(new CombProduct(MelliferaCombTypes.FROZEN.getId(), 0.30F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.SHORT).fertility(FertilityAllele.MAXIMUM),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> ICY =
        SPECIES.register("icy", () -> new BeeSpecies(
            "bee.mellifera.icy", -20.0F, 0.0F, true, 0xA0FFFF, 0xDAF5F3,
            List.of(new CombProduct(MelliferaCombTypes.FROZEN.getId(), 0.20F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOW).lifespan(LifespanAllele.SHORT),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> GLACIAL =
        SPECIES.register("glacial", () -> new BeeSpecies(
            "bee.mellifera.glacial", -20.0F, 0.0F, false, 0xEFFFFF, 0xDAF5F3,
            List.of(new CombProduct(MelliferaCombTypes.FROZEN.getId(), 0.20F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.SHORT),
            true));

    // -- Vengeful branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> VINDICTIVE =
        SPECIES.register("vindictive", () -> new BeeSpecies(
            "bee.mellifera.vindictive", 0.0F, 30.0F, false, 0xEAFFF3, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.IRRADIATED.getId(), 0.25F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOWER).lifespan(LifespanAllele.NORMAL),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> VENGEFUL =
        SPECIES.register("vengeful", () -> new BeeSpecies(
            "bee.mellifera.vengeful", 0.0F, 30.0F, false, 0xC2DE00, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.IRRADIATED.getId(), 0.40F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.NORMAL).lifespan(LifespanAllele.LONGER),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> AVENGING =
        SPECIES.register("avenging", () -> new BeeSpecies(
            "bee.mellifera.avenging", 0.0F, 30.0F, false, 0xDDFF00, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.IRRADIATED.getId(), 0.40F)),
            BeeTemplate.DEFAULT.lifespan(LifespanAllele.LONGEST),
            true));

    // -- Festive branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> LEPORINE =
        SPECIES.register("leporine", () -> new BeeSpecies(
            "bee.mellifera.leporine", 0.0F, 30.0F, false, 0xFEFF8F, 0x3CD757,
            List.of(new CombProduct(MelliferaCombTypes.SILKY.getId(), 0.30F)),
            BeeTemplate.DEFAULT.effect(EffectAllele.FESTIVE),
            true));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> MERRY =
        SPECIES.register("merry", () -> new BeeSpecies(
            "bee.mellifera.merry", -20.0F, 0.0F, false, 0xFFFFFF, 0xD40000,
            List.of(new CombProduct(MelliferaCombTypes.FROZEN.getId(), 0.30F)),
            BeeTemplate.DEFAULT.effect(EffectAllele.SNOWING),
            true));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> TIPSY =
        SPECIES.register("tipsy", () -> new BeeSpecies(
            "bee.mellifera.tipsy", -20.0F, 0.0F, false, 0xFFFFFF, 0xC219EC,
            List.of(new CombProduct(MelliferaCombTypes.FROZEN.getId(), 0.30F)),
            BeeTemplate.DEFAULT.effect(EffectAllele.DRUNKARD),
            true));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> TRICKY =
        SPECIES.register("tricky", () -> new BeeSpecies(
            "bee.mellifera.tricky", 0.0F, 30.0F, false, 0x49413B, 0xFF6A00,
            List.of(new CombProduct(MelliferaCombTypes.HONEY.getId(), 0.40F)),
            BeeTemplate.DEFAULT,
            true));

    // -- Agrarian branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> RURAL =
        SPECIES.register("rural", () -> new BeeSpecies(
            "bee.mellifera.rural", 0.0F, 30.0F, false, 0xFEFF8F, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.WHEATEN.getId(), 0.20F)),
            BeeTemplate.DEFAULT,
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> FARMERLY =
        SPECIES.register("farmerly", () -> new BeeSpecies(
            "bee.mellifera.farmerly", 0.0F, 30.0F, true, 0xD39728, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.WHEATEN.getId(), 0.27F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOW).territory(TerritoryAllele.LARGE),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> AGRARIAN =
        SPECIES.register("agrarian", () -> new BeeSpecies(
            "bee.mellifera.agrarian", 0.0F, 30.0F, true, 0xFFCA75, 0xFFE047,
            List.of(new CombProduct(MelliferaCombTypes.WHEATEN.getId(), 0.35F)),
            BeeTemplate.DEFAULT.speed(SpeedAllele.SLOW).territory(TerritoryAllele.LARGE).effect(EffectAllele.FERTILE),
            true));

    // -- Boggy branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> MARSHY =
        SPECIES.register("marshy", () -> new BeeSpecies(
            "bee.mellifera.marshy", 0.0F, 30.0F, true, 0x546626, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.MOSSY.getId(), 0.30F)),
            BeeTemplate.DEFAULT,
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> MIRY =
        SPECIES.register("miry", () -> new BeeSpecies(
            "bee.mellifera.miry", 0.0F, 30.0F, true, 0x92AF42, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.MOSSY.getId(), 0.36F)),
            BeeTemplate.DEFAULT.fertility(FertilityAllele.MAXIMUM),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> BOGGY =
        SPECIES.register("boggy", () -> new BeeSpecies(
            "bee.mellifera.boggy", 0.0F, 30.0F, true, 0x698948, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.MOSSY.getId(), 0.39F)),
            BeeTemplate.DEFAULT.territory(TerritoryAllele.LARGER).effect(EffectAllele.MYCOPHILIC),
            false));

    // -- Monastic branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> MONASTIC =
        SPECIES.register("monastic", () -> new BeeSpecies(
            "bee.mellifera.monastic", 0.0F, 30.0F, false, 0x42371C, 0xFFF7B6,
            List.of(new CombProduct(MelliferaCombTypes.WHEATEN.getId(), 0.30F), new CombProduct(MelliferaCombTypes.MELLOW.getId(), 0.10F)),
            BeeTemplate.DEFAULT,
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> SECLUDED =
        SPECIES.register("secluded", () -> new BeeSpecies(
            "bee.mellifera.secluded", 0.0F, 30.0F, true, 0x7B6634, 0xFFF7B6,
            List.of(new CombProduct(MelliferaCombTypes.MELLOW.getId(), 0.20F)),
            BeeTemplate.DEFAULT.flowering(FloweringAllele.FASTEST),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> HERMITIC =
        SPECIES.register("hermitic", () -> new BeeSpecies(
            "bee.mellifera.hermitic", 0.0F, 30.0F, false, 0xFFD46C, 0xFFF7B6,
            List.of(new CombProduct(MelliferaCombTypes.MELLOW.getId(), 0.20F)),
            BeeTemplate.DEFAULT.effect(EffectAllele.REPULSION).flowering(FloweringAllele.FASTEST),
            true));

    // -- Extra Bees resource line ------------------------------------------------------
    // Everything from here down is Binnie's Extra Bees rather than Forestry (ForestryMC/
    // Binnie, master-MC1.12, ExtraBeeDefinition + ExtraBeeBranchDefinition, LGPL v3):
    // names, dominance, both colours, comb products with their real drop chances, and the
    // alleles each branch stamps onto its members.
    //
    // Extra Bees splits its allele template in two -- an ExtraBeeBranchDefinition applied
    // to every member of a branch, then a per-species setAlleles() on top. The rock/metal/
    // gem branches all stamp the same three traits this engine models: FERTILITY LOW,
    // LIFESPAN SHORT and a temperature tolerance (BOTH_1 on Rocky, BOTH_2 on Metallic,
    // Precious, Mineral and Gemstone). ROCK_BRANCH and ORE_BRANCH below are those two
    // stampings, so each species only names what it genuinely adds.
    //
    // The branches also set NEVER_SLEEPS, CAVE_DWELLING, TOLERATES_RAIN and a rock/redstone
    // FLOWER_PROVIDER. This engine has none of those four chromosomes, so they are dropped
    // rather than faked onto a chromosome that means something else -- the same call this
    // file already makes for Forestry's humidity alleles.
    //
    // Only species whose output exists in Vanilla are here. Extra Bees' tin, lead, zinc,
    // titanium, tungstate, nickel, silver, platinum, ruby, sapphire, sodalite, pyrite,
    // bauxite, cinnabar and sphalerite bees all centrifuge into materials no Vanilla
    // Minecraft item corresponds to, and inventing items for them would be making data up
    // rather than transcribing it. See the README's third-party note.

    /// Extra Bees' ROCKY branch stamping.
    private static final BeeTemplate ROCK_BRANCH = BeeTemplate.DEFAULT
        .fertility(FertilityAllele.LOW).lifespan(LifespanAllele.SHORT).tolerance(ToleranceAllele.BOTH_1);

    /// The METALLIC / PRECIOUS / MINERAL / GEMSTONE stamping -- identical to ROCKY except
    /// that a bee which eats ore shrugs off twice the temperature swing.
    private static final BeeTemplate ORE_BRANCH = ROCK_BRANCH.tolerance(ToleranceAllele.BOTH_2);

    // -- Rocky branch: the gateway. No metal or gem bee is reachable except through it. --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> ROCK =
        SPECIES.register("rock", () -> new BeeSpecies(
            "bee.mellifera.rock", 0.0F, 30.0F, true, 0xA8A8A8, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.30F)),
            ROCK_BRANCH,
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> STONE =
        SPECIES.register("stone", () -> new BeeSpecies(
            "bee.mellifera.stone", 0.0F, 30.0F, false, 0x757575, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.30F)),
            ROCK_BRANCH,
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> GRANITE =
        SPECIES.register("granite", () -> new BeeSpecies(
            "bee.mellifera.granite", 0.0F, 30.0F, true, 0x695555, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.30F)),
            ROCK_BRANCH.tolerance(ToleranceAllele.BOTH_2),
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> MINERAL =
        SPECIES.register("mineral", () -> new BeeSpecies(
            "bee.mellifera.mineral", 0.0F, 30.0F, true, 0x6E757D, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.30F)),
            ROCK_BRANCH.tolerance(ToleranceAllele.BOTH_2),
            false));

    /// ADDITION. Coal is in neither Forestry nor Extra Bees; every other species in this file
    /// is transcribed, this one is not.
    ///
    /// It exists because the resource line has no fuel in it. Everything the Rocky branch
    /// leads to is a material you build with, and the one thing an early apiarist actually
    /// runs out of -- something to burn -- is reachable only by going mining, which is the
    /// activity the branch is meant to replace.
    ///
    /// Filed under Rocky and stamped with ROCK_BRANCH rather than ORE_BRANCH: coal is dug out
    /// of plain stone at any depth, so it gets the shallower temperature tolerance the rock
    /// bees have rather than the one upstream reserves for bees that eat ore.
    ///
    /// Specialty chance 0.15 against the metals' 0.05 - 0.06. Coal is worth roughly nothing
    /// per unit, so rationing it at ore rates would produce a bee that is technically a coal
    /// source and practically an ornament.
    public static final DeferredHolder<BeeSpecies, BeeSpecies> COAL =
        SPECIES.register("coal", () -> new BeeSpecies(
            "bee.mellifera.coal", 0.0F, 30.0F, true, 0x2E2E33, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.20F), new CombProduct(MelliferaCombTypes.COAL.getId(), 0.15F)),
            ROCK_BRANCH,
            false));

    // -- Metallic branch: Stone comb at 0.20 plus a trickle of the metal's own comb. Those
    // specialty chances (0.05 - 0.06) are upstream's and are the whole balance of the line.
    public static final DeferredHolder<BeeSpecies, BeeSpecies> COPPER =
        SPECIES.register("copper", () -> new BeeSpecies(
            "bee.mellifera.copper", 0.0F, 30.0F, true, 0xD16308, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.20F), new CombProduct(MelliferaCombTypes.COPPER.getId(), 0.06F)),
            ORE_BRANCH,
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> IRON =
        SPECIES.register("iron", () -> new BeeSpecies(
            "bee.mellifera.iron", 0.0F, 30.0F, false, 0xA87058, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.20F), new CombProduct(MelliferaCombTypes.IRON.getId(), 0.05F)),
            ORE_BRANCH,
            false));

    // -- Precious branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> GOLD =
        SPECIES.register("gold", () -> new BeeSpecies(
            "bee.mellifera.gold", 0.0F, 30.0F, true, 0xE6CC0B, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.20F), new CombProduct(MelliferaCombTypes.GOLD.getId(), 0.02F)),
            ORE_BRANCH,
            false));

    // -- Mineral branch: Lapis is also the sole parent of the whole Gemstone branch. --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> LAPIS =
        SPECIES.register("lapis", () -> new BeeSpecies(
            "bee.mellifera.lapis", 0.0F, 30.0F, true, 0x3D2CDB, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.20F), new CombProduct(MelliferaCombTypes.LAPIS.getId(), 0.05F)),
            ORE_BRANCH,
            false));

    // -- Gemstone branch --
    public static final DeferredHolder<BeeSpecies, BeeSpecies> EMERALD =
        SPECIES.register("emerald", () -> new BeeSpecies(
            "bee.mellifera.emerald", 0.0F, 30.0F, true, 0x1CFF03, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.20F), new CombProduct(MelliferaCombTypes.EMERALD.getId(), 0.04F)),
            ORE_BRANCH,
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> DIAMOND =
        SPECIES.register("diamond", () -> new BeeSpecies(
            "bee.mellifera.diamond", 0.0F, 30.0F, true, 0x7FBDFA, 0x999999,
            List.of(new CombProduct(MelliferaCombTypes.STONE.getId(), 0.20F), new CombProduct(MelliferaCombTypes.DIAMOND.getId(), 0.05F)),
            ORE_BRANCH,
            false));

    // -- Energetic branch: the only resource line that isn't rock-fed. Its Extra Bees
    // branch stamping is nothing but CAVE_DWELLING, a redstone FLOWER_PROVIDER and the
    // Lightning effect -- none of which this engine has a chromosome for -- so these three
    // genuinely do sit on the baseline template.
    public static final DeferredHolder<BeeSpecies, BeeSpecies> EXCITED =
        SPECIES.register("excited", () -> new BeeSpecies(
            "bee.mellifera.excited", 0.0F, 30.0F, true, 0xFF4545, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.REDSTONE.getId(), 0.10F)),
            BeeTemplate.DEFAULT,
            false));

    public static final DeferredHolder<BeeSpecies, BeeSpecies> ENERGETIC =
        SPECIES.register("energetic", () -> new BeeSpecies(
            "bee.mellifera.energetic", 0.0F, 30.0F, false, 0xE835C7, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.REDSTONE.getId(), 0.12F)),
            BeeTemplate.DEFAULT,
            false));

    /// Upstream Ecstatic also yields an IC2ENERGY comb at 0.08. That comb centrifuges into
    /// an IndustrialCraft energy drop and nothing else, so it is dropped here rather than
    /// registered as a comb with no meaningful output -- the same call MelliferaCombTypes
    /// already makes about Forestry's three orphan combs.
    public static final DeferredHolder<BeeSpecies, BeeSpecies> ECSTATIC =
        SPECIES.register("ecstatic", () -> new BeeSpecies(
            "bee.mellifera.ecstatic", 0.0F, 30.0F, true, 0xAF35E8, 0xFFDC16,
            List.of(new CombProduct(MelliferaCombTypes.REDSTONE.getId(), 0.20F)),
            BeeTemplate.DEFAULT,
            true));

    private MelliferaBeeSpecies() {}

    public static void register(IEventBus modEventBus) {
        SPECIES.register(modEventBus);
        modEventBus.addListener(MelliferaBeeSpecies::registerCustom);
    }

    /// Bees from `config/mellifera/custom_bees/` go into this same registry, on the same
    /// event, one listener later than the DeferredRegister above.
    ///
    /// Registration time is the only window there is -- the registry freezes when startup
    /// ends -- and using it is what keeps custom bees invisible to the rest of the mod. The
    /// guide, JEI, the tooltips, the objective panel and the breeding engine all read a
    /// registry, and none of them can tell which entries came from Java and which from a file.
    private static void registerCustom(RegisterEvent event) {
        event.register(REGISTRY_KEY, helper -> {
            CustomBees.species().forEach(helper::register);
            // Inside the same callback, and after the registrations above, because a custom
            // cross may name a custom parent: by this point every bee that will ever exist is
            // in the registry, so the existence check the loader runs can be trusted.
            MelliferaBeeMutations.addCustom(CustomBees.mutations(REGISTRY::containsKey));
        });
    }

    /// Falls back to Forest for an unknown id, the same way OreStickItem falls back to
    /// its own ORES.get(0) for a component that predates a registry change.
    public static BeeSpecies get(Identifier id) {
        BeeSpecies species = REGISTRY.getValue(id);
        return species != null ? species : FOREST.get();
    }

    public static boolean dominant(Identifier id) {
        return get(id).dominant();
    }
}
