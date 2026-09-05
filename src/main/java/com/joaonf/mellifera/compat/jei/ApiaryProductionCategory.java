package com.joaonf.mellifera.compat.jei;

import java.util.List;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.registry.MelliferaBlocks;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
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

public class ApiaryProductionCategory implements IRecipeCategory<ApiaryProductionRecipe> {
    public static final IRecipeType<ApiaryProductionRecipe> TYPE =
        IRecipeType.create(Identifier.fromNamespaceAndPath(Mellifera.MODID, "apiary_production"), ApiaryProductionRecipe.class);

    private static final int WIDTH = 160;
    private static final int HEIGHT = 52;
    private static final int TEXT_COLOR = 0xFF202020;

    /// Slots on one row with their chance printed *below* them, never overlapping: the first
    /// version drew the percentage inside the slot's own 18px box and it was unreadable.
    private static final int SLOT_Y = 6;
    private static final int CHANCE_Y = 26;
    private static final int GRID_X = 50;
    private static final int PITCH = 22;

    private final IDrawable icon;

    public ApiaryProductionCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MelliferaBlocks.APIARY_ITEM.get()));
    }

    @Override
    public IRecipeType<ApiaryProductionRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.mellifera.jei.production");
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
    public void setRecipe(IRecipeLayoutBuilder builder, ApiaryProductionRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 4, SLOT_Y).addItemStacks(recipe.bees());

        List<ItemStack> outputs = recipe.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, GRID_X + i * PITCH, SLOT_Y).add(outputs.get(i));
        }
    }

    @Override
    public void draw(ApiaryProductionRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.text(font, Component.literal("→"), 28, SLOT_Y + 5, TEXT_COLOR, false);

        List<Float> chances = recipe.chances();
        for (int i = 0; i < chances.size(); i++) {
            graphics.text(font, Component.literal(Math.round(chances.get(i) * 100) + "%"),
                GRID_X + i * PITCH, CHANCE_Y, TEXT_COLOR, false);
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, ApiaryProductionRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        tooltip.add(Component.translatable("gui.mellifera.jei.per_cycle"));
    }
}
