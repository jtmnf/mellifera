package com.joaonf.mellifera.client.color;

import java.util.Set;

import com.joaonf.mellifera.block.PipeBlock;
import com.joaonf.mellifera.block.PipeBlockEntity;
import com.joaonf.mellifera.registry.MelliferaFluids;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.fluid.FluidTintSource;

/// Colours the liquid inside a Pipe with whatever is going through it.
///
/// The pipe's inner element is painted in a neutral white so that this can tint it to any fluid,
/// this mod's or another's. The number comes from the fluid's own baked model -- the same tint the
/// game paints that fluid with when it is standing in the world -- so a pipe of water is water
/// coloured and a pipe of somebody else's acid is whatever they made it.
///
/// Most fluids carry no tint at all, this mod's honey included: they get their colour from their
/// sprite, which is not something a static model can be pointed at. So honey is named here, and
/// anything else without a tint runs pale. That is a wart and it is written down rather than hidden:
/// the general fix is reading the average colour out of the fluid's own sprite, which wants a cache
/// and a careful look at when the atlas is ready.
///
/// Positional, unlike the Apiary's paint. What is in a pipe is not a property of the pipe -- it is
/// what happens to be passing -- so this has to read the block entity, which is what colorInWorld is
/// for. The stateless `color` is the fallback for the item and for break particles, where there is
/// no pipe and so nothing in it.
public class PipeFluidTintSource implements BlockTintSource {
    private static final int WHITE = 0xFFFFFF;

    /// The colour the rest of the mod paints honey: HoneyGauge's own surface line, so a pipe and the
    /// gauge on the machine at the end of it agree.
    private static final int HONEY = 0xE0A526;

    @Override
    public int color(BlockState state) {
        return WHITE;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (!state.getValue(PipeBlock.FLOWING)) {
            return WHITE;
        }

        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) || pipe.carried().isEmpty()) {
            return WHITE;
        }

        if (pipe.carried().getFluid().isSame(MelliferaFluids.HONEY.get())) {
            return HONEY;
        }

        FluidState fluid = pipe.carried().getFluid().defaultFluidState();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid);
        FluidTintSource tint = model.fluidTintSource();

        return tint == null ? WHITE : tint.colorInWorld(fluid, state, level, pos);
    }

    /// FLOWING is the only property that changes the answer without the position changing, so it is
    /// the only one BlockColors has to treat as significant.
    @Override
    public Set<Property<?>> relevantProperties() {
        return Set.of(PipeBlock.FLOWING);
    }
}
