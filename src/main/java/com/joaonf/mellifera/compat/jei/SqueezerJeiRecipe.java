package com.joaonf.mellifera.compat.jei;

import java.util.List;
import java.util.stream.Stream;

import com.joaonf.mellifera.block.SqueezerBlockEntity;
import com.joaonf.mellifera.registry.MelliferaFluids;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;

/// One row of the Squeezer's page, built from the machine's own constants so the page cannot state a
/// rate the machine does not run.
public record SqueezerJeiRecipe(ItemStack input, FluidStack output) {
    /// One row per item the machine has a price for. Built from SqueezerBlockEntity.yieldOf rather than
    /// from a list of its own, so a page cannot go on quoting an item the press has stopped taking.
    public static List<SqueezerJeiRecipe> all() {
        return Stream.of(MelliferaItems.HONEY_DROP.get(), MelliferaItems.HONEYDEW.get())
            .map(ItemStack::new)
            .filter(stack -> SqueezerBlockEntity.yieldOf(stack) > 0)
            .map(stack -> new SqueezerJeiRecipe(stack,
                new FluidStack(MelliferaFluids.HONEY.get(), SqueezerBlockEntity.yieldOf(stack))))
            .toList();
    }
}
