package com.joaonf.mellifera.compat.jei;

import java.util.List;

import com.joaonf.mellifera.block.EngineBlockEntity;
import com.joaonf.mellifera.registry.MelliferaFluids;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;

/// One fuel the Engine burns, and what it is worth.
///
/// Built from the block entity's own constants, so the page cannot quote a figure the engine does
/// not honour. Either the item or the fluid is empty: a row is one fuel, because the machine burns
/// one at a time.
///
/// @param fuel   the item burned, or empty for the fluid row
/// @param honey  the fluid burned, or empty for the item row
/// @param ticks  how long that much fuel burns for
public record EngineJeiRecipe(ItemStack fuel, FluidStack honey, int ticks) {
    /// Total Forge Energy this row is worth, which is the number a player is actually comparing.
    public int energy() {
        return ticks * EngineBlockEntity.FE_PER_TICK;
    }

    public boolean isFluid() {
        return fuel.isEmpty();
    }

    public static List<EngineJeiRecipe> all() {
        return List.of(
            new EngineJeiRecipe(
                ItemStack.EMPTY,
                new FluidStack(MelliferaFluids.HONEY.get(), EngineBlockEntity.HONEY_DRAUGHT_MB),
                EngineBlockEntity.HONEY_DRAUGHT_TICKS),
            new EngineJeiRecipe(
                new ItemStack(MelliferaItems.PEAT.get()),
                FluidStack.EMPTY,
                EngineBlockEntity.PEAT_TICKS));
    }
}
