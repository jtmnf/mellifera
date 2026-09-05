package com.joaonf.mellifera.compat.jei;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.SqueezerBlockEntity;
import com.joaonf.mellifera.registry.MelliferaBlocks;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/// The Squeezer's page: a drop in, millibuckets of honey out.
///
/// The recipe is a pair of constants on the block entity rather than a datapack file, so JEI has no way
/// to find it on its own -- without this page the machine's only rate is documented in Java, where no
/// player looks. It is also the one page in this mod whose output is a fluid, which is why the amount is
/// spelled out beside it: a bucket-shaped icon says nothing about 250 versus 1000.
public class SqueezerCategory implements IRecipeCategory<SqueezerJeiRecipe> {
    public static final IRecipeType<SqueezerJeiRecipe> TYPE =
        IRecipeType.create(Identifier.fromNamespaceAndPath(Mellifera.MODID, "squeezer"), SqueezerJeiRecipe.class);

    private static final int WIDTH = 160;
    private static final int HEIGHT = 42;

    private static final int SLOT_Y = 6;
    private static final int INPUT_X = 4;
    private static final int ARROW_X = 28;
    private static final int OUTPUT_X = 50;

    /// The fluid slot is drawn taller than an item's, which is how every other mod draws a tank in a
    /// recipe page: a fluid is a level, not a stack.
    private static final int FLUID_WIDTH = 16;
    private static final int FLUID_HEIGHT = 26;

    private static final int TEXT_X = 76;
    private static final int TEXT_COLOR = 0xFF202020;

    private final IDrawable icon;

    public SqueezerCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MelliferaBlocks.SQUEEZER_ITEM.get()));
    }

    @Override
    public IRecipeType<SqueezerJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.mellifera.jei.squeezer");
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
    public void setRecipe(IRecipeLayoutBuilder builder, SqueezerJeiRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, SLOT_Y).add(recipe.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, SLOT_Y)
            .setFluidRenderer(SqueezerBlockEntity.BUCKET_MB, false, FLUID_WIDTH, FLUID_HEIGHT)
            .add(NeoForgeTypes.FLUID_STACK, recipe.output());
    }

    @Override
    public void draw(SqueezerJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.text(font, Component.literal("→"), ARROW_X, SLOT_Y + 9, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.squeezer.amount", recipe.output().getAmount()),
            TEXT_X, SLOT_Y + 4, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.squeezer.rate",
                SqueezerBlockEntity.BUCKET_MB / recipe.output().getAmount()),
            TEXT_X, SLOT_Y + 16, TEXT_COLOR, false);
    }
}
