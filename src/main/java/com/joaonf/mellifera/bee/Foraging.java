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
    /// The attribute decides whether *vanilla* grounds them, because that is vanilla's own rule and
    /// a datapack may change it. What is derived here is the *cause*, and only from things with no
    /// bearing on that decision: whether it is raining in this dimension, and whether the sky is
    /// dark. `grounded` with no cause showing is a reason nothing lifts -- some other mod's or
    /// datapack's -- and the record says so by leaving the flags false, which keeps the hive stopped
    /// rather than letting a Luminous frame quietly answer a question nobody asked.
    ///
    /// The third cause is this mod's alone: a hive with no sky over it is grounded whatever the
    /// attribute says, which is why `grounded` is not simply `flying()` inverted any more. See
    /// enclosed().
    public record Grounding(boolean grounded, boolean night, boolean rain, boolean enclosed) {
        public static final Grounding FLYING = new Grounding(false, false, false, false);

        /// Whether a hive whose stops are lifted this way may work through it.
        ///
        /// Each flag is a cause answered, and every cause in play has to be answered: a hive
        /// underground during a night storm needs all three. The three arguments arrive from two
        /// places that mean the same thing -- a frame installed in the hive, or a gene in the
        /// queen -- and this does not care which, see BeeHousingBlockEntity.foragersOut.
        public boolean liftedBy(boolean worksAtNight, boolean worksInRain, boolean worksUnderground) {
            if (!grounded) {
                return true;
            }

            if (!night && !rain && !enclosed) {
                // Grounded for a reason none of the three covers.
                return false;
            }

            return (!night || worksAtNight)
                && (!rain || worksInRain)
                && (!enclosed || worksUnderground);
        }
    }

    /// Whether a hive here is shut in: no sky above it, in a world that has a sky to be shut out of.
    ///
    /// THIS IS THE MOD'S OWN RULE, and the only one of the three causes that is. Night and rain are
    /// vanilla's, read off BEES_STAY_IN_HIVE; nothing in vanilla stops a beehive working under a
    /// roof, so a bee that needs daylight is something this mod has to assert.
    ///
    /// `hasSkyLight` is what keeps it from being absurd. The Nether has a ceiling everywhere, so a
    /// plain canSeeSky would make every Nether hive permanently enclosed and every Infernal bee
    /// need the cave gene to work in the place it comes from. A dimension with no sky has no
    /// daylight to be denied, so there is nothing there for this rule to say.
    public static boolean enclosed(Level level, BlockPos pos) {
        return level.dimensionType().hasSkyLight() && !level.canSeeSky(pos.above());
    }

    public static Grounding grounding(Level level, BlockPos pos) {
        boolean enclosed = enclosed(level, pos);

        if (flying(level, pos)) {
            // Vanilla is content to let them out, so a roof is the only thing left that can stop
            // them -- and when there is none, nothing is stopping them at all.
            return enclosed ? new Grounding(true, false, false, true) : Grounding.FLYING;
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

        return new Grounding(true, night, rain, enclosed);
    }
}
