package com.joaonf.mellifera.registry;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.CarpenterRecipe;
import com.joaonf.mellifera.item.FrameItem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/// What the Carpenter can make, and what each frame costs.
///
/// WHY THESE MOVED OFF THE BENCH. Every special frame used to be a shapeless craft: the plain frame
/// plus one item, in hand, instantly. That made the frames that lift a hive's hard stops -- climate,
/// dark, rain -- as cheap as the ingredient behind them, and it left this mod's own liquid honey
/// with nothing to be for. Both are fixed by the same move: the recipes are here now, they cost
/// honey as well as items, and the honey comes from the Squeezer and the Tank the mod already has.
///
/// The plain frame stays on the bench. It is sticks and wax, it is the blank every recipe below
/// consumes, and making a machine mandatory to assemble it would add a step without adding a
/// decision.
///
/// Held as a Java table rather than datapack JSON, matching MelliferaCentrifugeRecipes and the rest
/// of this mod's non-datapack data.
public final class MelliferaCarpenterRecipes {
    /// Honey, in millibuckets, by what the frame is worth. A bucket is 1000 and a Squeezer turns
    /// four honey drops into one, so the tiers below read as one, two and four drops of work.
    ///
    /// The three at the top are the frames that lift a stop the player has no other answer to --
    /// climate, dark and rain. They are the most expensive on purpose: they are also the only ones
    /// that make a hive run where it otherwise could not run at all.
    private static final int CHEAP_MB = 250;
    private static final int STANDARD_MB = 500;
    private static final int GATE_MB = 1_000;

    /// Ticks of *progress*. A powered Carpenter earns eight a tick (see MachineEnergy.POWERED_SPEED),
    /// so 400 is twenty seconds by hand and two and a half with power -- slow enough by hand that
    /// wiring the machine up is worth doing, which is the same bargain the Centrifuge strikes.
    private static final int STANDARD_TICKS = 400;
    private static final int GATE_TICKS = 600;

    /// Honey to make a worn frame whole again, whatever kind it is.
    ///
    /// A flat rate, and cheaper than any frame here. It has to be: the point of repair is that a
    /// spent Insulation frame is worth carrying back rather than throwing away, and a repair priced
    /// near a new frame would just be a new frame with extra steps.
    public static final int REPAIR_MB = 200;
    public static final int REPAIR_TICKS = 200;

    private static final List<CarpenterRecipe> RECIPES = List.of(
        // The three gates. Ingredients unchanged from the bench recipes they replace, so what a
        // player already knows still holds -- only the honey and the machine are new.
        new CarpenterRecipe(MelliferaItems.FRAME, MelliferaItems.SILK_WISP, GATE_MB, GATE_TICKS,
            MelliferaItems.FRAME_INSULATION),
        new CarpenterRecipe(MelliferaItems.FRAME, MelliferaItems.PHOSPHOR, GATE_MB, GATE_TICKS,
            MelliferaItems.FRAME_LUMINOUS),
        new CarpenterRecipe(MelliferaItems.FRAME, MelliferaItems.REFRACTORY_WAX, GATE_MB, GATE_TICKS,
            MelliferaItems.FRAME_CANOPY),

        // Breeding tools. Cheaper: they change what comes out of a hive that is already working.
        new CarpenterRecipe(MelliferaItems.FRAME, MelliferaItems.ROYAL_JELLY, STANDARD_MB, STANDARD_TICKS,
            MelliferaItems.FRAME_DOMINANT),
        new CarpenterRecipe(MelliferaItems.FRAME, MelliferaItems.POLLEN, CHEAP_MB, STANDARD_TICKS,
            MelliferaItems.FRAME_RECESSIVE),
        new CarpenterRecipe(MelliferaItems.FRAME, MelliferaItems.PULSATING_PROPOLIS, STANDARD_MB, STANDARD_TICKS,
            MelliferaItems.FRAME_MUTAGENIC),

        // Throughput and convenience.
        new CarpenterRecipe(MelliferaItems.FRAME, () -> Items.BLAZE_POWDER, STANDARD_MB, STANDARD_TICKS,
            MelliferaItems.FRAME_ACCELERATOR),
        new CarpenterRecipe(MelliferaItems.FRAME, () -> Items.REDSTONE, STANDARD_MB, STANDARD_TICKS,
            MelliferaItems.FRAME_AUTOMATION),

        // Ash rather than the phosphor its bench recipe used: phosphor is what lights the Luminous
        // frame now, and one ingredient making both "the bright frame" and "the frame that kills the
        // queen" reads as an accident. Ash for the one that ends a line is the better fit anyway.
        new CarpenterRecipe(MelliferaItems.FRAME, MelliferaItems.ASH, CHEAP_MB, STANDARD_TICKS,
            MelliferaItems.FRAME_TERMINATOR));

    private MelliferaCarpenterRecipes() {}

    public static List<CarpenterRecipe> all() {
        return RECIPES;
    }

    /// The recipe these two slots make, or null if they make nothing.
    public static @Nullable CarpenterRecipe find(ItemStack frame, ItemStack ingredient) {
        for (CarpenterRecipe recipe : RECIPES) {
            if (frame.is(recipe.frame().get()) && ingredient.is(recipe.ingredient().get())) {
                return recipe;
            }
        }

        return null;
    }

    /// Every item that may be put in the ingredient slot, so the slot can refuse the rest before a
    /// hopper fills it with something the machine will never use.
    public static boolean isIngredient(ItemStack stack) {
        for (CarpenterRecipe recipe : RECIPES) {
            if (stack.is(recipe.ingredient().get())) {
                return true;
            }
        }

        return false;
    }

    /// Every item that may go in the frame slot. Any frame at all: the blank the recipes above
    /// consume, and every worn one the machine can repair.
    public static boolean isFrame(ItemStack stack) {
        return stack.getItem() instanceof FrameItem;
    }
}
