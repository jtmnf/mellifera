package com.joaonf.mellifera.bee;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// One extracted gene, riding on a serum stack: which chromosome it came off, and the
/// value that chromosome carried.
///
/// Both fields are plain strings on purpose. `trait` is a BeeTrait's serialized name and
/// `value` is whatever that trait's own allele Codec serializes to, which means this record
/// stays a fixed shape however many allele types the genome grows -- and means a serum
/// whose trait or allele no longer exists degrades to an unnamed vial instead of failing to
/// deserialize and wiping the stack.
///
/// It is also everything a future "inject this gene back into a bee" machine needs: the
/// trait names the chromosome to overwrite, and the value feeds that chromosome's allele
/// Codec. Nothing here is extraction-specific.
public record SerumData(String trait, String value) {
    public static final Codec<SerumData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("trait").forGetter(SerumData::trait),
        Codec.STRING.fieldOf("value").forGetter(SerumData::value)
    ).apply(instance, SerumData::new));

    /// The gene a serum captures is the ACTIVE (expressed) allele, never the hidden one.
    ///
    /// That is the choice Binnie's Isolator makes and it is the one that keeps the machine
    /// legible: the active allele is the value already printed on the bee's tooltip, so what
    /// the player sees on the bee is exactly what they get in the vial. Pulling the inactive
    /// allele would make the output depend on information the bee's own tooltip shows only
    /// in brackets, and would make two visually identical bees yield different serums.
    public static SerumData extract(BeeTrait trait, BeeGenome genome) {
        return new SerumData(trait.getSerializedName(), trait.activeValue(genome));
    }

    public @Nullable BeeTrait resolvedTrait() {
        return BeeTrait.byName(trait);
    }
}
