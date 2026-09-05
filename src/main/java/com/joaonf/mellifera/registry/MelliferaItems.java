package com.joaonf.mellifera.registry;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.item.BeeGuideItem;
import com.joaonf.mellifera.item.BeeLocatorItem;
import com.joaonf.mellifera.item.DebugStickItem;
import com.joaonf.mellifera.item.DroneBeeItem;
import com.joaonf.mellifera.bee.FrameType;
import com.joaonf.mellifera.item.FrameItem;
import com.joaonf.mellifera.item.HoneyCombItem;
import com.joaonf.mellifera.item.PrincessBeeItem;
import com.joaonf.mellifera.item.QueenBeeItem;
import com.joaonf.mellifera.item.SerumItem;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// Items that stand on their own. Block items stay next to their block in MelliferaBlocks, so this
// is only for things with no other home.
// Creative tab placement lives in MelliferaCreativeTabs, not here.
public final class MelliferaItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mellifera.MODID);

    // Apiculture: one item id per role, not per species -- see the individual item classes.
    public static final DeferredItem<PrincessBeeItem> PRINCESS_BEE = ITEMS.registerItem("princess_bee", PrincessBeeItem::new);
    public static final DeferredItem<DroneBeeItem> DRONE_BEE = ITEMS.registerItem("drone_bee", DroneBeeItem::new);
    public static final DeferredItem<QueenBeeItem> QUEEN_BEE = ITEMS.registerItem("queen_bee", QueenBeeItem::new);
    public static final DeferredItem<HoneyCombItem> HONEY_COMB = ITEMS.registerItem("honey_comb", HoneyCombItem::new);
    // One item class, one registration per behaviour -- see FrameType.
    public static final DeferredItem<FrameItem> FRAME =
        ITEMS.registerItem("frame", p -> new FrameItem(FrameType.PRODUCTIVITY, p));
    public static final DeferredItem<FrameItem> FRAME_ACCELERATOR =
        ITEMS.registerItem("frame_accelerator", p -> new FrameItem(FrameType.ACCELERATOR, p));
    public static final DeferredItem<FrameItem> FRAME_DOMINANT =
        ITEMS.registerItem("frame_dominant", p -> new FrameItem(FrameType.DOMINANT, p));
    public static final DeferredItem<FrameItem> FRAME_RECESSIVE =
        ITEMS.registerItem("frame_recessive", p -> new FrameItem(FrameType.RECESSIVE, p));
    public static final DeferredItem<FrameItem> FRAME_MUTAGENIC =
        ITEMS.registerItem("frame_mutagenic", p -> new FrameItem(FrameType.MUTAGENIC, p));
    public static final DeferredItem<FrameItem> FRAME_TERMINATOR =
        ITEMS.registerItem("frame_terminator", p -> new FrameItem(FrameType.TERMINATOR, p));
    public static final DeferredItem<FrameItem> FRAME_AUTOMATION =
        ITEMS.registerItem("frame_automation", p -> new FrameItem(FrameType.AUTOMATION, p));
    public static final DeferredItem<FrameItem> FRAME_INSULATION =
        ITEMS.registerItem("frame_insulation", p -> new FrameItem(FrameType.INSULATION, p));
    public static final DeferredItem<FrameItem> FRAME_LUMINOUS =
        ITEMS.registerItem("frame_luminous", p -> new FrameItem(FrameType.LUMINOUS, p));
    public static final DeferredItem<FrameItem> FRAME_CANOPY =
        ITEMS.registerItem("frame_canopy", p -> new FrameItem(FrameType.CANOPY, p));

    // Centrifuge output. Plain items with no behaviour of their own -- what makes them
    // worth having is the recipes they feed (see MelliferaCentrifugeRecipes). Names and roles
    // are real Forestry's: wax and honey drops are the bulk output of ordinary combs,
    // refractory wax and phosphor come out of Nether combs, propolis out of Industrious
    // ones, and the pulsating variety only out of End combs.
    public static final DeferredItem<Item> HONEY_DROP = ITEMS.registerSimpleItem("honey_drop");
    public static final DeferredItem<Item> HONEYDEW = ITEMS.registerSimpleItem("honeydew");
    // Royal jelly shimmers. It shares the copper-nugget silhouette with honey drop and
    // honeydew, so without the glint the three read as the same item in three colours --
    // and this is the one of them a player is meant to hoard. The glint rides on a data
    // component rather than an isFoil override because there is nothing per-stack to
    // decide here: every royal jelly glints, always.
    public static final DeferredItem<Item> ROYAL_JELLY = ITEMS.registerItem(
        "royal_jelly", p -> new Item(p.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    // Glints, for the same reason royal jelly does: it shares the nether-wart silhouette
    // with nothing, but it is the prize drop of a flowering line and should look like one.
    public static final DeferredItem<Item> POLLEN = ITEMS.registerItem(
        "pollen", p -> new Item(p.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    public static final DeferredItem<Item> BEESWAX = ITEMS.registerSimpleItem("beeswax");
    public static final DeferredItem<Item> REFRACTORY_WAX = ITEMS.registerSimpleItem("refractory_wax");
    public static final DeferredItem<Item> PHOSPHOR = ITEMS.registerSimpleItem("phosphor");
    public static final DeferredItem<Item> PROPOLIS = ITEMS.registerSimpleItem("propolis");
    public static final DeferredItem<Item> SILKY_PROPOLIS = ITEMS.registerSimpleItem("silky_propolis");
    public static final DeferredItem<Item> PULSATING_PROPOLIS = ITEMS.registerSimpleItem("pulsating_propolis");
    public static final DeferredItem<Item> SILK_WISP = ITEMS.registerSimpleItem("silk_wisp");

    // Non-comb apiary output: Fiendish drops ash, the Frozen branch drops ice shards, and
    // Boggy digs up peat. Forestry items with no Vanilla equivalent, so they get their own.
    public static final DeferredItem<Item> ASH = ITEMS.registerSimpleItem("ash");
    public static final DeferredItem<Item> ICE_SHARD = ITEMS.registerSimpleItem("ice_shard");
    public static final DeferredItem<Item> PEAT = ITEMS.registerSimpleItem("peat");

    // Isolator output: one bottled gene each. One item id for all of them, the SERUM_DATA
    // component carries which -- see SerumItem.
    public static final DeferredItem<SerumItem> SERUM = ITEMS.registerItem("serum", SerumItem::new);

    // Points at the nearest wild hive. See BeeLocatorItem.
    public static final DeferredItem<BeeLocatorItem> BEE_LOCATOR = ITEMS.registerItem("bee_locator", BeeLocatorItem::new);

    // Opens the mutation browser. See BeeGuideItem.
    public static final DeferredItem<BeeGuideItem> BEE_GUIDE = ITEMS.registerItem("bee_guide", BeeGuideItem::new);

    // Shows the climate readout while held. No behaviour of its own -- the item is purely the
    // condition the HUD checks, see MelliferaTemperatureHud. The lore rides on a component
    // because that is the whole of what it needs: an item subclass just to override
    // appendHoverText would say nothing this does not.
    public static final DeferredItem<Item> CLIMATE_CHART = ITEMS.registerItem("climate_chart", p -> new Item(
        p.component(DataComponents.LORE, new ItemLore(List.of(
            Component.translatable("tooltip.mellifera.climate_chart"))))));

    // Creative-only testing tool -- no recipe. See DebugStickItem.
    public static final DeferredItem<DebugStickItem> DEBUG_STICK = ITEMS.registerItem("debug_stick", DebugStickItem::new);

    /// Every frame, so anything that enumerates them (the JEI anvil recipes, for one)
    /// cannot silently miss a newly added type.
    public static final java.util.List<DeferredItem<FrameItem>> ALL_FRAMES = java.util.List.of(
        FRAME, FRAME_ACCELERATOR, FRAME_DOMINANT, FRAME_RECESSIVE,
        FRAME_MUTAGENIC, FRAME_TERMINATOR, FRAME_AUTOMATION, FRAME_INSULATION,
        FRAME_LUMINOUS, FRAME_CANOPY);

    private MelliferaItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
