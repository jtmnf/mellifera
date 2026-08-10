package com.joaonf.mellifera.bee;

/// What a frame installed in an apiary actually does.
///
/// One class of item, five behaviours, chosen at registration the same way ConduitItem
/// takes its ConduitType -- so a new frame is a registration plus a texture, not a new
/// item class.
///
/// Frames stack: three Mutagenic frames multiply the mutation chance three times over, and
/// a Productivity frame next to a Terminator gives you a fast single-cycle queen. The one
/// exception is inheritance, where two frames pulling in opposite directions cancel (see
/// ApiaryBlockEntity.inheritanceMode) -- there is no sensible way to obey both.
public enum FrameType {
    /// The plain frame: more comb out of every cycle, not faster cycles.
    ///
    /// It buys output rather than speed because speed already has an answer -- the
    /// Accelerator -- and two frames that both make the hive "go faster" would be one frame
    /// with two names. This one makes each pulse worth more instead.
    PRODUCTIVITY("productivity", 0.0F, 0.02F, 1.0F, Inheritance.RANDOM, false, false, false, 0.15F),

    /// Four times the production rate on its own. The plain Productivity frame's +15% is a
    /// nudge; this is the answer to an apiary that still takes minutes per cycle.
    ///
    /// Wears fast on purpose -- ten pulses and it is gone, so at full speed it burns out in
    /// well under one queen. It is meant as a burst, unless you spend an ender pearl on an
    /// anvil to make it permanent (see MelliferaAnvilRecipes).
    ACCELERATOR("accelerator", 3.0F, 0.10F, 1.0F, Inheritance.RANDOM, false, false, false, 0.0F),

    /// Offspring always inherit each parent's *expressed* allele instead of a coin flip
    /// between its two. Purifies a line: what you can see is what gets passed on.
    DOMINANT("dominant", 0.0F, 0.04F, 1.0F, Inheritance.ACTIVE, false, false, false, 0.0F),

    /// The mirror image: offspring always inherit each parent's *hidden* allele. Surfaces
    /// whatever a bee has been quietly carrying, which is the only way to deliberately pull
    /// a recessive species back out of a line.
    RECESSIVE("recessive", 0.0F, 0.04F, 1.0F, Inheritance.INACTIVE, false, false, false, 0.0F),

    /// Multiplies every mutation roll. Chances stay capped at 1.0, so this shortens the
    /// grind without ever guaranteeing a result.
    MUTAGENIC("mutagenic", 0.0F, 0.08F, 4.0F, Inheritance.RANDOM, false, false, false, 0.0F),

    /// Ends the queen after a single production cycle, whatever her lifespan says. For
    /// running a cross quickly when you only care about the brood.
    TERMINATOR("terminator", 0.0F, 0.25F, 1.0F, Inheritance.RANDOM, true, false, false, 0.0F),

    /// Shelters the queen from the climate entirely: the housing works whatever the local
    /// temperature, so a Tropical line runs in a tundra and a Wintry one in a desert.
    ///
    /// This is the job the Alveary used to do by being a different building. Moving it onto
    /// a frame changes what it costs: a building was a one-off you built once and forgot,
    /// whereas a frame occupies one of the three slots for as long as it lasts, so running
    /// a bee out of its band is now paid for in the slot it takes from Productivity or
    /// Mutagenic rather than in a single up-front craft.
    ///
    /// Wears slowly on purpose. It has no effect on output at all -- it only removes a
    /// gate -- and a frame you have to keep replacing to stop production dying entirely is
    /// a chore rather than a decision.
    INSULATION("insulation", 0.0F, 0.015F, 1.0F, Inheritance.RANDOM, false, false, true, 0.0F),

    /// Keeps the hive running by itself: when the queen dies, her replacement princess goes
    /// straight back into the queen slot and one of her drones into the drone slot, so the
    /// next generation starts without you.
    ///
    /// A drone only goes back in if it is genetically identical to what is already sitting
    /// there (or the slot is empty) -- siblings from one brood are independent mutation
    /// rolls and can differ, and silently pairing a bee you did not choose would undo the
    /// breeding. Anything that doesn't match goes to the output like normal.
    AUTOMATION("automation", 0.0F, 0.05F, 1.0F, Inheritance.RANDOM, false, true, false, 0.0F);

    /// Which allele of a parent's pair an offspring inherits.
    public enum Inheritance {
        RANDOM,
        ACTIVE,
        INACTIVE
    }

    private final String name;
    private final float speedBonus;
    private final float wearPerPulse;
    private final float mutationMultiplier;
    private final Inheritance inheritance;
    private final boolean terminates;
    private final boolean automates;
    private final boolean insulates;
    private final float combBonus;

    FrameType(String name, float speedBonus, float wearPerPulse, float mutationMultiplier,
              Inheritance inheritance, boolean terminates, boolean automates, boolean insulates,
              float combBonus) {
        this.name = name;
        this.speedBonus = speedBonus;
        this.wearPerPulse = wearPerPulse;
        this.mutationMultiplier = mutationMultiplier;
        this.inheritance = inheritance;
        this.terminates = terminates;
        this.automates = automates;
        this.insulates = insulates;
        this.combBonus = combBonus;
    }

    public String frameName() {
        return name;
    }

    /// Fraction shaved off the production threshold per installed frame.
    public float speedBonus() {
        return speedBonus;
    }

    /// How fast this frame is consumed. The stronger the effect, the shorter it lasts --
    /// a Terminator burns out in four cycles, a Productivity frame lasts fifty.
    public float wearPerPulse() {
        return wearPerPulse;
    }

    public float mutationMultiplier() {
        return mutationMultiplier;
    }

    public Inheritance inheritance() {
        return inheritance;
    }

    public boolean terminates() {
        return terminates;
    }

    public boolean automates() {
        return automates;
    }

    /// True if this frame lets the housing work outside the species' temperature band.
    public boolean insulates() {
        return insulates;
    }

    /// Fraction of an extra production pass this frame is worth. Frames add up, so three
    /// Productivity frames are +45% comb and not 15% compounded three times -- the same way
    /// speed bonuses already stack.
    public float combBonus() {
        return combBonus;
    }
}
