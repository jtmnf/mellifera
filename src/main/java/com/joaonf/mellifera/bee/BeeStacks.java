package com.joaonf.mellifera.bee;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.minecraft.core.component.DataComponentGetter;
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
///
/// The readers take a DataComponentGetter rather than an ItemStack, which every stack is.
/// The one caller that is not holding a stack is BeePredicate, which is handed the bare
/// component bag -- and widening the parameter is cheaper than a second copy of the
/// two-components rule this class exists to hold.
public final class BeeStacks {
    private BeeStacks() {}

    /// The genome a stack carries, or null if it is not a bee. For a Queen this is her own
    /// genome, not her mate's.
    public static @Nullable BeeGenome genomeOf(DataComponentGetter stack) {
        BeeGenome genome = stack.get(MelliferaDataComponents.BEE_GENOME.get());
        if (genome != null) {
            return genome;
        }

        QueenGenomeData queen = stack.get(MelliferaDataComponents.QUEEN_GENOME.get());
        return queen == null ? null : queen.own();
    }

    /// The species a stack expresses, or null if it is not a bee.
    public static @Nullable Identifier speciesOf(DataComponentGetter stack) {
        BeeGenome genome = genomeOf(stack);
        return genome == null ? null : genome.species().active();
    }

    /// The genome of the mate a Queen is carrying, or null for anything that is not a mated
    /// Queen. She keeps it from the moment she is mated until she dies, which is what lets
    /// anything asking "what is this hive going to breed?" answer while she is still working
    /// -- by then the drone that fathered the brood has already been consumed out of its
    /// slot, and only she still knows what it was.
    public static @Nullable BeeGenome mateGenomeOf(DataComponentGetter stack) {
        QueenGenomeData queen = stack.get(MelliferaDataComponents.QUEEN_GENOME.get());
        return queen == null ? null : queen.mate();
    }

    /// The species of a Queen's mate, or null if this is not a mated Queen.
    public static @Nullable Identifier mateSpeciesOf(DataComponentGetter stack) {
        BeeGenome mate = mateGenomeOf(stack);
        return mate == null ? null : mate.species().active();
    }

    // -- what the player is allowed to know --------------------------------------------------

    /// Whether this bee's genome has been read yet.
    ///
    /// A bee out of a wild hive, or out of a queen's brood, arrives unread: its tooltip names the
    /// species and nothing else, and what it is quietly carrying is exactly what a player has to
    /// find out rather than be told. See the Beealyzer, which is the only thing that flips this.
    ///
    /// The species is never hidden, which is a departure from Forestry and a deliberate one: this
    /// mod paints every bee its species' colour, on the model, in the hand and on the floor. A
    /// tooltip that refused to name what the player can see would be coy rather than mysterious.
    /// What is worth hiding is the half of a genome that is not on the outside of the bee.
    public static boolean isAnalysed(ItemStack stack) {
        return stack.getOrDefault(MelliferaDataComponents.BEE_ANALYSED.get(), false);
    }

    /// Marks a bee read. Returns whether it did anything, so a caller can charge for the ones it
    /// actually changed and leave an already-read bee alone.
    public static boolean analyse(ItemStack stack) {
        if (genomeOf(stack) == null || isAnalysed(stack)) {
            return false;
        }

        stack.set(MelliferaDataComponents.BEE_ANALYSED.get(), true);
        return true;
    }

    /// A bee that arrives already read: what the creative menu and JEI hand out.
    ///
    /// Those two are showing a player what a species *is*, which is a different job from handing
    /// them one out of a hive. A creative-mode bee whose traits were hidden would make the menu
    /// useless for the thing it is for.
    public static ItemStack analysed(ItemStack stack) {
        stack.set(MelliferaDataComponents.BEE_ANALYSED.get(), true);
        return stack;
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

    /// Every caste a species can be held as, all three already analysed.
    ///
    /// The only caller is JEI, which is cataloguing species rather than handing out bees -- the
    /// same reason the creative menu's entries arrive read. Nothing in the world is built through
    /// here; a bee a player finds comes out of a loot table or out of a queen's brood, and neither
    /// sets the component.
    ///
    ///
    /// Both of this mod's tables -- the mutation tree and the production table -- are stated per
    /// species, never per caste, so anything that answers "what does this bee do?" has to answer the
    /// same for all three. A drone is half of every cross and a Queen is the only caste that actually
    /// produces; showing either of them nothing would be showing them a falsehood.
    public static List<ItemStack> allCastes(Identifier species) {
        return List.of(analysed(princess(species)), analysed(drone(species)), analysed(queen(species)));
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
