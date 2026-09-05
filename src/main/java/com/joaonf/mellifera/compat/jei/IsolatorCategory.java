package com.joaonf.mellifera.compat.jei;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.IsolatorBlockEntity;
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
import net.minecraft.world.item.Items;

/// The Isolator's page: a bee and a bottle in, one serum out, eight times over.
///
/// The machine has no datapack recipe of any kind -- what it does is read a genome and bottle it a
/// chromosome at a time (see IsolatorBlockEntity) -- so JEI could never have found it. Without this
/// page the whole genetics half of the mod is undiscoverable from the recipe browser: a player
/// holding a serum has no way to ask where serums come from.
///
/// One row per chromosome, because that is what the machine really does: a bee yields all eight,
/// one bottle and one run each, and the run is the same length whichever gene is up.
public class IsolatorCategory implements IRecipeCategory<GeneticsJeiRecipe> {
    public static final IRecipeType<GeneticsJeiRecipe> TYPE =
        IRecipeType.create(Identifier.fromNamespaceAndPath(Mellifera.MODID, "isolator"), GeneticsJeiRecipe.class);

    private static final int WIDTH = 160;
    private static final int HEIGHT = 42;

    private static final int SLOT_Y = 6;
    private static final int BEE_X = 4;
    private static final int BOTTLE_X = 26;
    private static final int ARROW_X = 50;
    private static final int OUTPUT_X = 68;

    private static final int TEXT_X = 92;
    private static final int TEXT_COLOR = 0xFF202020;

    private static final int TICKS_PER_SECOND = 20;

    private final IDrawable icon;

    public IsolatorCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MelliferaBlocks.ISOLATOR_ITEM.get()));
    }

    @Override
    public IRecipeType<GeneticsJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.mellifera.jei.isolator");
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
        builder.addSlot(RecipeIngredientRole.INPUT, BOTTLE_X, SLOT_Y).add(new ItemStack(Items.GLASS_BOTTLE));
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, SLOT_Y).add(recipe.serum());
    }

    @Override
    public void draw(GeneticsJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;

        graphics.text(font, Component.literal("→"), ARROW_X, SLOT_Y + 4, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.isolator.trait", recipe.trait().label()),
            TEXT_X, SLOT_Y, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.isolator.time",
                IsolatorBlockEntity.PROCESS_TICKS / TICKS_PER_SECOND),
            TEXT_X, SLOT_Y + 12, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.isolator.consumed"),
            TEXT_X, SLOT_Y + 24, TEXT_COLOR, false);
    }
}
