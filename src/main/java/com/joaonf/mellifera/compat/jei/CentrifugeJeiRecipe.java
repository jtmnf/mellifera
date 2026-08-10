package com.joaonf.mellifera.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.joaonf.mellifera.bee.CentrifugeRecipe;
import com.joaonf.mellifera.bee.ItemProduct;

import net.minecraft.world.item.ItemStack;

/// A centrifuge recipe flattened for display: the comb that goes in, and each possible
/// output with the chance it actually appears.
public record CentrifugeJeiRecipe(ItemStack input, List<ItemStack> outputs, List<Float> chances) {

    public static CentrifugeJeiRecipe of(ItemStack input, CentrifugeRecipe recipe) {
        List<ItemStack> stacks = new ArrayList<>();
        List<Float> chances = new ArrayList<>();

        for (ItemProduct output : recipe.outputs()) {
            stacks.add(new ItemStack(output.item().get(), output.count()));
            chances.add(output.chance());
        }

        return new CentrifugeJeiRecipe(input, List.copyOf(stacks), List.copyOf(chances));
    }
}
