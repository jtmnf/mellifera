package com.joaonf.mellifera.registry;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.BeePredicate;

import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/// Ways a predicate in a data file can ask about a component this mod puts on an item.
///
/// Separate from MelliferaDataComponents on purpose: that registers the components themselves,
/// which is what the game stores on a stack, and this registers the questions loot tables,
/// recipes and advancements are allowed to ask about them. The two registries are different and
/// so are the reasons to add to either -- a new component needs no predicate unless something
/// in a data file wants to match on it.
public final class MelliferaDataComponentPredicates {
    public static final DeferredRegister<DataComponentPredicate.Type<?>> PREDICATES =
        DeferredRegister.create(Registries.DATA_COMPONENT_PREDICATE_TYPE, Mellifera.MODID);

    /// `mellifera:bee` -- see BeePredicate. Used by the advancement tree to name a species, a
    /// list of them, or simply "a bee that came out of a mutation".
    public static final DeferredHolder<DataComponentPredicate.Type<?>, DataComponentPredicate.Type<BeePredicate>> BEE =
        PREDICATES.register("bee", () -> BeePredicate.TYPE);

    private MelliferaDataComponentPredicates() {}

    public static void register(IEventBus modEventBus) {
        PREDICATES.register(modEventBus);
    }
}
