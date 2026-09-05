package com.joaonf.mellifera.compat.jei;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.block.CarpenterBlockEntity;
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

/// The Carpenter's page: a frame and an ingredient in, honey spent, a finished frame out.
///
/// WHY THIS PAGE HAS TO EXIST. The seven special frames used to be shapeless bench crafts, which
/// JEI found on its own because they were datapack recipes. They are the machine's now, and the
/// machine's table is Java (see MelliferaCarpenterRecipes) -- so without this page the only way to
/// learn that an Insulation frame costs a silk wisp and a bucket of honey would be to read the
/// source. Every row the machine can run is registered, repairs included.
///
/// The honey is spelled out in millibuckets beside the tank for the same reason the Squeezer's page
/// does it: a fluid icon says nothing about 250 versus 1000.
public class CarpenterCategory implements IRecipeCategory<CarpenterJeiRecipe> {
    public static final IRecipeType<CarpenterJeiRecipe> TYPE =
        IRecipeType.create(Identifier.fromNamespaceAndPath(Mellifera.MODID, "carpenter"), CarpenterJeiRecipe.class);

    private static final int WIDTH = 160;
    private static final int HEIGHT = 46;

    /// The two inputs stacked on the left, the way the machine's own window has them.
    private static final int FRAME_X = 2;
    private static final int FRAME_Y = 2;
    private static final int INGREDIENT_X = 2;
    private static final int INGREDIENT_Y = 24;

    private static final int FLUID_X = 26;
    private static final int FLUID_Y = 4;
    private static final int FLUID_WIDTH = 16;
    private static final int FLUID_HEIGHT = 38;

    private static final int ARROW_X = 50;
    private static final int OUTPUT_X = 66;
    private static final int OUTPUT_Y = 14;

    private static final int TEXT_X = 90;
    private static final int TEXT_COLOR = 0xFF202020;

    /// What the fluid gauge is drawn against, so a full bar means a bucket. The machine's own tank
    /// holds four, and drawing 1000 as a quarter-full sliver would say something true about the tank
    /// and nothing at all about the price.
    private static final int GAUGE_MB = CarpenterBlockEntity.BUCKET_MB;

    private static final int TICKS_PER_SECOND = 20;

    private final IDrawable icon;

    public CarpenterCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MelliferaBlocks.CARPENTER_ITEM.get()));
    }

    @Override
    public IRecipeType<CarpenterJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.mellifera.jei.carpenter");
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
    public void setRecipe(IRecipeLayoutBuilder builder, CarpenterJeiRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, FRAME_X, FRAME_Y).add(recipe.frame());

        // Declared even when empty, so the repair rows show the slot the machine really wants left
        // alone rather than a hole where a slot should be.
        if (!recipe.isRepair()) {
            builder.addSlot(RecipeIngredientRole.INPUT, INGREDIENT_X, INGREDIENT_Y).add(recipe.ingredient());
        }

        builder.addSlot(RecipeIngredientRole.INPUT, FLUID_X, FLUID_Y)
            .setFluidRenderer(GAUGE_MB, false, FLUID_WIDTH, FLUID_HEIGHT)
            .add(NeoForgeTypes.FLUID_STACK, recipe.honey());

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y).add(recipe.result());
    }

    @Override
    public void draw(CarpenterJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;

        graphics.text(font, Component.literal("→"), ARROW_X, OUTPUT_Y + 4, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.carpenter.honey", recipe.honey().getAmount()),
            TEXT_X, OUTPUT_Y - 4, TEXT_COLOR, false);
        graphics.text(font, Component.translatable("gui.mellifera.jei.carpenter.time", recipe.ticks() / TICKS_PER_SECOND),
            TEXT_X, OUTPUT_Y + 8, TEXT_COLOR, false);

        if (recipe.isRepair()) {
            graphics.text(font, Component.translatable("gui.mellifera.jei.carpenter.repair"),
                TEXT_X, OUTPUT_Y + 20, TEXT_COLOR, false);
        }
    }
}
