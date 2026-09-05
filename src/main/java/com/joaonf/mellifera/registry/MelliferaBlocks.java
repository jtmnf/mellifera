package com.joaonf.mellifera.registry;

import java.util.List;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.ApiaryBlock;
import com.joaonf.mellifera.block.CarpenterBlock;
import com.joaonf.mellifera.block.CentrifugeBlock;
import com.joaonf.mellifera.block.SqueezerBlock;
import com.joaonf.mellifera.block.HiveBlock;
import com.joaonf.mellifera.block.InfuserBlock;
import com.joaonf.mellifera.block.IsolatorBlock;
import com.joaonf.mellifera.block.TankBlock;
import com.joaonf.mellifera.item.TankBlockItem;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// Every block this mod adds, all of them apiculture.
// Creative tab placement lives in MelliferaCreativeTabs, not here.
public final class MelliferaBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mellifera.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mellifera.MODID);

    // Wood that has been fitted with a little metal: a notch above planks to break, well below
    // a machine block.
    public static final DeferredBlock<ApiaryBlock> APIARY = BLOCKS.registerBlock(
        "apiary",
        ApiaryBlock::new,
        p -> p.mapColor(MapColor.WOOD)
            .strength(2.5F)
            .sound(SoundType.WOOD));

    public static final DeferredItem<BlockItem> APIARY_ITEM = ITEMS.registerSimpleBlockItem(APIARY);

    // The other half of the apiculture loop: combs in, products out. Stone-ish rather than
    // wooden -- it is a machine, not a hive.
    //
    // Dim light while running, so a working machine is legible across a dark workshop and
    // not only from arm's reach. Half a torch: this is a hot drum behind a window, not a
    // light source anyone should build with.
    public static final DeferredBlock<CentrifugeBlock> CENTRIFUGE = BLOCKS.registerBlock(
        "centrifuge",
        CentrifugeBlock::new,
        p -> p.mapColor(MapColor.METAL)
            .requiresCorrectToolForDrops()
            .strength(3.5F)
            .lightLevel(state -> state.getValue(CentrifugeBlock.WORKING) ? 7 : 0)
            .sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> CENTRIFUGE_ITEM = ITEMS.registerSimpleBlockItem(CENTRIFUGE);

    // Presses honey drops into liquid honey. Same tier and material as the centrifuge -- it is the
    // same kind of machine, and the one that gets this mod's honey into a pipe.
    public static final DeferredBlock<SqueezerBlock> SQUEEZER = BLOCKS.registerBlock(
        "squeezer",
        SqueezerBlock::new,
        p -> p.mapColor(MapColor.METAL)
            .requiresCorrectToolForDrops()
            .strength(3.5F)
            .lightLevel(state -> state.getValue(SqueezerBlock.WORKING) ? 7 : 0)
            .sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> SQUEEZER_ITEM = ITEMS.registerSimpleBlockItem(SQUEEZER);

    // Sixteen buckets of anything, behind glass. Same tier and material as the machines it stands
    // next to -- it is the same workshop -- but noOcclusion, which is not decoration: a block whose
    // texture has a hole in it still hides its neighbours' faces unless it says otherwise, and the
    // fluid TankRenderer draws inside it is one of the things that would be hidden.
    public static final DeferredBlock<TankBlock> TANK = BLOCKS.registerBlock(
        "tank",
        TankBlock::new,
        p -> p.mapColor(MapColor.METAL)
            .requiresCorrectToolForDrops()
            .strength(3.5F)
            .noOcclusion()
            .sound(SoundType.STONE));

    // Not registerSimpleBlockItem: a tank keeps what is in it when it is broken, and TankBlockItem
    // is what says so on the tooltip. See TankBlockEntity.collectImplicitComponents.
    public static final DeferredItem<TankBlockItem> TANK_ITEM = ITEMS.registerItem(
        "tank",
        p -> new TankBlockItem(TANK.get(), p),
        p -> p.useBlockDescriptionPrefix());

    // Works honey into frames. Same tier and material as the rest -- it is the fifth machine on the
    // same bench -- and the same dim light while running, so a working row of them reads across a
    // dark workshop.
    public static final DeferredBlock<CarpenterBlock> CARPENTER = BLOCKS.registerBlock(
        "carpenter",
        CarpenterBlock::new,
        p -> p.mapColor(MapColor.METAL)
            .requiresCorrectToolForDrops()
            .strength(3.5F)
            .lightLevel(state -> state.getValue(CarpenterBlock.WORKING) ? 7 : 0)
            .sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> CARPENTER_ITEM = ITEMS.registerSimpleBlockItem(CARPENTER);

    // Genetics rather than production: pulls a bee's traits out one at a time into serums.
    // Same tier and material as the centrifuge -- they are the same kind of machine.
    public static final DeferredBlock<IsolatorBlock> ISOLATOR = BLOCKS.registerBlock(
        "isolator",
        IsolatorBlock::new,
        p -> p.mapColor(MapColor.METAL)
            .requiresCorrectToolForDrops()
            .strength(3.5F)
            .lightLevel(state -> state.getValue(IsolatorBlock.WORKING) ? 7 : 0)
            .sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> ISOLATOR_ITEM = ITEMS.registerSimpleBlockItem(ISOLATOR);

    // The isolator run backwards: serums back into a bee. Same tier and material as the two
    // machines it works with -- they are one bench, not three unrelated devices.
    public static final DeferredBlock<InfuserBlock> INFUSER = BLOCKS.registerBlock(
        "infuser",
        InfuserBlock::new,
        p -> p.mapColor(MapColor.METAL)
            .requiresCorrectToolForDrops()
            .strength(3.5F)
            .lightLevel(state -> state.getValue(InfuserBlock.WORKING) ? 7 : 0)
            .sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> INFUSER_ITEM = ITEMS.registerSimpleBlockItem(INFUSER);

    // Wild hives: the "find naturally" counterpart to the apiary recipes, one per
    // hive-root species (see MelliferaBeeSpecies and the matching placed_feature/biome_modifier
    // pairs under data/mellifera/worldgen).
    //
    // A hive still isn't meant to be picked up and placed -- no loot table drops the block,
    // and none of these items is in a creative tab. The BlockItem exists purely so the hive
    // is an ItemStack, because that is the only thing JEI can hold: without one, the six
    // blocks a player is actually sent out to look for were the one part of the mod JEI
    // could not show, bookmark, or answer R and U about. See MelliferaJeiPlugin, which puts
    // them in the ingredient list without putting them in the creative menu.
    public static final DeferredBlock<HiveBlock> HIVE_FOREST = registerHive("hive_forest", MelliferaBeeSpecies.FOREST.getId());
    public static final DeferredBlock<HiveBlock> HIVE_MEADOWS = registerHive("hive_meadows", MelliferaBeeSpecies.MEADOWS.getId());
    public static final DeferredBlock<HiveBlock> HIVE_MARSHY = registerHive("hive_marshy", MelliferaBeeSpecies.MARSHY.getId());
    public static final DeferredBlock<HiveBlock> HIVE_MODEST = registerHive("hive_modest", MelliferaBeeSpecies.MODEST.getId());
    public static final DeferredBlock<HiveBlock> HIVE_TROPICAL = registerHive("hive_tropical", MelliferaBeeSpecies.TROPICAL.getId());
    public static final DeferredBlock<HiveBlock> HIVE_WINTRY = registerHive("hive_wintry", MelliferaBeeSpecies.WINTRY.getId());

    /// Every wild hive, so anything enumerating them (the JEI ingredient list, for one)
    /// cannot silently miss one that gets added later.
    public static final List<DeferredItem<BlockItem>> ALL_HIVE_ITEMS = List.of(
        hiveItem(HIVE_FOREST), hiveItem(HIVE_MEADOWS), hiveItem(HIVE_MARSHY),
        hiveItem(HIVE_MODEST), hiveItem(HIVE_TROPICAL), hiveItem(HIVE_WINTRY));

    private static DeferredBlock<HiveBlock> registerHive(String name, Identifier species) {
        return BLOCKS.registerBlock(
            name,
            p -> new HiveBlock(species, p),
            p -> p.mapColor(MapColor.WOOD)
                .noCollision()
                .instabreak()
                .sound(SoundType.WOOD));
    }

    private static DeferredItem<BlockItem> hiveItem(DeferredBlock<HiveBlock> hive) {
        return ITEMS.registerSimpleBlockItem(hive);
    }

    private MelliferaBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
