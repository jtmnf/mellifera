package com.joaonf.mellifera.item;

import com.joaonf.mellifera.bee.CombType;
import com.joaonf.mellifera.registry.MelliferaCombTypes;
import com.joaonf.mellifera.registry.MelliferaDataComponents;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/// One shared item id for all 14 combs, carrying only COMB_TYPE (an Identifier into
/// MelliferaCombTypes), never a genome -- a comb is an inert output, not a breeding
/// individual.
///
/// Real Forestry names combs after what they are ("Dripping Honeycomb"), not after which
/// bee produced them, and the stack stores exactly that: several species share a branch's
/// comb, and a single species can produce two different ones.
public class HoneyCombItem extends Item {
    public HoneyCombItem(Properties properties) {
        super(properties);
    }

    public static CombType combType(ItemStack stack) {
        Identifier id = stack.get(MelliferaDataComponents.COMB_TYPE.get());
        return id == null ? MelliferaCombTypes.HONEY.get() : MelliferaCombTypes.get(id);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(combType(stack).translationKey());
    }
}
