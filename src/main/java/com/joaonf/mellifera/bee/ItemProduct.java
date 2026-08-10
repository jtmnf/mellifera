package com.joaonf.mellifera.bee;

import java.util.function.Supplier;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/// A chance-rolled concrete item output, used both for a bee's non-comb apiary products
/// (see MelliferaBeeProducts) and for centrifuge results (see CentrifugeRecipe).
///
/// Holds a Supplier rather than an Item because both tables are static data declared while
/// the item registry is still being populated.
public record ItemProduct(Supplier<Item> item, int count, float chance) {
    public ItemStack roll(RandomSource random) {
        return random.nextFloat() < chance ? new ItemStack(item.get(), count) : ItemStack.EMPTY;
    }
}
