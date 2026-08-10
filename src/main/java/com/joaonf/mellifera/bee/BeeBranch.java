package com.joaonf.mellifera.bee;

import java.util.List;

import net.minecraft.resources.Identifier;

/// A family of related species -- Forestry's own grouping, the one its bees are named after.
///
/// A branch is presentation, not mechanics: nothing in the breeding engine reads it, and two
/// bees in the same branch are no more likely to cross than any other pair. What it carries
/// is the shape a player already has in their head -- Noble/Majestic/Imperial are one line
/// that gets better, and a list that says so is navigable where a list of 57 names is not.
///
/// The members are in the order the line is bred, so the entry bee of a branch is the first
/// row under its header.
///
/// @param translationKey lang key for the branch's own name
/// @param species members, in breeding order
public record BeeBranch(String translationKey, List<Identifier> species) {
    public BeeBranch {
        species = List.copyOf(species);
    }
}
