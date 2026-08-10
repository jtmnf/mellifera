package com.joaonf.mellifera.temperature;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;

/// The temperature of the world at a position, in Celsius.
///
/// This is the *ambient* temperature -- what the place is like, not what the player feels. It is
/// deliberately side-agnostic and stateless so the body temperature system can call it from the
/// server with the same result the HUD shows on the client.
///
/// The model, in order:
///
/// - the biome sets a baseline, read as the daily *mean* rather than the peak, blended with
///   nearby biomes so a border reads as a gradient instead of a single-block cliff
/// - altitude cools it, at a fixed lapse rate above sea level
/// - the sun swings it either way, by an amount the biome's own humidity decides
/// - rain and thunder pull it down
/// - sky exposure blends all of the above against a sheltered profile, which is what makes
///   caves, overhangs and sealed rooms mild whatever the surface is doing
/// - depth adds geothermal warming below y=0
/// - nearby blocks add or remove heat locally
/// - standing in fluid overrides most of it
public final class EnvironmentTemperature {
    /// Minecraft's biome temperature is a unitless float: 0.0 snowy plains, 0.5 plains,
    /// 0.95 jungle, 1.2 savanna, 2.0 desert, and frozen peaks sit at -0.7. The mapping onto
    /// Celsius is linear, which is a simplification -- the real curve saturates at the hot end --
    /// but linear stays predictable to tune, and these two numbers are the whole baseline.
    /// Defaults put snowy plains at -2 C, plains at 8 C, jungle at 17 C and desert at 38 C.
    public static final float CELSIUS_AT_ZERO = -2.0F;
    public static final float CELSIUS_PER_UNIT = 20.0F;

    /// Degrees lost per block above sea level. At the world ceiling this is about -23 C, so
    /// peaks are hostile on their own without help from the biome.
    private static final float LAPSE_PER_BLOCK = 0.09F;

    private static final long DAY_LENGTH = 24000L;

    /// Peak swing between noon and midnight. Which of the two applies is decided by the biome's
    /// downfall: dry air holds no heat, so deserts swing hard and jungles barely move.
    private static final float ARID_DAY_SWING = 15.0F;
    private static final float HUMID_DAY_SWING = 4.0F;

    private static final float RAIN_COOLING = -5.0F;
    private static final float THUNDER_COOLING = -3.0F;

    /// Sheltered air is pulled this far from the local baseline toward CAVE_CELSIUS. Not all the
    /// way: a cave under a desert should still be warmer than one under a glacier.
    private static final float SHELTER_DAMPING = 0.65F;
    private static final float CAVE_CELSIUS = 12.0F;

    /// Below this height the world starts warming again on the way down to the lava seas.
    private static final int GEOTHERMAL_START_Y = 0;
    private static final float GEOTHERMAL_PER_BLOCK = 0.22F;

    /// Radius of the block scan for local heat. Costs a sphere of block lookups per call, so
    /// callers should sample on a cadence -- per tick at the very most, and preferably less
    /// often than that. Nothing this model reads moves fast: see BeeHousingBlockEntity's
    /// climate cooldown and MelliferaTemperatureHud, which are the two callers that matter.
    private static final int HEAT_RADIUS = 4;
    private static final float HEAT_SOURCE_CAP = 25.0F;

    private static final int HEAT_SPAN = 2 * HEAT_RADIUS + 1;

    /// The falloff of every offset in the scan box, indexed by dx/dy/dz.
    ///
    /// The weight is `1 - distance / HEAT_RADIUS`, which used to mean a square root per block
    /// per call -- 729 of them on every sample, for a shape that is a compile-time constant.
    /// Zero outside the sphere, which is also what the shell at exactly HEAT_RADIUS weighed, so
    /// a zero here means "skip this block" without changing any total.
    private static final float[] HEAT_WEIGHTS = buildHeatWeights();

    /// Horizontal radius and step over which nearby biomes are blended into the baseline.
    /// See BIOME_BLEND_OFFSETS, which is this grid worked out once.
    /// 8 blocks matches the width of Vanilla's own default grass/fog colour blend, so the
    /// transition already reads as familiar. Weight falls off linearly with distance, zero
    /// at the edge, so a step further out never fully drowns the biome you're standing in.
    private static final int BIOME_BLEND_RADIUS = 8;
    private static final int BIOME_BLEND_STEP = 4;

    private static final float LAVA_RADIANCE = 45.0F;
    private static final float FIRE_RADIANCE = 14.0F;
    private static final float CAMPFIRE_RADIANCE = 12.0F;
    private static final float FURNACE_RADIANCE = 8.0F;
    private static final float MAGMA_RADIANCE = 6.0F;
    private static final float TORCH_RADIANCE = 3.0F;
    private static final float POWDER_SNOW_CHILL = -8.0F;

    /// How hard standing in a fluid drags the ambient value toward the fluid's own temperature.
    private static final float WATER_PULL = 0.6F;
    private static final float WATER_CELSIUS = 14.0F;
    private static final float LAVA_CELSIUS = 200.0F;

    private EnvironmentTemperature() {}

    /// The contributions are kept separate from the total so the HUD can show why the number is
    /// what it is. Tuning a model this size blind is not workable.
    public record Sample(
        float celsius,
        float biomeCelsius,
        float altitude,
        float sun,
        float weather,
        float geothermal,
        float heatSources,
        float skyExposure) {}

    public static float celsius(Level level, BlockPos pos) {
        return sample(level, pos).celsius();
    }

    public static Sample sample(Level level, BlockPos pos) {
        BiomeClimate climate = blendedClimate(level, pos);
        float biomeCelsius = CELSIUS_AT_ZERO + climate.baseTemperature() * CELSIUS_PER_UNIT;

        // Stored sky light, not the current light level: it is 15 under open sky at midnight as
        // well as at noon, which makes it a clean measure of exposure rather than brightness.
        // It also degrades smoothly under trees and overhangs instead of flipping like canSeeSky.
        float skyExposure = level.getBrightness(LightLayer.SKY, pos) / 15.0F;

        float altitude = altitudeAdjustment(level, pos);
        float sun = sunAdjustment(level, climate) * skyExposure;
        float weather = weatherAdjustment(level, pos) * skyExposure;
        float geothermal = geothermalAdjustment(pos);
        float heatSources = heatSourceAdjustment(level, pos);

        float local = biomeCelsius + altitude;
        float outdoor = local + sun + weather;
        float sheltered = Mth.lerp(SHELTER_DAMPING, local, CAVE_CELSIUS);

        // Dimensions without a sky have no shelter to speak of -- their "cave" is the whole
        // dimension. Blending toward a mild baseline there would just erase the Nether.
        float ambient = level.dimensionType().hasSkyLight()
            ? Mth.lerp(skyExposure, sheltered, outdoor)
            : local;

        float celsius = submersionAdjustment(level, pos, ambient + geothermal + heatSources);

        return new Sample(celsius, biomeCelsius, altitude, sun, weather, geothermal, heatSources, skyExposure);
    }

    private static float altitudeAdjustment(Level level, BlockPos pos) {
        int seaLevel = level.getSeaLevel();
        if (pos.getY() <= seaLevel) {
            return 0.0F;
        }

        return -(pos.getY() - seaLevel) * LAPSE_PER_BLOCK;
    }

    /// Day time 0 is sunrise, 6000 noon, 18000 midnight, so a plain sine over the day is already
    /// the sun's elevation. Dimensions with no clock report 0 ticks, which lands on neutral.
    private static float sunAdjustment(Level level, BiomeClimate climate) {
        long timeOfDay = level.getDefaultClockTime() % DAY_LENGTH;
        float elevation = Mth.sin((float) (2.0 * Math.PI * timeOfDay / DAY_LENGTH));

        float aridity = 1.0F - Mth.clamp(climate.downfall(), 0.0F, 1.0F);
        float swing = Mth.lerp(aridity, HUMID_DAY_SWING, ARID_DAY_SWING);

        return elevation * swing;
    }

    private record BiomeClimate(float baseTemperature, float downfall) {}

    /// One position the blend reads, and how much it counts for.
    private record BlendOffset(int dx, int dz, float weight) {}

    /// The blend grid, worked out once. Only nine of the twenty-five candidate positions carry
    /// any weight, and which nine is fixed, so the corners are dropped here instead of being
    /// rejected on every sample -- and the divisor comes with them rather than being re-summed.
    private static final BlendOffset[] BIOME_BLEND_OFFSETS = buildBlendOffsets();
    private static final float BIOME_BLEND_WEIGHT = totalWeight(BIOME_BLEND_OFFSETS);

    /// Averages baseTemperature/downfall over nearby biomes, weighted linearly by distance, so
    /// the reading eases across a border over BIOME_BLEND_RADIUS blocks instead of snapping the
    /// instant a single-block biome cell flips. Horizontal only: biomes don't vary meaningfully
    /// within a column the way they do across the map.
    private static BiomeClimate blendedClimate(Level level, BlockPos pos) {
        float weightedTemperature = 0.0F;
        float weightedDownfall = 0.0F;

        // One cursor for all nine reads: getBiome only takes coordinates off the position and
        // keeps no reference to it, and pos.offset was allocating nine of these per sample.
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (BlendOffset offset : BIOME_BLEND_OFFSETS) {
            cursor.set(pos.getX() + offset.dx(), pos.getY(), pos.getZ() + offset.dz());
            Biome biome = level.getBiome(cursor).value();
            weightedTemperature += biome.getBaseTemperature() * offset.weight();
            weightedDownfall += biome.getModifiedClimateSettings().downfall() * offset.weight();
        }

        return new BiomeClimate(weightedTemperature / BIOME_BLEND_WEIGHT, weightedDownfall / BIOME_BLEND_WEIGHT);
    }

    /// Walked in the same order the sample used to walk it, so the weights are summed the same
    /// way and the blended figure is unchanged to the bit.
    private static BlendOffset[] buildBlendOffsets() {
        List<BlendOffset> offsets = new ArrayList<>();

        for (int dx = -BIOME_BLEND_RADIUS; dx <= BIOME_BLEND_RADIUS; dx += BIOME_BLEND_STEP) {
            for (int dz = -BIOME_BLEND_RADIUS; dz <= BIOME_BLEND_RADIUS; dz += BIOME_BLEND_STEP) {
                float weight = BIOME_BLEND_RADIUS - Mth.sqrt(dx * dx + dz * dz);
                if (weight <= 0.0F) {
                    continue;
                }

                offsets.add(new BlendOffset(dx, dz, weight));
            }
        }

        return offsets.toArray(new BlendOffset[0]);
    }

    private static float totalWeight(BlendOffset[] offsets) {
        float total = 0.0F;
        for (BlendOffset offset : offsets) {
            total += offset.weight();
        }

        return total;
    }

    private static float[] buildHeatWeights() {
        float[] weights = new float[HEAT_SPAN * HEAT_SPAN * HEAT_SPAN];

        for (int dx = -HEAT_RADIUS; dx <= HEAT_RADIUS; dx++) {
            for (int dy = -HEAT_RADIUS; dy <= HEAT_RADIUS; dy++) {
                for (int dz = -HEAT_RADIUS; dz <= HEAT_RADIUS; dz++) {
                    float distance = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                    weights[heatIndex(dx, dy, dz)] = distance >= HEAT_RADIUS
                        ? 0.0F
                        : 1.0F - distance / HEAT_RADIUS;
                }
            }
        }

        return weights;
    }

    private static int heatIndex(int dx, int dy, int dz) {
        return ((dx + HEAT_RADIUS) * HEAT_SPAN + (dy + HEAT_RADIUS)) * HEAT_SPAN + (dz + HEAT_RADIUS);
    }

    /// isRainingAt already rules out biomes that get no precipitation and positions with no sky,
    /// so a desert stays dry through a storm.
    private static float weatherAdjustment(Level level, BlockPos pos) {
        if (!level.isRainingAt(pos)) {
            return 0.0F;
        }

        return level.getRainLevel(1.0F) * RAIN_COOLING + level.getThunderLevel(1.0F) * THUNDER_COOLING;
    }

    private static float geothermalAdjustment(BlockPos pos) {
        if (pos.getY() >= GEOTHERMAL_START_Y) {
            return 0.0F;
        }

        return (GEOTHERMAL_START_Y - pos.getY()) * GEOTHERMAL_PER_BLOCK;
    }

    private static float heatSourceAdjustment(Level level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        float total = 0.0F;

        for (int dx = -HEAT_RADIUS; dx <= HEAT_RADIUS; dx++) {
            for (int dz = -HEAT_RADIUS; dz <= HEAT_RADIUS; dz++) {
                // The sphere does not reach the corners of the box, so thirty-six of the
                // eighty-one columns cannot contain a weighted block at any height. Rejecting
                // them on two multiplies saves their loaded-check as well as their nine reads.
                if (dx * dx + dz * dz >= HEAT_RADIUS * HEAT_RADIUS) {
                    continue;
                }

                // Once per column rather than per block: getBlockState is free to pull an
                // unloaded chunk on the server when the scan crosses the edge of loaded area.
                cursor.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                if (!level.isLoaded(cursor)) {
                    continue;
                }

                for (int dy = -HEAT_RADIUS; dy <= HEAT_RADIUS; dy++) {
                    float weight = HEAT_WEIGHTS[heatIndex(dx, dy, dz)];
                    if (weight <= 0.0F) {
                        continue;
                    }

                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    float strength = radianceOf(level.getBlockState(cursor));
                    if (strength != 0.0F) {
                        total += strength * weight;
                    }
                }
            }
        }

        return Mth.clamp(total, -HEAT_SOURCE_CAP, HEAT_SOURCE_CAP);
    }

    private static float radianceOf(BlockState state) {
        // Almost everything in a box this size is air, and air is neither a fluid nor in any of
        // the tags below. One reference comparison up front is worth more here than anywhere
        // else in the model, because it is the case that happens hundreds of times per sample.
        if (state.isAir()) {
            return 0.0F;
        }

        FluidState fluid = state.getFluidState();

        if (fluid.is(FluidTags.LAVA)) {
            return LAVA_RADIANCE;
        }

        if (state.is(BlockTags.FIRE)) {
            return FIRE_RADIANCE;
        }

        if (state.is(BlockTags.CAMPFIRES)) {
            return isLit(state) ? CAMPFIRE_RADIANCE : 0.0F;
        }

        if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) {
            return isLit(state) ? FURNACE_RADIANCE : 0.0F;
        }

        if (state.is(Blocks.MAGMA_BLOCK)) {
            return MAGMA_RADIANCE;
        }

        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN)) {
            return TORCH_RADIANCE;
        }

        // Powder snow only, on purpose. Ice and snow layers carpet whole biomes, and the biome's
        // own temperature already prices them in -- counting them here would charge twice.
        if (state.is(Blocks.POWDER_SNOW)) {
            return POWDER_SNOW_CHILL;
        }

        return 0.0F;
    }

    private static boolean isLit(BlockState state) {
        return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    /// Fluid beats air: water is far denser than the air above it, so being in it drags the
    /// reading toward the fluid rather than nudging it.
    private static float submersionAdjustment(Level level, BlockPos pos, float ambient) {
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty()) {
            return ambient;
        }

        if (fluid.is(FluidTags.WATER)) {
            return Mth.lerp(WATER_PULL, ambient, WATER_CELSIUS);
        }

        if (fluid.is(FluidTags.LAVA)) {
            return LAVA_CELSIUS;
        }

        return ambient;
    }
}
