package com.joaonf.mellifera.registry;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.item.BeeGuideItem;
import com.joaonf.mellifera.item.BeealyzerItem;
import com.joaonf.mellifera.item.BeeLocatorItem;
import com.joaonf.mellifera.item.DebugStickItem;
import com.joaonf.mellifera.item.DroneBeeItem;
import com.joaonf.mellifera.bee.FrameType;
import com.joaonf.mellifera.item.FrameItem;
import com.joaonf.mellifera.item.HoneyCombItem;
import com.joaonf.mellifera.item.PrincessBeeItem;
import com.joaonf.mellifera.item.QueenBeeItem;
import com.joaonf.mellifera.item.ScoopItem;
import com.joaonf.mellifera.item.SerumItem;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
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

    /// Takes a wild hive apart without destroying it.
    ///
    /// Two halves, and they say the same thing twice on purpose because a player meets them at
    /// different moments. What the Scoop *drops* is decided by the six hive loot tables, which
    /// check for it with `minecraft:match_tool` -- that is the rule, and it is enforced there so
    /// there is one place to read it. What the Scoop *is for* is decided here: a hive takes thirty
    /// seconds to break with anything else (see MelliferaBlocks.HIVE_DESTROY_TIME) and comes apart
    /// in a single tick with this, so a player who has not found the Scoop yet is told so by the
    /// block refusing to move, long before they get as far as being told by an empty floor.
    ///
    /// The speed is derived rather than typed: vanilla breaks a block in
    /// `destroyTime * HARVEST_MODIFIER / toolSpeed` ticks, so a speed of exactly that product
    /// finishes it in one. Doubled for margin, and because a number sitting on the edge of a
    /// float comparison is a number waiting to take two ticks on somebody's machine.
    ///
    /// `overrideSpeed` rather than `minesAndDrops`: this rule is about speed only, and claiming
    /// the drops here as well would put a second opinion about them next to the loot tables'.
    ///
    /// Sixteen hives and it is done, and then it breaks the way a tool does -- unlike the
    /// Beealyzer, which fills up and stays in the hand. The difference is what they are: a full
    /// ledger is a thing you keep, and a net dragged through sixteen hives is a thing that has
    /// given out.
    public static final int SCOOP_USES = 16;

    public static final DeferredItem<ScoopItem> SCOOP = ITEMS.registerItem("scoop", p -> new ScoopItem(
        p.durability(SCOOP_USES)
            .component(DataComponents.TOOL, new Tool(
                List.of(Tool.Rule.overrideSpeed(
                    BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK)
                        .getOrThrow(MelliferaBlockTags.HIVES),
                    MelliferaBlocks.HIVE_DESTROY_TIME * MelliferaBlocks.HARVEST_MODIFIER * 2.0F)),
                1.0F,
                // Zero, and the wear is charged in ScoopItem.mineBlock instead. Left to the
                // component, a Tool spends this on *every* block it breaks that is not instabreak,
                // rules or no rules -- which on a sixteen-point tool means a player who cleared
                // some dirt while holding it has quietly spent a quarter of their Scoop.
                0,
                true))
            .component(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("tooltip.mellifera.scoop"))))));

    /// Reads the genome of every bee the player is carrying. See BeealyzerItem.
    ///
    /// Thirty-two readings and it is spent, one point per bee. That is the price of a reading:
    /// there is no second charge to carry, which is why the honey the earlier version wanted per
    /// bee moved into the recipe instead -- a honey drop is a Centrifuge output, so the gate on
    /// reading anything at all is still that a player has a machine spinning combs.
    ///
    /// It glints, like the royal jelly and the pollen: this is a prize of a tool rather than a
    /// stick, and the glint is the only thing that says so at 16 pixels.
    ///
    /// It is NOT enchantable, and that falls out of never calling `enchantable(...)`: the
    /// ENCHANTABLE component is what an enchanting table and an anvil look for, and an item with
    /// durability does not acquire one by having durability. Said out loud here because the
    /// absence of a line is a poor way to record a decision -- Unbreaking on this would make the
    /// wearing-out that is its whole cost into a formality.
    public static final DeferredItem<BeealyzerItem> BEEALYZER = ITEMS.registerItem("beealyzer",
        p -> new BeealyzerItem(p.stacksTo(1).durability(BeealyzerItem.READINGS)
            .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

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
