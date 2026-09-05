package com.joaonf.mellifera.bee;

import java.util.function.Supplier;

import net.minecraft.world.item.Item;

/// One thing the Carpenter can make: a frame, an ingredient, some honey and some time.
///
/// The frame is always an input as well as an output. Every special frame is the plain one with
/// something worked into it -- that was true when they were bench recipes and it stays true here --
/// so the machine never conjures a frame, it only finishes one.
///
/// Suppliers for the items, because this table is static data built while MelliferaItems is still
/// registering. Same reason MelliferaCentrifugeRecipes uses them.
///
/// @param frame      what has to be in the frame slot
/// @param ingredient what has to be in the ingredient slot
/// @param honeyMb    millibuckets of liquid honey consumed, all at once when the job completes
/// @param ticks      ticks of *progress*, so a powered Carpenter finishes eight times sooner
/// @param result     what comes out, fresh
public record CarpenterRecipe(
    Supplier<? extends Item> frame,
    Supplier<? extends Item> ingredient,
    int honeyMb,
    int ticks,
    Supplier<? extends Item> result) {}
