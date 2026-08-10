package com.joaonf.mellifera.item;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.BeeTrait;
import com.joaonf.mellifera.bee.SerumData;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/// One item id for all sixty-odd serums, the same way HoneyCombItem covers every comb: what
/// varies is the SERUM_DATA component, and both the name and the liquid's colour are
/// resolved from it (see SerumTintSource).
///
/// Two serums only stack when their component matches, which is the behaviour wanted here --
/// "Fast" and "Fastest" speed serums must never merge -- and it falls out of vanilla stack
/// merging for free.
public class SerumItem extends Item {
    public SerumItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(BeeTrait trait, BeeGenome genome) {
        ItemStack stack = new ItemStack(MelliferaItems.SERUM.get());
        stack.set(MelliferaDataComponents.SERUM_DATA.get(), SerumData.extract(trait, genome));
        return stack;
    }

    public static @Nullable SerumData data(ItemStack stack) {
        return stack.get(MelliferaDataComponents.SERUM_DATA.get());
    }

    /// "Serum · Speed: Fast". The trait half is the *existing* `tooltip.mellifera.bee.<trait>`
    /// line rather than a new per-trait label key, so a serum can never drift out of sync
    /// with what the bee's own tooltip calls that trait -- which is also why the separator
    /// is a middle dot instead of a colon: the reused key already brings its own.
    @Override
    public Component getName(ItemStack stack) {
        SerumData data = data(stack);
        BeeTrait trait = data == null ? null : data.resolvedTrait();

        return trait == null
            ? Component.translatable("item.mellifera.serum")
            : Component.translatable("item.mellifera.serum.named", trait.describe(data.value()));
    }
}
