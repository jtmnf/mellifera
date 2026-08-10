package com.joaonf.mellifera.bee;

import java.util.List;

/// A registered kind of bee: display name, its base climate band (before ToleranceAllele
/// widens it), whether its species allele is dominant, the two body colours its items
/// render with, the comb(s) its Apiary yields and how often, and the alleles it is born
/// with.
///
/// Real Forestry gives different branches genuinely different comb items (Dripping, Silky,
/// Frozen...), not just a retinted Honey Comb. Which one a given comb stack *is* travels on
/// the stack itself (MelliferaDataComponents.COMB_TYPE), so one shared item id still covers
/// all 14.
///
/// primaryColor and secondaryColor are packed 0xRRGGBB, multiplied over the bee's sprite
/// layers the same way Vanilla tints leaves/grass over a white mask.
///
/// Which layer gets which colour is not obvious and is worth stating, because getting it
/// wrong makes every bee look nearly identical: matching Forestry's own item model, the
/// *outline* sprite (a solid white silhouette) takes primaryColor and is what actually
/// gives a bee its species colour; `body1` takes secondaryColor and supplies the gold
/// banding; and the caste sprite (`body2`, the crown/wings detail) is drawn on top
/// untinted. See BeeTintSource and the model JSONs.
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
    int secondaryColor,
    List<CombProduct> products,
    BeeTemplate template,
    boolean hasEffect) {}
