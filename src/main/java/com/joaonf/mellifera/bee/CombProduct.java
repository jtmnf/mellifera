package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/// One entry of a species' output table: a CombType id (into MelliferaCombTypes) and the
/// per-pulse chance the Apiary yields it, straight from Forestry's
/// `addProduct(comb, chance)`.
///
/// A list rather than a single field because real Forestry species genuinely do produce
/// more than one comb -- Austere yields Parched at 0.20 *and* Powdery at 0.50, Monastic
/// yields Wheaten at 0.30 and Mellow at 0.10 -- and collapsing that to one comb would
/// quietly delete the only source of Powdery in the game.
public record CombProduct(Identifier comb, float chance) {
    /// For species defined outside Java -- see the custom bee loader. The range is enforced
    /// here rather than clamped later so a typo is a load error naming the file, not a bee
    /// that silently produces on every pulse.
    public static final Codec<CombProduct> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.fieldOf("comb").forGetter(CombProduct::comb),
        Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(CombProduct::chance)
    ).apply(instance, CombProduct::new));
}
