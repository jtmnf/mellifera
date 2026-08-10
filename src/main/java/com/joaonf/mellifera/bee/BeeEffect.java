package com.joaonf.mellifera.bee;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/// A queen's periodic special behavior, run on a slower cadence than production. See
/// EffectAllele for the fixed set of implementations.
@FunctionalInterface
public interface BeeEffect {
    void apply(ServerLevel level, BlockPos apiaryPos, int territoryRadius);
}
