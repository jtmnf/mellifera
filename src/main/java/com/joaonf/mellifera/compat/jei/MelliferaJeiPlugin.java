package com.joaonf.mellifera.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.BeeMutation;
import com.joaonf.mellifera.bee.QueenGenomeData;
import com.joaonf.mellifera.bee.BeeProgression;
import com.joaonf.mellifera.bee.CarpenterRecipe;
import com.joaonf.mellifera.bee.CentrifugeRecipe;
import com.joaonf.mellifera.client.ApiaryScreen;
import com.joaonf.mellifera.client.CarpenterScreen;
import com.joaonf.mellifera.client.CentrifugeScreen;
import com.joaonf.mellifera.client.EngineScreen;
import com.joaonf.mellifera.client.InfuserScreen;
import com.joaonf.mellifera.client.IsolatorScreen;
import com.joaonf.mellifera.client.MachineGeometry;
import com.joaonf.mellifera.client.SqueezerScreen;
import com.joaonf.mellifera.registry.MelliferaBeeMutations;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaBlocks;
import com.joaonf.mellifera.registry.MelliferaCarpenterRecipes;
import com.joaonf.mellifera.registry.MelliferaCentrifugeRecipes;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.item.FrameItem;
import com.joaonf.mellifera.registry.MelliferaItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;

/// JEI integration: the breeding tree and the centrifuge table, both generated from the very
/// tables the simulation runs on (MelliferaBeeMutations, MelliferaCentrifugeRecipes) so they can
/// never disagree with the game.
///
/// Loaded only by JEI's own @JeiPlugin scan, so the mod runs fine without JEI installed --
/// the built-in Apiarist Database covers that case.
@JeiPlugin
public class MelliferaJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(Mellifera.MODID, "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    /// Bees and combs are all one item id apiece, told apart only by a data component.
    /// Without this JEI collapses every species into a single entry -- the ingredient list
    /// showed one Princess and one Drone instead of 44 of each.
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerFromDataComponentTypes(MelliferaItems.PRINCESS_BEE.get(), MelliferaDataComponents.BEE_GENOME.get());
        registration.registerFromDataComponentTypes(MelliferaItems.DRONE_BEE.get(), MelliferaDataComponents.BEE_GENOME.get());
        // A Queen is keyed on her own species and not on her whole QUEEN_GENOME, and that is the
        // difference between the lookup working and not working at all.
        //
        // That component carries her genome *and* her mate's, so keying on it makes a Forest queen
        // mated to a Meadows drone a different ingredient from a Forest queen mated to a Forest drone
        // -- one subtype per pair, 58 x 58 of them, of which the recipes declare a handful. Pressing R
        // on a queen a player actually bred therefore found nothing, every time.
        //
        // Her mate matters enormously to what she breeds, and it is not lost: it is on her tooltip and
        // in the Apiarist Database. What it is not is a different kind of bee.
        registration.registerSubtypeInterpreter(MelliferaItems.QUEEN_BEE.get(), (stack, context) -> {
            QueenGenomeData queen = stack.get(MelliferaDataComponents.QUEEN_GENOME.get());
            return queen == null ? null : queen.own().species().active();
        });
        registration.registerFromDataComponentTypes(MelliferaItems.HONEY_COMB.get(), MelliferaDataComponents.COMB_TYPE.get());
    }

    /// Wild hives: their BlockItems exist only so there is an ItemStack to hand JEI, and they
    /// are deliberately absent from the creative menu because a hive is something you find,
    /// not something you place. This hook is what lets both of those be true at once.
    ///
    /// Queens used to be registered here for the same reason -- they were kept out of the
    /// creative menu, which left the one bee a player actually obtains by playing as the only
    /// item in the mod JEI did not know existed. They are in the menu now (see
    /// MelliferaCreativeTabs), precisely because extras land at the end of the ingredient list:
    /// a queen listed here could never sit next to her own princess and drone.
    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        List<ItemStack> extras = new ArrayList<>();

        for (DeferredItem<BlockItem> hive : MelliferaBlocks.ALL_HIVE_ITEMS) {
            extras.add(new ItemStack(hive.get()));
        }

        registration.addExtraItemStacks(extras);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
            new BeeMutationCategory(guiHelper),
            new ApiaryProductionCategory(guiHelper),
            new CentrifugeCategory(guiHelper),
            new SqueezerCategory(guiHelper),
            new CarpenterCategory(guiHelper),
            new EngineCategory(guiHelper),
            new IsolatorCategory(guiHelper),
            new InfuserCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<BeeMutationRecipe> mutations = new ArrayList<>();
        for (BeeMutation mutation : MelliferaBeeMutations.all()) {
            mutations.add(BeeMutationRecipe.of(mutation));
        }
        registration.addRecipes(BeeMutationCategory.TYPE, mutations);

        List<ApiaryProductionRecipe> production = new ArrayList<>();
        for (Identifier speciesId : BeeProgression.sorted(MelliferaBeeSpecies.REGISTRY.keySet(), MelliferaBeeMutations.all())) {
            production.add(ApiaryProductionRecipe.of(speciesId));
        }
        registration.addRecipes(ApiaryProductionCategory.TYPE, production);

        List<CentrifugeJeiRecipe> spins = new ArrayList<>();
        for (Map.Entry<Identifier, CentrifugeRecipe> entry : MelliferaCentrifugeRecipes.allCombRecipes().entrySet()) {
            ItemStack comb = new ItemStack(MelliferaItems.HONEY_COMB.get());
            comb.set(MelliferaDataComponents.COMB_TYPE.get(), entry.getKey());
            spins.add(CentrifugeJeiRecipe.of(comb, entry.getValue()));
        }
        for (Map.Entry<Item, CentrifugeRecipe> entry : MelliferaCentrifugeRecipes.allItemRecipes().entrySet()) {
            spins.add(CentrifugeJeiRecipe.of(new ItemStack(entry.getKey()), entry.getValue()));
        }
        registration.addRecipes(CentrifugeCategory.TYPE, spins);

        registration.addRecipes(SqueezerCategory.TYPE, SqueezerJeiRecipe.all());

        // The Engine has no recipes at all, only fuels: two rows built from its own constants.
        registration.addRecipes(EngineCategory.TYPE, EngineJeiRecipe.all());

        registerCarpenterRecipes(registration);

        // The genetics bench. Both machines work off a bee's genome rather than off a recipe, so
        // neither has anything JEI could discover; the rows apiece are the chromosomes themselves.
        registration.addRecipes(IsolatorCategory.TYPE, GeneticsJeiRecipe.all());
        registration.addRecipes(InfuserCategory.TYPE, GeneticsJeiRecipe.all());

        registerAnvilRecipes(registration);
    }

    /// Every frame the Carpenter can make, and every frame it can repair.
    ///
    /// Both halves have to be here. The seven special frames stopped being bench crafts when the
    /// machine took them over, so JEI can no longer find them by itself; and repair is not a recipe
    /// in any registry at all, it is a branch inside the block entity. Neither would be discoverable
    /// in game otherwise.
    ///
    /// Built by walking the machine's own table rather than listing rows here, so a frame added to
    /// MelliferaCarpenterRecipes appears on this page without anybody remembering to come back.
    private static void registerCarpenterRecipes(IRecipeRegistration registration) {
        List<CarpenterJeiRecipe> rows = new ArrayList<>();

        for (CarpenterRecipe recipe : MelliferaCarpenterRecipes.all()) {
            rows.add(CarpenterJeiRecipe.of(recipe));
        }

        // One repair row per frame, so pressing U on a worn frame of any kind finds the machine that
        // makes it whole. Same list the anvil rows below walk.
        for (DeferredItem<FrameItem> frame : MelliferaItems.ALL_FRAMES) {
            rows.add(CarpenterJeiRecipe.repair(frame.get()));
        }

        registration.addRecipes(CarpenterCategory.TYPE, rows);
    }

    /// Frame + ender pearl on an anvil makes a frame permanent (see MelliferaAnvilRecipes).
    ///
    /// That is an AnvilUpdateEvent handler, not a datapack recipe, so JEI has no way to
    /// discover it -- without registering it here the mechanic exists in game and is
    /// documented nowhere a player would look.
    private static void registerAnvilRecipes(IRecipeRegistration registration) {
        IVanillaRecipeFactory factory = registration.getVanillaRecipeFactory();
        List<ItemStack> pearl = List.of(new ItemStack(Items.ENDER_PEARL));
        List<IJeiAnvilRecipe> anvil = new ArrayList<>();

        for (DeferredItem<FrameItem> frame : MelliferaItems.ALL_FRAMES) {
            ItemStack plain = new ItemStack(frame.get());

            ItemStack permanent = new ItemStack(frame.get());
            permanent.set(MelliferaDataComponents.FRAME_UNBREAKABLE.get(), true);

            anvil.add(factory.createAnvilRecipe(plain, pearl, List.of(permanent),
                Identifier.fromNamespaceAndPath(Mellifera.MODID, "anvil/" + frame.getId().getPath())));
        }

        registration.addRecipes(RecipeTypes.ANVIL, anvil);
    }

    /// The apiary's objective tab, fed by dragging a bee out of the ingredient list. This is
    /// the only reason the tab is worth having: it lets a player aim at a species they have
    /// never bred and so have no stack of to put anywhere.
    /// Both halves are needed and the order they are described in matters: the extra-areas
    /// handler is what gets JEI's ingredient list off the tab, and without that the ghost
    /// handler below never sees a drop at all, because the list is sitting on top of the
    /// target eating the mouse.
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(ApiaryScreen.class, new ApiaryGuiHandler());
        registration.addGhostIngredientHandler(ApiaryScreen.class, new ApiaryGhostHandler());

        registerMachineRecipeAreas(registration);
    }

    /// Clicking the drive track in the middle of a machine window opens that machine's page.
    /// See MachineRecipeArea for why every one of these machines needs it more than most.
    ///
    /// The Apiary is deliberately not here. Its progress gauge is a two-pixel strip down the
    /// left edge rather than a track in the middle, and it is the one window that already has
    /// another way in: a bee dragged out of the ingredient list onto its objective tab.
    private static void registerMachineRecipeAreas(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(CentrifugeScreen.class,
            MachineRecipeArea.of(MachineGeometry.CENTRIFUGE_TRACK, CentrifugeCategory.TYPE));
        registration.addGuiContainerHandler(SqueezerScreen.class,
            MachineRecipeArea.of(MachineGeometry.SQUEEZER_TRACK, SqueezerCategory.TYPE));
        registration.addGuiContainerHandler(CarpenterScreen.class,
            MachineRecipeArea.of(MachineGeometry.CARPENTER_TRACK, CarpenterCategory.TYPE));
        registration.addGuiContainerHandler(EngineScreen.class,
            MachineRecipeArea.of(MachineGeometry.ENGINE_TRACK, EngineCategory.TYPE));
        registration.addGuiContainerHandler(IsolatorScreen.class,
            MachineRecipeArea.of(MachineGeometry.ISOLATOR_TRACK, IsolatorCategory.TYPE));
        registration.addGuiContainerHandler(InfuserScreen.class,
            MachineRecipeArea.of(MachineGeometry.INFUSER_TRACK, InfuserCategory.TYPE));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(BeeMutationCategory.TYPE, MelliferaBlocks.APIARY_ITEM.get());
        registration.addCraftingStation(ApiaryProductionCategory.TYPE, MelliferaBlocks.APIARY_ITEM.get());
        registration.addCraftingStation(CentrifugeCategory.TYPE, MelliferaBlocks.CENTRIFUGE_ITEM.get());
        registration.addCraftingStation(SqueezerCategory.TYPE, MelliferaBlocks.SQUEEZER_ITEM.get());
        registration.addCraftingStation(CarpenterCategory.TYPE, MelliferaBlocks.CARPENTER_ITEM.get());
        registration.addCraftingStation(EngineCategory.TYPE, MelliferaBlocks.ENGINE_ITEM.get());
        registration.addCraftingStation(IsolatorCategory.TYPE, MelliferaBlocks.ISOLATOR_ITEM.get());
        registration.addCraftingStation(InfuserCategory.TYPE, MelliferaBlocks.INFUSER_ITEM.get());
    }
}
