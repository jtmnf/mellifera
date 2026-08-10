package com.joaonf.mellifera.client.color;

import java.util.Set;

import com.joaonf.mellifera.block.ApiaryBlock;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/// Paints the Apiary's board layer from the colour on its blockstate.
///
/// Layer 0 of the block's tint sources, which is what `"tintindex": 0` in the four apiary
/// models asks for. The second cube in those models -- brass, doorway, bees -- carries no
/// tintindex and never reaches here, which is the whole reason a blue hive does not get blue
/// bees standing at the door.
///
/// There is no in-world variant of `color`: paint does not depend on where the hive is, only on
/// what was thrown at it, so the block in the hand, the block in the world and the particles
/// that come off it when it breaks all resolve to the same number.
public class ApiaryTintSource implements BlockTintSource {
    @Override
    public int color(BlockState state) {
        return state.getValue(ApiaryBlock.TINT).paintColor();
    }

    /// Tells BlockColors that the colour moves with this property, so it does not have to treat
    /// every state as its own colour or, worse, cache one of them for all of them.
    @Override
    public Set<Property<?>> relevantProperties() {
        return Set.of(ApiaryBlock.TINT);
    }
}
