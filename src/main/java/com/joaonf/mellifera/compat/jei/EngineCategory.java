package com.joaonf.mellifera.compat.jei;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.EngineBlockEntity;
import com.joaonf.mellifera.registry.MelliferaBlocks;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/// The Engine's page: what it burns and what that is worth in Forge Energy.
///
/// The one page in the mod with no output ingredient at all, because what this machine makes is
/// power and power is not an item JEI can draw. So the right-hand side is text: the total a fuel is
/// worth and the rate it comes out at, which are the two numbers a player is comparing when they
/// decide whether to plumb honey in or carry peat home.
public class EngineCategory implements IRecipeCategory<EngineJeiRecipe> {
    public static final RecipeType<EngineJeiRecipe> TYPE =
        new RecipeType<>(Identifier.fromNamespaceAndPath(Mellifera.MODID, "engine"), EngineJeiRecipe.class);

    private static final int WIDTH = 160;
    private static final int HEIGHT = 42;

    private static final int SLOT_Y = 6;
    private static final int INPUT_X = 4;
    private static final int ARROW_X = 28;

    /// A fluid slot is drawn taller than an item's, the same as the Squeezer's page: a fluid is a
    /// level, not a stack.
    private static final int FLUID_WIDTH = 16;
    private static final int FLUID_HEIGHT = 26;

    private static final int TEXT_X = 50;
    private static final int TEXT_COLOR = 0xFF202020;

    private static final int TICKS_PER_SECOND = 20;

    private final IDrawable icon;

    public EngineCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MelliferaBlocks.ENGINE_ITEM.get()));
    }

    @Override
    public IRecipeType<EngineJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.mellifera.jei.engine");
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
    public void setRecipe(IRecipeLayoutBuilder builder, EngineJeiRecipe recipe, IFocusGroup focuses) {
        if (recipe.isFluid()) {
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, SLOT_Y)
                .setFluidRenderer(EngineBlockEntity.HONEY_DRAUGHT_MB, false, FLUID_WIDTH, FLUID_HEIGHT)
                .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.honey());
        } else {
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, SLOT_Y).addItemStack(recipe.fuel());
        }
    }

    @Override
    public void draw(EngineJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.text(font, Component.literal("→"), ARROW_X, SLOT_Y + 9, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.engine.output", recipe.energy()),
            TEXT_X, SLOT_Y + 4, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.engine.rate",
                EngineBlockEntity.FE_PER_TICK, recipe.ticks() / TICKS_PER_SECOND),
            TEXT_X, SLOT_Y + 16, TEXT_COLOR, false);
    }
}
