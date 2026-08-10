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
    private Foraging() {}

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
}
