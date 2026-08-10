package com.joaonf.mellifera;

import com.joaonf.mellifera.item.FrameItem;
import com.joaonf.mellifera.registry.MelliferaDataComponents;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/// Frame + ender pearl on an anvil makes the frame permanent.
///
/// An anvil rather than an enchantment because a frame's wear is a component spent once per
/// production pulse, not durability damage per use: Unbreaking's probabilistic saving throw
/// had no meaningful reading there, and Mending -- which shares Unbreaking's supported-items
/// tag -- would have been applicable while doing precisely nothing. A single explicit
/// combination says what it does.
///
/// Anvils are not datapack-driven (they only repair, rename and combine enchantments), so
/// this has to be an event handler; there is no JSON equivalent.
@EventBusSubscriber(modid = Mellifera.MODID)
public final class MelliferaAnvilRecipes {
    /// Levels charged. Deliberately steep: this removes an entire upkeep loop from the
    /// late game, and an ender pearl alone is cheap.
    private static final int XP_COST = 12;

    private MelliferaAnvilRecipes() {}

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack frame = event.getLeft();
        ItemStack pearl = event.getRight();

        if (!(frame.getItem() instanceof FrameItem) || !pearl.is(Items.ENDER_PEARL)) {
            return;
        }

        // Nothing to sell someone who already has one.
        if (FrameItem.neverWears(frame)) {
            return;
        }

        ItemStack result = frame.copyWithCount(1);
        result.set(MelliferaDataComponents.FRAME_UNBREAKABLE.get(), true);

        // Keeps whatever wear it had. Making it permanent should not also repair it -- that
        // would be two rewards for one pearl, and a spent frame is meant to be a loss.
        event.setOutput(result);
        event.setMaterialCost(1);
        event.setXpCost(XP_COST);
    }
}
