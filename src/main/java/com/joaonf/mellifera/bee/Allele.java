package com.joaonf.mellifera.bee;

/// A value a Chromosome can carry on either its active or inactive side.
///
/// Dominance is authored per constant, not derived from "which value is better" -- that
/// is what lets a freshly discovered mutation stay recessive instead of showing up on
/// half of its very first offspring.
public interface Allele {
    boolean dominant();
}
