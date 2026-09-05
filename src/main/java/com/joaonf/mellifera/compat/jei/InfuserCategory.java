package com.joaonf.mellifera.compat.jei;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.InfuserBlockEntity;
import com.joaonf.mellifera.registry.MelliferaBlocks;
import com.joaonf.mellifera.registry.MelliferaItems;

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

/// The Infuser's page: the Isolator run backwards -- a bee, a serum and some pollen, and the bee
/// comes back out carrying the gene the serum held.
///
/// Registered for the same reason the Isolator's is: nothing about this machine is a recipe in any
/// registry, so JEI has no way to know it exists. A player who has bottled a serum and cannot find
/// what to do with it is the exact case this page answers.
///
/// The bee is on both sides of the arrow on purpose. It is the same bee -- the machine edits it in
/// place rather than consuming it (see InfuserBlockEntity), which is the one thing about this
/// machine that is genuinely surprising and therefore worth showing rather than describing.
public class InfuserCategory implements IRecipeCategory<GeneticsJeiRecipe> {
    public static final IRecipeType<GeneticsJeiRecipe> TYPE =
        IRecipeType.create(Identifier.fromNamespaceAndPath(Mellifera.MODID, "infuser"), GeneticsJeiRecipe.class);

    private static final int WIDTH = 160;
    private static final int HEIGHT = 42;

    private static final int SLOT_Y = 6;
    private static final int BEE_X = 4;
    private static final int SERUM_X = 26;
    private static final int POLLEN_X = 48;
    private static final int ARROW_X = 72;
    private static final int OUTPUT_X = 90;

    private static final int TEXT_X = 112;
    private static final int TEXT_COLOR = 0xFF202020;

    private static final int TICKS_PER_SECOND = 20;

    private final IDrawable icon;

    public InfuserCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MelliferaBlocks.INFUSER_ITEM.get()));
    }

    @Override
    public IRecipeType<GeneticsJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.mellifera.jei.infuser");
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
    public void setRecipe(IRecipeLayoutBuilder builder, GeneticsJeiRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, BEE_X, SLOT_Y).addItemStacks(recipe.bees());
        builder.addSlot(RecipeIngredientRole.INPUT, SERUM_X, SLOT_Y).add(recipe.serum());
        builder.addSlot(RecipeIngredientRole.INPUT, POLLEN_X, SLOT_Y)
            .add(new ItemStack(MelliferaItems.POLLEN.get()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, SLOT_Y).addItemStacks(recipe.bees());
    }

    @Override
    public void draw(GeneticsJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;

        graphics.text(font, Component.literal("→"), ARROW_X, SLOT_Y + 4, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.infuser.trait", recipe.trait().label()),
            TEXT_X, SLOT_Y, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.infuser.time",
                InfuserBlockEntity.PROCESS_TICKS / TICKS_PER_SECOND),
            TEXT_X, SLOT_Y + 12, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.infuser.charges",
                InfuserBlockEntity.CHARGES_PER_POLLEN),
            TEXT_X, SLOT_Y + 24, TEXT_COLOR, false);
    }
}
