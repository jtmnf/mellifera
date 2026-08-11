package com.joaonf.mellifera.registry;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.BeeProgression;
import com.joaonf.mellifera.bee.BeeTrait;
import com.joaonf.mellifera.bee.QueenGenomeData;
import com.joaonf.mellifera.item.SerumItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// One tab for everything the mod adds, instead of scattering items across Vanilla's own
// tabs.
//
// Every registered BeeSpecies gets a Queen+Princess+Drone triple here (a pure genome of
// that species, same shape BeeGenome.pure() builds for the crafting-recipe results) so all
// 25 species are directly reachable in creative, not just the 6 hive-root ones craftable in
// survival.
//
// The queen used to be left out on the grounds that she is an intermediate state reached by
// mating, not something to hand a player directly. She is listed now because JEI builds its
// ingredient list from the creative menu and appends anything else at the end: keeping her
// out meant the three castes of one species could never appear together, which costs more in
// readability than handing out a queen costs in creative.
public final class MelliferaCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mellifera.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mellifera.main"))
            .icon(() -> MelliferaBlocks.APIARY_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(MelliferaBlocks.APIARY_ITEM.get());
                output.accept(MelliferaBlocks.CENTRIFUGE_ITEM.get());
                output.accept(MelliferaBlocks.ISOLATOR_ITEM.get());
                output.accept(MelliferaBlocks.INFUSER_ITEM.get());
                output.accept(MelliferaBlocks.SQUEEZER_ITEM.get());
                output.accept(MelliferaBlocks.TANK_ITEM.get());
                output.accept(MelliferaFluids.HONEY_BUCKET.get());

                output.accept(MelliferaItems.BEE_GUIDE.get());
                output.accept(MelliferaItems.BEE_LOCATOR.get());
                output.accept(MelliferaItems.CLIMATE_CHART.get());
                output.accept(MelliferaItems.DEBUG_STICK.get());
                output.accept(MelliferaItems.FRAME.get());
                output.accept(MelliferaItems.FRAME_ACCELERATOR.get());
                output.accept(MelliferaItems.FRAME_DOMINANT.get());
                output.accept(MelliferaItems.FRAME_RECESSIVE.get());
                output.accept(MelliferaItems.FRAME_MUTAGENIC.get());
                output.accept(MelliferaItems.FRAME_TERMINATOR.get());
                output.accept(MelliferaItems.FRAME_AUTOMATION.get());
                output.accept(MelliferaItems.FRAME_INSULATION.get());

                output.accept(MelliferaItems.HONEY_DROP.get());
                output.accept(MelliferaItems.HONEYDEW.get());
                output.accept(MelliferaItems.ROYAL_JELLY.get());
                output.accept(MelliferaItems.POLLEN.get());
                output.accept(MelliferaItems.BEESWAX.get());
                output.accept(MelliferaItems.REFRACTORY_WAX.get());
                output.accept(MelliferaItems.PHOSPHOR.get());
                output.accept(MelliferaItems.PROPOLIS.get());
                output.accept(MelliferaItems.SILKY_PROPOLIS.get());
                output.accept(MelliferaItems.PULSATING_PROPOLIS.get());
                output.accept(MelliferaItems.SILK_WISP.get());
                output.accept(MelliferaItems.ASH.get());
                output.accept(MelliferaItems.ICE_SHARD.get());
                output.accept(MelliferaItems.PEAT.get());

                // Ordered by breeding tier, not registration order: the tab reads as the
                // progression, hive roots first and branch tops last. See BeeProgression.
                for (Identifier speciesId : BeeProgression.sorted(MelliferaBeeSpecies.REGISTRY.keySet(), MelliferaBeeMutations.all())) {
                    output.accept(queenStack(speciesId));
                    output.accept(beeStack(MelliferaItems.PRINCESS_BEE.get(), speciesId));
                    output.accept(beeStack(MelliferaItems.DRONE_BEE.get(), speciesId));
                }

                for (Identifier combId : MelliferaCombTypes.REGISTRY.keySet()) {
                    output.accept(combStack(combId));
                }

                // One serum per chromosome rather than one per chromosome *value*: the
                // latter is well over a hundred entries, and these eight already show every
                // trait colour. Any specific allele is a bee away in the Isolator.
                for (BeeTrait trait : BeeTrait.ALL) {
                    output.accept(SerumItem.create(trait, BeeGenome.defaultGenome()));
                }
            })
            .build());

    private MelliferaCreativeTabs() {}

    private static ItemStack beeStack(net.minecraft.world.item.Item item, Identifier speciesId) {
        ItemStack stack = new ItemStack(item);
        stack.set(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.pure(speciesId));
        return stack;
    }

    /// A queen carries a pair of genomes, hers and her mate's. She is listed pure-bred to
    /// herself rather than enumerating 44x44 pairings -- the entry is there so the species
    /// has a queen, not to catalogue every possible marriage.
    private static ItemStack queenStack(Identifier speciesId) {
        BeeGenome genome = BeeGenome.pure(speciesId);
        ItemStack stack = new ItemStack(MelliferaItems.QUEEN_BEE.get());
        stack.set(MelliferaDataComponents.QUEEN_GENOME.get(), new QueenGenomeData(genome, genome));
        return stack;
    }

    private static ItemStack combStack(Identifier combId) {
        ItemStack stack = new ItemStack(MelliferaItems.HONEY_COMB.get());
        stack.set(MelliferaDataComponents.COMB_TYPE.get(), combId);
        return stack;
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
