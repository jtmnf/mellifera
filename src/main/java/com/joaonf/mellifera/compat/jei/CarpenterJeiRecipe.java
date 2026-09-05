package com.joaonf.mellifera.compat.jei;

import com.joaonf.mellifera.bee.CarpenterRecipe;
import com.joaonf.mellifera.item.FrameItem;
import com.joaonf.mellifera.registry.MelliferaCarpenterRecipes;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaFluids;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/// One row of the Carpenter's page, built from the machine's own table so the page cannot state a
/// price the machine does not charge.
///
/// `ingredient` is empty for the repair rows. That is the machine's own rule rather than a display
/// trick -- a frame with nothing beside it is what a repair *is* (see CarpenterBlockEntity.job) --
/// so the empty slot on the page is showing the recipe accurately.
public record CarpenterJeiRecipe(ItemStack frame, ItemStack ingredient, FluidStack honey, ItemStack result, int ticks) {
    /// How worn the frame on a repair row is drawn. Any figure below full would do; a third left
    /// puts the durability bar deep in the red, which is what a frame worth carrying back looks
    /// like.
    private static final float SHOWN_WEAR = 0.35F;

    public static CarpenterJeiRecipe of(CarpenterRecipe recipe) {
        return new CarpenterJeiRecipe(
            new ItemStack(recipe.frame().get()),
            new ItemStack(recipe.ingredient().get()),
            new FluidStack(MelliferaFluids.HONEY.get(), recipe.honeyMb()),
            new ItemStack(recipe.result().get()),
            recipe.ticks());
    }

    /// The repair row for one kind of frame: worn in, fresh out, honey in between.
    public static CarpenterJeiRecipe repair(Item frame) {
        ItemStack worn = new ItemStack(frame);
        worn.set(MelliferaDataComponents.FRAME_WEAR.get(), SHOWN_WEAR);

        ItemStack fresh = new ItemStack(frame);
        fresh.set(MelliferaDataComponents.FRAME_WEAR.get(), FrameItem.FRESH_WEAR);

        return new CarpenterJeiRecipe(
            worn,
            ItemStack.EMPTY,
            new FluidStack(MelliferaFluids.HONEY.get(), MelliferaCarpenterRecipes.repairMb(fresh)),
            fresh,
            MelliferaCarpenterRecipes.REPAIR_TICKS);
    }

    public boolean isRepair() {
        return ingredient.isEmpty();
    }
}
