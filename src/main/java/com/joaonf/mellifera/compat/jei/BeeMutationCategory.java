package com.joaonf.mellifera.compat.jei;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.registry.MelliferaBlocks;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class BeeMutationCategory implements IRecipeCategory<BeeMutationRecipe> {
    public static final IRecipeType<BeeMutationRecipe> TYPE =
        IRecipeType.create(Identifier.fromNamespaceAndPath(Mellifera.MODID, "bee_mutation"), BeeMutationRecipe.class);

    private static final int WIDTH = 160;

    /// Room for both rows below the slots to wrap to two lines. A biome or a block name is
    /// whatever the pack that added it called it, so neither row has a length this can assume.
    private static final int HEIGHT = 78;

    /// The full page, less a margin each side: these two rows have the width to themselves.
    private static final int TEXT_X = 4;
    private static final int TEXT_WIDTH = WIDTH - TEXT_X * 2;
    private static final int TEXT_Y = 30;

    /// Near-black, no drop shadow: JEI draws recipes on a light panel, and the mid-grey this
    /// used to use was effectively invisible against it.

    private final IDrawable icon;

    public BeeMutationCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MelliferaBlocks.APIARY_ITEM.get()));
    }

    @Override
    public IRecipeType<BeeMutationRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.mellifera.jei.mutation");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BeeMutationRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 4, 6).addItemStacks(recipe.parentA());
        builder.addSlot(RecipeIngredientRole.INPUT, 30, 6).addItemStacks(recipe.parentB());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 80, 6).addItemStacks(recipe.result());
    }

    /// Also exposed as a tooltip: if the drawn rows are ever clipped by a resource pack or a
    /// narrow layout, the numbers are still reachable by hovering.
    @Override
    public void getTooltip(mezz.jei.api.gui.builder.ITooltipBuilder tooltip, BeeMutationRecipe recipe,
                           IRecipeSlotsView slots, double mouseX, double mouseY) {
        tooltip.add(recipe.chanceText());
        Component condition = recipe.conditionText();
        if (condition != null) {
            tooltip.add(condition);
        }
    }

    @Override
    public void draw(BeeMutationRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;

        graphics.text(font, Component.literal("+"), 24, 10, RecipeText.COLOR, false);
        graphics.text(font, Component.literal("→"), 60, 10, RecipeText.COLOR, false);

        // Chance and condition on their own rows under the slots, where there is room for
        // them -- squeezed alongside the result slot they ran off the edge of the category.
        // Wrapped as well as moved: "Nearby: <some pack's block>" has no length worth assuming.
        int y = RecipeText.draw(graphics, font, recipe.chanceText(), TEXT_X, TEXT_Y, TEXT_WIDTH);

        Component condition = recipe.conditionText();
        if (condition != null) {
            RecipeText.draw(graphics, font, condition, TEXT_X, y + 4, TEXT_WIDTH);
        }
    }
}
