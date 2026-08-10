package com.joaonf.mellifera.bee;

/// What a species' comb actually is -- real Forestry gives different branches genuinely
/// different comb items (Dripping, Silky, Frozen...), not just a retinted Honey Comb.
///
/// Both colours are transcribed from Forestry's EnumHoneyComb (LGPL v3) and both are used.
///
/// Note the layer mapping is the *reverse* of a bee's, and that is upstream's doing, not a
/// mistake: ItemHoneyComb.getColorFromItemstack returns primaryColor for tintIndex 1 and
/// secondaryColor otherwise, so the cell fill (layer0) takes secondaryColor and the cell
/// walls (layer1) take primaryColor. Swapping them washes every comb out -- see the item
/// JSON, whose tint entries are ordered accordingly.
public record CombType(String translationKey, int primaryColor, int secondaryColor) {}
