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
    FloweringAllele flowering) {

    /// Forestry's own baseline template: the values every BeeDefinition starts from before
    /// its setAlleles() overrides anything.
    public static final BeeTemplate DEFAULT = new BeeTemplate(
        SpeedAllele.SLOWEST,
        LifespanAllele.SHORTER,
        TerritoryAllele.AVERAGE,
        FertilityAllele.NORMAL,
        ToleranceAllele.NONE,
        EffectAllele.NONE,
        FloweringAllele.SLOWEST);

    /// Every field optional, defaulting to DEFAULT's own value -- the codec equivalent of the
    /// wither chain above, so a bee defined in JSON also names only what it changes.
    public static final Codec<BeeTemplate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        SpeedAllele.CODEC.optionalFieldOf("speed", DEFAULT.speed()).forGetter(BeeTemplate::speed),
        LifespanAllele.CODEC.optionalFieldOf("lifespan", DEFAULT.lifespan()).forGetter(BeeTemplate::lifespan),
        TerritoryAllele.CODEC.optionalFieldOf("territory", DEFAULT.territory()).forGetter(BeeTemplate::territory),
        FertilityAllele.CODEC.optionalFieldOf("fertility", DEFAULT.fertility()).forGetter(BeeTemplate::fertility),
        ToleranceAllele.CODEC.optionalFieldOf("tolerance", DEFAULT.tolerance()).forGetter(BeeTemplate::tolerance),
        EffectAllele.CODEC.optionalFieldOf("effect", DEFAULT.effect()).forGetter(BeeTemplate::effect),
        FloweringAllele.CODEC.optionalFieldOf("flowering", DEFAULT.flowering()).forGetter(BeeTemplate::flowering)
    ).apply(instance, BeeTemplate::new));

    public BeeTemplate speed(SpeedAllele value) {
        return new BeeTemplate(value, lifespan, territory, fertility, tolerance, effect, flowering);
    }

    public BeeTemplate lifespan(LifespanAllele value) {
        return new BeeTemplate(speed, value, territory, fertility, tolerance, effect, flowering);
    }

    public BeeTemplate territory(TerritoryAllele value) {
        return new BeeTemplate(speed, lifespan, value, fertility, tolerance, effect, flowering);
    }

    public BeeTemplate fertility(FertilityAllele value) {
        return new BeeTemplate(speed, lifespan, territory, value, tolerance, effect, flowering);
    }

    public BeeTemplate tolerance(ToleranceAllele value) {
        return new BeeTemplate(speed, lifespan, territory, fertility, value, effect, flowering);
    }

    public BeeTemplate effect(EffectAllele value) {
        return new BeeTemplate(speed, lifespan, territory, fertility, tolerance, value, flowering);
    }

    public BeeTemplate flowering(FloweringAllele value) {
        return new BeeTemplate(speed, lifespan, territory, fertility, tolerance, effect, value);
    }
}
