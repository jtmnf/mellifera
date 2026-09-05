package com.joaonf.mellifera.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.BeeTrait;
import com.joaonf.mellifera.item.SerumItem;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.minecraft.world.item.ItemStack;

/// One row of the Isolator's or the Infuser's page: which gene it is working on, and the bee it
/// works on it with.
///
/// The two machines are each other's mirror -- one bottles a gene out of a bee, the other puts one
/// back -- so both pages are built from the same eight rows, one per chromosome, and share this
/// record. What differs is only which side of the arrow the serum is on, and that is the category's
/// business rather than the row's.
///
/// The bee is a *list* of stacks. Any bee with a genome may go in either machine -- princess, drone
/// or queen -- and JEI cycles a multi-stack slot, which says "any of these" better than picking one
/// caste and quietly implying the others do not work.
public record GeneticsJeiRecipe(BeeTrait trait, List<ItemStack> bees, ItemStack serum) {
    public static List<GeneticsJeiRecipe> all() {
        List<GeneticsJeiRecipe> rows = new ArrayList<>();
        for (BeeTrait trait : BeeTrait.ALL) {
            rows.add(new GeneticsJeiRecipe(trait, exampleBees(), SerumItem.create(trait, BeeGenome.defaultGenome())));
        }

        return rows;
    }

    /// A bee of each caste, carrying the default genome.
    ///
    /// Which genome hardly matters here and is deliberately the plain one: the page is about the
    /// machine, not about a species, and a Forest drone in the slot would read as a requirement.
    private static List<ItemStack> exampleBees() {
        ItemStack princess = new ItemStack(MelliferaItems.PRINCESS_BEE.get());
        princess.set(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.defaultGenome());

        ItemStack drone = new ItemStack(MelliferaItems.DRONE_BEE.get());
        drone.set(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.defaultGenome());

        return List.of(princess, drone);
    }
}
