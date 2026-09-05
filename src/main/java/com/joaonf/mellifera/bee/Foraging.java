package com.joaonf.mellifera.bee;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/// The two questions both sides of the mod ask about foraging: what a bee will fly to, and
/// whether it is flying at all.
///
/// Common, and deliberately so. The answers drive two things that must not disagree -- the
/// foragers a player watches leave the hive (HiveSwarm, client) and what the hive actually
/// produces (BeeHousingBlockEntity, server). If the client drew bees working a meadow the
/// server had never looked at, the prettiest thing in the mod would be lying about the only
/// thing it is there to show.
public final class Foraging {
    /// Vanilla's own dusk and dawn, the two keyframes the overworld timeline sets
    /// BEES_STAY_IN_HIVE on and off at. Used only to name the cause of a stop -- see grounding.
    private static final long DAY_LENGTH = 24000L;
    private static final long DUSK = 12542L;
    private static final long DAWN = 23460L;

    private Foraging() {}

    private static boolean isAfterDusk(long timeOfDay) {
        return timeOfDay >= DUSK && timeOfDay < DAWN;
    }

    /// What counts as worth flying to.
    ///
    /// `Bee.attractsBees` is vanilla's own answer -- flowers, less the waterlogged ones and the
    /// lower half of a sunflower -- and BEE_GROWABLES adds the crops the apiary's own flowering
    /// pass bonemeals, so a hive over a wheat field is foraging rather than staring at it.
    public static boolean attracts(BlockState state) {
        return Bee.attractsBees(state) || state.is(BlockTags.BEE_GROWABLES);
    }

    /// Whether foragers are out of the hive at all.
    ///
    /// Vanilla's own rule, not a re-derivation of it: BEES_STAY_IN_HIVE is an environment
    /// attribute, set true by the overworld timeline between dusk and dawn (Timelines, keyframes
    /// 12542 and 23460) and by rain (WeatherAttributes), and it is what vanilla's own beehives
    /// consult before releasing a bee. Reading the same attribute means this mod's hives keep
    /// the same hours as the ones already in the world, and that a datapack which changes those
    /// hours changes both.
    ///
    /// Position matters: the attribute is sampled where the hive is, because a dimension with a
    /// fixed time has no dusk to observe.
    public static boolean flying(Level level, BlockPos pos) {
        return !level.environmentAttributes().getValue(EnvironmentAttributes.BEES_STAY_IN_HIVE, pos);
    }

    /// Why the foragers are indoors, when they are.
    ///
    /// WHY THIS IS SEPARATE FROM flying(). Two frames now lift two of the reasons -- Luminous the
    /// dark, Canopy the rain -- and a single boolean cannot say which one a hive needs. Both are
    /// true at once during a night storm, and then both frames are needed; that is not a special
    /// case, it is what the two flags mean.
    ///
    /// The attribute still decides *whether* they are grounded, because that is vanilla's own rule
    /// and a datapack may change it. What is derived here is only the *cause*, and only from things
    /// with no bearing on that decision: whether it is raining in this dimension, and whether the
    /// sky is dark. `grounded` with neither cause showing is a reason no frame lifts -- some other
    /// mod's or datapack's -- and the record says so by leaving both flags false, which keeps the
    /// hive stopped rather than letting a Luminous frame quietly answer a question nobody asked.
    public record Grounding(boolean grounded, boolean night, boolean rain) {
        public static final Grounding FLYING = new Grounding(false, false, false);

        /// Whether a hive with these two frames may work through it.
        public boolean liftedBy(boolean worksAtNight, boolean worksInRain) {
            if (!grounded) {
                return true;
            }

            if (!night && !rain) {
                // Grounded for a reason neither frame covers.
                return false;
            }

            return (!night || worksAtNight) && (!rain || worksInRain);
        }
    }

    public static Grounding grounding(Level level, BlockPos pos) {
        if (flying(level, pos)) {
            return Grounding.FLYING;
        }

        // Dimension-wide rather than isRainingAt: the attribute that grounded them is set by the
        // weather itself (WeatherAttributes), not by whether rain is falling on this exact block,
        // so a hive under a roof is kept in by a storm the same as one in the open and must be able
        // to say so.
        boolean rain = level.isRaining();

        // The clock, and not isDarkOutside, which is the clock *and* the weather -- a storm darkens
        // the sky, so it would report a night that is really a thunderstorm and the two frames would
        // stop meaning what their names say.
        //
        // This does re-derive vanilla's dusk and dawn, which flying() above pointedly does not, and
        // the difference matters: flying() decides *whether* the hive works and must follow whatever
        // the world says, while this only names the cause of a stop the attribute has already
        // called. A datapack that moves the hours moves the stop with it; at worst it moves out of
        // step with which frame lifts it.
        boolean night = !level.dimensionType().hasFixedTime() && isAfterDusk(level.getDefaultClockTime() % DAY_LENGTH);

        return new Grounding(true, night, rain);
    }
}
