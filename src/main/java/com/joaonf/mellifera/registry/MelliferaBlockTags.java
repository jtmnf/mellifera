package com.joaonf.mellifera.registry;

import com.joaonf.mellifera.Mellifera;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class MelliferaBlockTags {
    // What the Rocky bee's mutation wants nearby (see MelliferaBeeMutations) -- the only thing
    // this tag is still for. MC 26.2 dropped the old unified minecraft:ores tag in favour of one
    // tag per metal, so this is the union of those plus quartz/debris, which never had a tag.
    public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Mellifera.MODID, "ores"));

    /// The six wild hives. Two things read it: the Scoop, which mines them and nothing else
    /// quickly (see MelliferaItems.SCOOP), and a data pack that wants to add a hive of its own and
    /// have the Scoop work on it.
    public static final TagKey<Block> HIVES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Mellifera.MODID, "hives"));

    private MelliferaBlockTags() {}
}
