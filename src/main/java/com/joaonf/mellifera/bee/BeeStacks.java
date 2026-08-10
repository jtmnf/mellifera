package com.joaonf.mellifera.bee;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/// Reading a bee out of an item stack, in the one place that knows a caste from a genome.
///
/// The awkward bit this exists to stop being repeated: a Princess and a Drone carry a
/// BeeGenome, but a Queen carries a QueenGenomeData -- her own genome plus her mate's --
/// under a different component entirely. Anything that asks "which bee is this stack?"
/// has to check both, and the copies that did not check the second one silently treated
/// every mated queen as not-a-bee.
public final class BeeStacks {
    private BeeStacks() {}

    /// The genome a stack carries, or null if it is not a bee. For a Queen this is her own
    /// genome, not her mate's.
    public static @Nullable BeeGenome genomeOf(ItemStack stack) {
        BeeGenome genome = stack.get(MelliferaDataComponents.BEE_GENOME.get());
        if (genome != null) {
            return genome;
        }

        QueenGenomeData queen = stack.get(MelliferaDataComponents.QUEEN_GENOME.get());
        return queen == null ? null : queen.own();
    }

    /// The species a stack expresses, or null if it is not a bee.
    public static @Nullable Identifier speciesOf(ItemStack stack) {
        BeeGenome genome = genomeOf(stack);
        return genome == null ? null : genome.species().active();
    }

    /// The genome of the mate a Queen is carrying, or null for anything that is not a mated
    /// Queen. She keeps it from the moment she is mated until she dies, which is what lets
    /// anything asking "what is this hive going to breed?" answer while she is still working
    /// -- by then the drone that fathered the brood has already been consumed out of its
    /// slot, and only she still knows what it was.
    public static @Nullable BeeGenome mateGenomeOf(ItemStack stack) {
        QueenGenomeData queen = stack.get(MelliferaDataComponents.QUEEN_GENOME.get());
        return queen == null ? null : queen.mate();
    }

    /// The species of a Queen's mate, or null if this is not a mated Queen.
    public static @Nullable Identifier mateSpeciesOf(ItemStack stack) {
        BeeGenome mate = mateGenomeOf(stack);
        return mate == null ? null : mate.species().active();
    }

    // -- building them ---------------------------------------------------------------------

    public static ItemStack princess(Identifier species) {
        return carrying(MelliferaItems.PRINCESS_BEE.get(), species);
    }

    public static ItemStack drone(Identifier species) {
        return carrying(MelliferaItems.DRONE_BEE.get(), species);
    }

    /// A Queen of this species, pure-bred to herself. There is no such thing as an unmated Queen, so
    /// something has to be named as the mate, and her own species is the only answer that says nothing
    /// it does not know.
    public static ItemStack queen(Identifier species) {
        BeeGenome genome = BeeGenome.pure(species);
        ItemStack stack = new ItemStack(MelliferaItems.QUEEN_BEE.get());
        stack.set(MelliferaDataComponents.QUEEN_GENOME.get(), new QueenGenomeData(genome, genome));
        return stack;
    }

    /// Every caste a species can be held as.
    ///
    /// Both of this mod's tables -- the mutation tree and the production table -- are stated per
    /// species, never per caste, so anything that answers "what does this bee do?" has to answer the
    /// same for all three. A drone is half of every cross and a Queen is the only caste that actually
    /// produces; showing either of them nothing would be showing them a falsehood.
    public static List<ItemStack> allCastes(Identifier species) {
        return List.of(princess(species), drone(species), queen(species));
    }

    /// What a brood is: one princess and the drones beside her. Never a Queen -- a Queen exists only
    /// by mating, so no cross can hand one back.
    public static List<ItemStack> offspring(Identifier species) {
        return List.of(princess(species), drone(species));
    }

    private static ItemStack carrying(Item item, Identifier species) {
        ItemStack stack = new ItemStack(item);
        stack.set(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.pure(species));
        return stack;
    }
}
