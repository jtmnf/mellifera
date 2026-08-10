package com.joaonf.mellifera.compat.jei;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.bee.BeeStacks;
import com.joaonf.mellifera.bee.MutationObjective;
import com.joaonf.mellifera.client.ApiaryScreen;
import com.joaonf.mellifera.client.ObjectivePanel;
import com.joaonf.mellifera.registry.MelliferaBeeMutations;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/// Lets a bee be dragged out of JEI's ingredient list onto the apiary's objective tab.
///
/// "Ghost" is the right model here and not a compromise: the objective is a species id the
/// apiary remembers, so the drag has to work with bees the player does not own and has
/// possibly never bred -- which is exactly the case where telling the apiary what to aim
/// for is worth anything at all. A real slot could only accept a bee already in hand.
///
/// Only bee stacks are offered a target. Returning an empty target list for anything else
/// is what makes JEI show the "no" cursor over the tab while dragging a stick.
public class ApiaryGhostHandler implements IGhostIngredientHandler<ApiaryScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(ApiaryScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        ObjectivePanel panel = screen.objectivePanel();
        if (panel == null || speciesOf(ingredient) == null) {
            return List.of();
        }

        return List.of(new Target<>() {
            @Override
            public Rect2i getArea() {
                // Read per call rather than captured: the panel slides, and a stale
                // rectangle would leave the highlight sitting where the tab used to be.
                return panel.dropTarget();
            }

            @Override
            public void accept(I dropped) {
                BeeSpecies species = speciesOf(dropped);
                if (species != null) {
                    screen.setObjective(species);
                }
            }
        });
    }

    @Override
    public void onComplete() {}

    /// Shift-clicking a bee in the ingredient list sets the objective too, which is the
    /// quicker gesture once a player knows the tab is there.
    @Override
    public <I> boolean quickMove(ApiaryScreen screen, ITypedIngredient<I> ingredient) {
        BeeSpecies species = speciesOf(ingredient);
        if (species == null) {
            return false;
        }

        screen.setObjective(species);
        return true;
    }

    private static <I> @Nullable BeeSpecies speciesOf(ITypedIngredient<I> ingredient) {
        return ingredient.getItemStack().map(ApiaryGhostHandler::speciesOf).orElse(null);
    }

    /// JEI hands the dropped ingredient back as its raw type, which for an item ingredient
    /// is the ItemStack itself.
    private static @Nullable BeeSpecies speciesOf(Object ingredient) {
        return ingredient instanceof ItemStack stack ? speciesOf(stack) : null;
    }

    /// The species a Princess/Drone/Queen stack carries, or null for anything that is not a
    /// bee. Reads the genome component directly rather than checking the item, so it
    /// accepts all three castes without naming them: only a bee has one.
    ///
    /// Also null for a bee nothing breeds into -- the hive roots and the loot-only species.
    /// Refusing them here is what makes JEI show the no-drop cursor over the tab instead of
    /// accepting an objective that could never be worked towards.
    private static @Nullable BeeSpecies speciesOf(ItemStack stack) {
        Identifier species = BeeStacks.speciesOf(stack);
        if (species == null || !MutationObjective.canTarget(species, MelliferaBeeMutations.all())) {
            return null;
        }

        return MelliferaBeeSpecies.REGISTRY.getValue(species);
    }
}
