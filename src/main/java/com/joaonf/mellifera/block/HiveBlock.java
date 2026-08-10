package com.joaonf.mellifera.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/// A wild bee hive -- what a player finds growing in the world instead of crafting a
/// Princess/Drone from scratch, the way real Forestry's overworldHiveBees actually enter
/// play. One class for every hive-root species: `species` is baked in at registration
/// (see MelliferaBlocks), the same way ConduitItem takes its ConduitType as a constructor
/// argument rather than needing a subclass per type. What it drops on break lives entirely
/// in its loot table (data/mellifera/loot_table/blocks/hive_<species>.json), matching every
/// other block in this mod -- no Java-side drop logic.
public class HiveBlock extends VegetationBlock {
    public static final MapCodec<HiveBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Identifier.CODEC.fieldOf("species").forGetter(HiveBlock::species),
        propertiesCodec()
    ).apply(instance, HiveBlock::new));

    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 10.0);

    private final Identifier species;

    public HiveBlock(Identifier species, Properties properties) {
        super(properties);
        this.species = species;
    }

    public Identifier species() {
        return species;
    }

    @Override
    public MapCodec<? extends VegetationBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // VegetationBlock's default only allows #minecraft:supports_vegetation (dirt/grass/
    // podzol/...), which excludes the bare sand Modest's real desert habitat generates on.
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.SUPPORTS_VEGETATION)
            || state.is(Blocks.SAND)
            || state.is(Blocks.RED_SAND)
            || state.is(Blocks.TERRACOTTA);
    }
}
