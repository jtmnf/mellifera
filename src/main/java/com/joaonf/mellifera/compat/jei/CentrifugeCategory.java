package com.joaonf.mellifera.compat.jei;

import java.util.List;

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

public class CentrifugeCategory implements IRecipeCategory<CentrifugeJeiRecipe> {
    public static final IRecipeType<CentrifugeJeiRecipe> TYPE =
        IRecipeType.create(Identifier.fromNamespaceAndPath(Mellifera.MODID, "centrifuge"), CentrifugeJeiRecipe.class);

    private static final int WIDTH = 160;
    private static final int HEIGHT = 52;

    /// Chances print *below* their slot. They used to be drawn at the slot's own y range and
    /// were rendered straight under the item icon -- unreadable.
    private static final int SLOT_Y = 6;
    private static final int CHANCE_Y = 26;
    private static final int GRID_X = 50;
    private static final int PITCH = 22;
    private static final int TEXT_COLOR = 0xFF202020;

    private final IDrawable icon;

    public CentrifugeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MelliferaBlocks.CENTRIFUGE_ITEM.get()));
    }

    @Override
    public IRecipeType<CentrifugeJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.mellifera.jei.centrifuge");
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
    public void setRecipe(IRecipeLayoutBuilder builder, CentrifugeJeiRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 4, SLOT_Y).add(recipe.input());

        List<ItemStack> outputs = recipe.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, GRID_X + i * PITCH, SLOT_Y).add(outputs.get(i));
        }
    }

    /// Each output's own chance, drawn under its slot -- the whole point of this machine is
    /// that it is a roll per output, not a fixed set.
    @Override
    public void draw(CentrifugeJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.text(font, Component.literal("→"), 28, SLOT_Y + 5, TEXT_COLOR, false);

        List<Float> chances = recipe.chances();
        for (int i = 0; i < chances.size(); i++) {
            graphics.text(font, Component.literal(Math.round(chances.get(i) * 100) + "%"),
                GRID_X + i * PITCH, CHANCE_Y, TEXT_COLOR, false);
        }

        if (recipe.outputs().isEmpty()) {
            graphics.text(font, Component.translatable("gui.mellifera.jei.no_output"), GRID_X, SLOT_Y + 5, TEXT_COLOR, false);
        }
    }
}
