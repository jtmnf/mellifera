package com.joaonf.mellifera.registry;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.joaonf.mellifera.bee.ItemProduct;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;

/// The apiary output that *isn't* a comb, transcribed from Forestry's `addProduct(...)` and
/// `addSpecialty(...)` calls on each BeeDefinition (LGPL v3).
///
/// Kept apart from BeeSpecies.products (which is comb-only) because these name concrete
/// items rather than CombTypes, and because they are what make a handful of species worth
/// keeping around for their own sake: Imperial is the only source of royal jelly, and
/// Industrious the only source of pollen, in the whole mod.
///
/// Forestry distinguishes "products" from "specialties" (the latter only drop from a bee
/// working its preferred flowers). There is no flower-preference system here, so both are
/// folded into one table at their upstream chances rather than inventing a mechanic to
/// separate them.
public final class MelliferaBeeProducts {
    private static final Map<Identifier, List<ItemProduct>> BY_SPECIES = build();

    private MelliferaBeeProducts() {}

    public static List<ItemProduct> forSpecies(Identifier species) {
        return BY_SPECIES.getOrDefault(species, List.of());
    }

    private static Map<Identifier, List<ItemProduct>> build() {
        return Map.ofEntries(
            entry(MelliferaBeeSpecies.IMPERIAL, out(MelliferaItems.ROYAL_JELLY, 0.15F)),
            entry(MelliferaBeeSpecies.INDUSTRIOUS, out(MelliferaItems.POLLEN, 0.15F)),
            entry(MelliferaBeeSpecies.VALIANT, vanilla(Items.SUGAR, 0.15F)),
            entry(MelliferaBeeSpecies.FIENDISH, out(MelliferaItems.ASH, 0.15F)),
            entry(MelliferaBeeSpecies.DEMONIC, vanilla(Items.GLOWSTONE_DUST, 0.15F)),
            entry(MelliferaBeeSpecies.ICY, out(MelliferaItems.ICE_SHARD, 0.20F)),
            entry(MelliferaBeeSpecies.GLACIAL, out(MelliferaItems.ICE_SHARD, 0.40F)),
            entry(MelliferaBeeSpecies.MERRY, out(MelliferaItems.ICE_SHARD, 0.20F)),
            entry(MelliferaBeeSpecies.TIPSY, out(MelliferaItems.ICE_SHARD, 0.20F)),
            entry(MelliferaBeeSpecies.LEPORINE, vanilla(Items.EGG, 0.10F)),
            entry(MelliferaBeeSpecies.TRICKY,
                vanilla(Items.COOKIE, 0.15F),
                vanilla(Items.SKELETON_SKULL, 0.02F),
                vanilla(Items.ZOMBIE_HEAD, 0.02F),
                vanilla(Items.CREEPER_HEAD, 0.02F)),
            entry(MelliferaBeeSpecies.BOGGY, out(MelliferaItems.PEAT, 0.08F)));
    }

    private static Map.Entry<Identifier, List<ItemProduct>> entry(
        DeferredHolder<?, ?> species, ItemProduct... products) {
        return Map.entry(species.getId(), List.of(products));
    }

    private static ItemProduct out(Supplier<? extends Item> item, float chance) {
        return new ItemProduct(item::get, 1, chance);
    }

    private static ItemProduct vanilla(Item item, float chance) {
        return new ItemProduct(() -> item, 1, chance);
    }
}
