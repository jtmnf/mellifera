package com.joaonf.mellifera.bee;

import java.util.List;

/// A registered kind of bee: display name, its base climate band (before ToleranceAllele
/// widens it), whether its species allele is dominant, the body colour its items render
/// with, the comb(s) its Apiary yields and how often, and the alleles it is born with.
///
/// Real Forestry gives different branches genuinely different comb items (Dripping, Silky,
/// Frozen...), not just a retinted Honey Comb. Which one a given comb stack *is* travels on
/// the stack itself (MelliferaDataComponents.COMB_TYPE), so one shared item id still covers
/// all 14.
///
/// primaryColor is packed 0xRRGGBB and is the species colour: BeeTextures repaints the
/// abdomen texels of Vanilla's bee sheet with it, keeping each texel's own brightness, so the
/// bands stay dark and the face stays Vanilla. See BeeSpecialRenderer.
///
/// There is deliberately no second colour. There used to be one, `secondaryColor`, for the
/// gold banding of the old two-layer sprites; once the bees moved to Vanilla's model with a
/// repainted texture, nothing read it, and a field nobody reads is a field that lies about
/// what a species controls. The combs keep their own pair -- see CombType, which is a
/// different thing entirely: a comb is coloured by what kind of comb it is.
///
/// hasEffect is Forestry's own `setHasEffect()` flag: a handful of species -- the top of
/// each branch, plus the festive ones -- render their item with the enchantment glint
/// permanently, as a visual marker that this is a prize bee. It is a property of the
/// *species*, not of how a given individual came to exist.
///
/// See CombProduct for the output table -- the per-pulse chances are Forestry's own (0.20
/// for most branch roots, up to 0.55 for Fiendish), and they are the main reason the deeper
/// bees in a branch are worth breeding.
public record BeeSpecies(
    String translationKey,
    float minCelsius,
    float maxCelsius,
    boolean dominant,
    int primaryColor,
    List<CombProduct> products,
    BeeTemplate template,
    boolean hasEffect) {}
