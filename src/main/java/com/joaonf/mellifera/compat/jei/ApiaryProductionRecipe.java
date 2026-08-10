package com.joaonf.mellifera.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.joaonf.mellifera.bee.BeeStacks;
import com.joaonf.mellifera.bee.CombProduct;
import com.joaonf.mellifera.bee.ItemProduct;
import com.joaonf.mellifera.config.MelliferaOutputConfig;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/// What one species yields in an apiary: its combs and its non-comb products, each with the
/// chance the simulation actually rolls.
///
/// This is the missing link that made combs, royal jelly and pollen look like they came from
/// nowhere -- the centrifuge category told you what a comb *becomes*, but nothing told you
/// where the comb itself came from.
///
/// The input holds all three castes, and the Queen is the honest one of them: she is the caste that
/// actually produces. A princess alone was findable and the other two were not, which left the two
/// bees a player is most often holding unable to answer what they make.
public record ApiaryProductionRecipe(List<ItemStack> bees, List<ItemStack> outputs, List<Float> chances) {

    public static ApiaryProductionRecipe of(Identifier speciesId) {
        List<ItemStack> outputs = new ArrayList<>();
        List<Float> chances = new ArrayList<>();

        for (CombProduct comb : MelliferaOutputConfig.combsOf(speciesId)) {
            ItemStack stack = new ItemStack(MelliferaItems.HONEY_COMB.get());
            stack.set(MelliferaDataComponents.COMB_TYPE.get(), comb.comb());
            outputs.add(stack);
            chances.add(comb.chance());
        }

        for (ItemProduct extra : MelliferaOutputConfig.productsOf(speciesId)) {
            outputs.add(new ItemStack(extra.item().get(), extra.count()));
            chances.add(extra.chance());
        }

        return new ApiaryProductionRecipe(BeeStacks.allCastes(speciesId), List.copyOf(outputs), List.copyOf(chances));
    }
}
