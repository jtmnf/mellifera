package com.joaonf.mellifera.compat.jei;

import java.util.List;

import com.joaonf.mellifera.bee.BeeMutation;
import com.joaonf.mellifera.bee.BeeStacks;
import com.joaonf.mellifera.bee.MutationCondition;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/// One row of the mutation browser, built straight from a BeeMutation.
///
/// Every slot holds all three castes of its species rather than one, and JEI cycles them. This used
/// to be a Princess on the left and a Drone on the right, on the grounds that a princess and a drone
/// is what the apiary takes -- true, and still what the row means, but it made the row findable from
/// only one of the six stacks it is about. BeeMutation.matchesParents accepts the two species in
/// either order, so which side of the cross holds which is the player's choice; and a Queen carries a
/// species as much as a princess does. Asking JEI what a drone breeds into and being told nothing was
/// the display lying about the simulation.
///
/// The result is a princess and drones, which is what a brood is. Never a Queen: no cross produces
/// one, she exists only by mating.
public record BeeMutationRecipe(List<ItemStack> parentA, List<ItemStack> parentB, List<ItemStack> result,
                                Identifier resultSpecies, float chance, MutationCondition condition) {

    public static BeeMutationRecipe of(BeeMutation mutation) {
        return new BeeMutationRecipe(
            BeeStacks.allCastes(mutation.parentA()),
            BeeStacks.allCastes(mutation.parentB()),
            BeeStacks.offspring(mutation.result()),
            mutation.result(),
            mutation.baseChance(),
            mutation.condition());
    }

    public Component chanceText() {
        return Component.translatable("gui.mellifera.jei.chance", Math.round(chance * 100));
    }

    /// Null when the pairing works anywhere -- the category then draws nothing rather than
    /// an empty bracket.
    public Component conditionText() {
        return switch (condition) {
            case MutationCondition.RequiresBiomeTag tag ->
                Component.translatable("gui.mellifera.jei.biome", tag.tag().location().getPath());
            case MutationCondition.RequiresClimate climate ->
                Component.translatable("gui.mellifera.jei.climate",
                    (int) climate.minCelsius() + "°C .. " + (int) climate.maxCelsius() + "°C");
            case MutationCondition.RequiresDateRange date ->
                Component.translatable("gui.mellifera.jei.date",
                    date.startMonth() + "/" + date.startDay() + " - " + date.endMonth() + "/" + date.endDay());
            case MutationCondition.RequiresBlockNearby block ->
                Component.translatable("gui.mellifera.jei.block", block.tag().location().getPath());
            case MutationCondition.RequiresFluidNearby fluid ->
                Component.translatable("gui.mellifera.jei.fluid", fluid.tag().location().getPath());
            case MutationCondition.None ignored -> null;
        };
    }

    public Component resultName() {
        return Component.translatable(MelliferaBeeSpecies.get(resultSpecies).translationKey());
    }
}
