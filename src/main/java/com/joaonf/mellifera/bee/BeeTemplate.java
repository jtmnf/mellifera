package com.joaonf.mellifera.bee;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// The non-species alleles a species is born with -- real Forestry's `setAlleles(template)`
/// per BeeDefinition, which is why a wild Forest bee already flowers slowly and breeds
/// well while a Cultivated one is fast but short-lived, before any breeding happens.
///
/// Written as a wither chain off DEFAULT so a registration only names the traits that
/// actually differ from baseline, exactly the way Forestry's own definitions only set the
/// handful of chromosomes they care about:
///
/// ```
/// BeeTemplate.DEFAULT.flowering(FloweringAllele.SLOWER).fertility(FertilityAllele.HIGH)
/// ```
public record BeeTemplate(
    SpeedAllele speed,
    LifespanAllele lifespan,
    TerritoryAllele territory,
    FertilityAllele fertility,
    ToleranceAllele tolerance,
    EffectAllele effect,
    FloweringAllele flowering,
    ToggleAllele nocturnal,
    ToggleAllele tolerantFlyer,
    ToggleAllele caveDwelling) {

    /// Forestry's own baseline template: the values every BeeDefinition starts from before
    /// its setAlleles() overrides anything.
    ///
    /// The three toggles default to NO, which is both Forestry's baseline and the only answer
    /// that keeps a custom bee written before they existed working as its author meant it to.
    public static final BeeTemplate DEFAULT = new BeeTemplate(
        SpeedAllele.SLOWEST,
        LifespanAllele.SHORTER,
        TerritoryAllele.AVERAGE,
        FertilityAllele.NORMAL,
        ToleranceAllele.NONE,
        EffectAllele.NONE,
        FloweringAllele.SLOWEST,
        ToggleAllele.NO,
        ToggleAllele.NO,
        ToggleAllele.NO);

    /// Every field optional, defaulting to DEFAULT's own value -- the codec equivalent of the
    /// wither chain above, so a bee defined in JSON also names only what it changes.
    public static final Codec<BeeTemplate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        SpeedAllele.CODEC.optionalFieldOf("speed", DEFAULT.speed()).forGetter(BeeTemplate::speed),
        LifespanAllele.CODEC.optionalFieldOf("lifespan", DEFAULT.lifespan()).forGetter(BeeTemplate::lifespan),
        TerritoryAllele.CODEC.optionalFieldOf("territory", DEFAULT.territory()).forGetter(BeeTemplate::territory),
        FertilityAllele.CODEC.optionalFieldOf("fertility", DEFAULT.fertility()).forGetter(BeeTemplate::fertility),
        ToleranceAllele.CODEC.optionalFieldOf("tolerance", DEFAULT.tolerance()).forGetter(BeeTemplate::tolerance),
        EffectAllele.CODEC.optionalFieldOf("effect", DEFAULT.effect()).forGetter(BeeTemplate::effect),
        FloweringAllele.CODEC.optionalFieldOf("flowering", DEFAULT.flowering()).forGetter(BeeTemplate::flowering),
        ToggleAllele.CODEC.optionalFieldOf("nocturnal", DEFAULT.nocturnal()).forGetter(BeeTemplate::nocturnal),
        ToggleAllele.CODEC.optionalFieldOf("tolerant_flyer", DEFAULT.tolerantFlyer()).forGetter(BeeTemplate::tolerantFlyer),
        ToggleAllele.CODEC.optionalFieldOf("cave_dwelling", DEFAULT.caveDwelling()).forGetter(BeeTemplate::caveDwelling)
    ).apply(instance, BeeTemplate::new));

    public BeeTemplate speed(SpeedAllele value) {
        return new BeeTemplate(value, lifespan, territory, fertility, tolerance, effect, flowering,
            nocturnal, tolerantFlyer, caveDwelling);
    }

    public BeeTemplate lifespan(LifespanAllele value) {
        return new BeeTemplate(speed, value, territory, fertility, tolerance, effect, flowering,
            nocturnal, tolerantFlyer, caveDwelling);
    }

    public BeeTemplate territory(TerritoryAllele value) {
        return new BeeTemplate(speed, lifespan, value, fertility, tolerance, effect, flowering,
            nocturnal, tolerantFlyer, caveDwelling);
    }

    public BeeTemplate fertility(FertilityAllele value) {
        return new BeeTemplate(speed, lifespan, territory, value, tolerance, effect, flowering,
            nocturnal, tolerantFlyer, caveDwelling);
    }

    public BeeTemplate tolerance(ToleranceAllele value) {
        return new BeeTemplate(speed, lifespan, territory, fertility, value, effect, flowering,
            nocturnal, tolerantFlyer, caveDwelling);
    }

    public BeeTemplate effect(EffectAllele value) {
        return new BeeTemplate(speed, lifespan, territory, fertility, tolerance, value, flowering,
            nocturnal, tolerantFlyer, caveDwelling);
    }

    public BeeTemplate flowering(FloweringAllele value) {
        return new BeeTemplate(speed, lifespan, territory, fertility, tolerance, effect, value,
            nocturnal, tolerantFlyer, caveDwelling);
    }

    /// The three toggles take no argument and only ever switch a trait *on*: DEFAULT already has
    /// all three off, and a species that wanted one off would be saying nothing.
    ///
    /// Named for what the bee does rather than for the chromosome, both because that is how they
    /// read in a registration -- `.worksAtNight()` after a chain of `.speed(...)` calls -- and
    /// because the record's own accessors have already taken `nocturnal()` and the other two.
    public BeeTemplate worksAtNight() {
        return new BeeTemplate(speed, lifespan, territory, fertility, tolerance, effect, flowering,
            ToggleAllele.YES, tolerantFlyer, caveDwelling);
    }

    public BeeTemplate worksInRain() {
        return new BeeTemplate(speed, lifespan, territory, fertility, tolerance, effect, flowering,
            nocturnal, ToggleAllele.YES, caveDwelling);
    }

    public BeeTemplate worksUnderground() {
        return new BeeTemplate(speed, lifespan, territory, fertility, tolerance, effect, flowering,
            nocturnal, tolerantFlyer, ToggleAllele.YES);
    }
}
