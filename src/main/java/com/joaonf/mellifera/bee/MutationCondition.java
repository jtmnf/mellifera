package com.joaonf.mellifera.bee;

import com.joaonf.mellifera.temperature.EnvironmentTemperature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/// An extra environmental requirement a BeeMutation can layer on top of its species-pair
/// match, which is always checked regardless (see MutationEngine). Radius-based checks
/// use the queen's own TerritoryAllele rather than a fixed distance, so a wider-ranging
/// queen has a genuinely better shot at block-tag mutations.
public sealed interface MutationCondition {
    boolean matches(ServerLevel level, BlockPos apiaryPos, int territoryRadius);

    record None() implements MutationCondition {
        @Override
        public boolean matches(ServerLevel level, BlockPos apiaryPos, int territoryRadius) {
            return true;
        }
    }

    record RequiresBiomeTag(TagKey<Biome> tag) implements MutationCondition {
        @Override
        public boolean matches(ServerLevel level, BlockPos apiaryPos, int territoryRadius) {
            return level.getBiome(apiaryPos).is(tag);
        }
    }

    // Mirrors Forestry's restrictTemperature -- Austere/Tropical/Frozen-branch mutations
    // gate on ambient climate. Uses EnvironmentTemperature directly (the same real-Celsius
    // model ApiaryBlockEntity's own climate gate uses), not a raw biome-temperature check.
    record RequiresClimate(float minCelsius, float maxCelsius) implements MutationCondition {
        @Override
        public boolean matches(ServerLevel level, BlockPos apiaryPos, int territoryRadius) {
            float celsius = EnvironmentTemperature.celsius(level, apiaryPos);
            return celsius >= minCelsius && celsius <= maxCelsius;
        }
    }

    /// Mirrors Forestry's restrictDateRange -- the Festive branch (Leporine, Merry, Tipsy,
    /// Tricky) is only breedable around real Easter/Christmas/New Year/Halloween. Wraps
    /// across the year end, which Tipsy (Dec 27 - Jan 2) genuinely needs.
    ///
    /// Reads the *server's* local date. That is exactly what upstream does, and it is the
    /// only sane option: there is no in-world calendar to hang this off.
    record RequiresDateRange(int startMonth, int startDay, int endMonth, int endDay) implements MutationCondition {
        @Override
        public boolean matches(ServerLevel level, BlockPos apiaryPos, int territoryRadius) {
            java.time.LocalDate today = java.time.LocalDate.now();
            int now = today.getMonthValue() * 100 + today.getDayOfMonth();
            int from = startMonth * 100 + startDay;
            int to = endMonth * 100 + endDay;

            return from <= to ? now >= from && now <= to : now >= from || now <= to;
        }
    }

    record RequiresBlockNearby(TagKey<Block> tag) implements MutationCondition {
        @Override
        public boolean matches(ServerLevel level, BlockPos apiaryPos, int territoryRadius) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = -territoryRadius; dx <= territoryRadius; dx++) {
                for (int dy = -territoryRadius; dy <= territoryRadius; dy++) {
                    for (int dz = -territoryRadius; dz <= territoryRadius; dz++) {
                        cursor.set(apiaryPos.getX() + dx, apiaryPos.getY() + dy, apiaryPos.getZ() + dz);
                        if (level.getBlockState(cursor).is(tag)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }
    }

    // Water is a fluid state, not a block tag -- there's no vanilla "is water" block tag to
    // check against (confirmed against the real 26.2 data), and this matches how the rest
    // of this mod already tests for water (see MelliferaClient's bubble particles).
    record RequiresFluidNearby(TagKey<Fluid> tag) implements MutationCondition {
        @Override
        public boolean matches(ServerLevel level, BlockPos apiaryPos, int territoryRadius) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = -territoryRadius; dx <= territoryRadius; dx++) {
                for (int dy = -territoryRadius; dy <= territoryRadius; dy++) {
                    for (int dz = -territoryRadius; dz <= territoryRadius; dz++) {
                        cursor.set(apiaryPos.getX() + dx, apiaryPos.getY() + dy, apiaryPos.getZ() + dz);
                        if (level.getFluidState(cursor).is(tag)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }
    }
}
