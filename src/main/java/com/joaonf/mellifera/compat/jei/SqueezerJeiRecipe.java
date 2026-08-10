package com.joaonf.mellifera.compat.jei;

import com.joaonf.mellifera.block.SqueezerBlockEntity;
import com.joaonf.mellifera.registry.MelliferaFluids;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;

/// One row of the Squeezer's page, built from the machine's own constants so the page cannot state a
/// rate the machine does not run.
public record SqueezerJeiRecipe(ItemStack input, FluidStack output) {
    public static SqueezerJeiRecipe of() {
        return new SqueezerJeiRecipe(
            new ItemStack(MelliferaItems.HONEY_DROP.get()),
            new FluidStack(MelliferaFluids.HONEY.get(), SqueezerBlockEntity.MB_PER_DROP));
    }
}
