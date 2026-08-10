package com.joaonf.mellifera.bee;

import java.util.List;

import com.joaonf.mellifera.temperature.EnvironmentTemperature;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/// Each constant carries its BeeEffect directly rather than going through a separate
/// id-keyed behavior registry -- this codebase's other per-block/fluid behaviors (see
/// fluid/HotSpringEffects) are hardcoded the same way, not datapack-driven.
///
/// The roster and dominance mirror real Forestry's AlleleEffects (LGPL v3) for every
/// species this mod carries. The *behaviours* are re-implemented against modern Minecraft
/// rather than ported line for line, and two of Forestry's are deliberately reinterpreted:
/// REANIMATION/RESURRECTION raise mobs from bone meal-ish drops upstream, which has no
/// clean modern equivalent here, so Spectral and Phantasmal instead get a regeneration
/// aura and a strong absorption/resistance aura respectively. HYDRATION is this mod's own
/// addition (real Forestry gives the Boggy branch no effect at all).
public enum EffectAllele implements Allele, StringRepresentable {
    NONE("none", true, (level, pos, radius) -> {}),

    HYDRATION("hydration", false, EffectAllele::hydrate),
    AGGRESSIVE("aggressive", false, (level, pos, radius) -> hurtIntruders(level, pos, radius, 2.0F)),
    IGNITION("ignition", false, EffectAllele::ignite),
    BEATIFIC("beatific", false, (level, pos, radius) ->
        blessPlayers(level, pos, radius, MobEffects.REGENERATION, 100, 0)),
    EXPLORATION("exploration", false, (level, pos, radius) ->
        blessPlayers(level, pos, radius, MobEffects.NIGHT_VISION, 300, 0)),
    CREEPER("creeper", false, (level, pos, radius) ->
        blessPlayers(level, pos, radius, MobEffects.SLOWNESS, 100, 1)),
    FERTILE("fertile", false, EffectAllele::fertilise),
    MYCOPHILIC("mycophilic", false, EffectAllele::spreadMushrooms),
    REPULSION("repulsion", false, EffectAllele::repel),
    SNOWING("snowing", false, EffectAllele::snow),
    DRUNKARD("drunkard", false, (level, pos, radius) ->
        blessPlayers(level, pos, radius, MobEffects.NAUSEA, 100, 0)),
    HEROIC("heroic", false, (level, pos, radius) ->
        blessPlayers(level, pos, radius, MobEffects.STRENGTH, 200, 0)),
    REANIMATION("reanimation", false, (level, pos, radius) ->
        blessPlayers(level, pos, radius, MobEffects.REGENERATION, 200, 1)),
    RESURRECTION("resurrection", false, (level, pos, radius) -> {
        blessPlayers(level, pos, radius, MobEffects.ABSORPTION, 300, 1);
        blessPlayers(level, pos, radius, MobEffects.RESISTANCE, 300, 0);
    }),

    /// Upstream: AlleleEffectPotion("miasmic", POISON, 600 ticks).
    MIASMIC("miasmic", false, (level, pos, radius) ->
        blessPlayers(level, pos, radius, MobEffects.POISON, 600, 0)),

    /// Upstream: 4 damage to players in range, reduced by apiarist armour. There is no
    /// apiarist armour here, so it is the flat damage -- this is a hostile bee by design.
    MISANTHROPE("misanthrope", true, (level, pos, radius) -> hurtIntruders(level, pos, radius, 4.0F)),

    /// Upstream: freezes water to ice and turns ground to snow within territory, and only
    /// while the hive is cold -- a warm Glacial hive does nothing.
    GLACIAL("glacial", false, EffectAllele::freeze),

    /// Upstream: 8 damage to every living entity in range, plus environmental destruction.
    /// The damage is kept; the block destruction is not, because a hive that eats the
    /// player's build while they are away is a grief mechanic, not a difficulty one.
    RADIOACTIVE("radioactive", true, (level, pos, radius) -> hurtLiving(level, pos, radius, 8.0F)),

    /// Upstream is literally AlleleEffectNone("festiveEaster", true) -- a marker with no
    /// behaviour at all. Kept so the Festive branch's allele exists and reads correctly.
    FESTIVE("festive", true, (level, pos, radius) -> {});

    /// Above this, a Glacial hive does nothing -- see freeze().
    private static final float FREEZING_CELSIUS = 5.0F;

    public static final Codec<EffectAllele> CODEC = StringRepresentable.fromEnum(EffectAllele::values);

    private final String name;
    private final boolean dominant;
    private final BeeEffect effect;

    EffectAllele(String name, boolean dominant, BeeEffect effect) {
        this.name = name;
        this.dominant = dominant;
        this.effect = effect;
    }

    public BeeEffect effect() {
        return effect;
    }

    @Override
    public boolean dominant() {
        return dominant;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }

    // -- helpers ----------------------------------------------------------------------

    private static AABB territory(BlockPos apiaryPos, int radius) {
        return new AABB(apiaryPos).inflate(radius, 2.0, radius);
    }

    private static List<Player> playersIn(ServerLevel level, BlockPos apiaryPos, int radius) {
        return level.getEntitiesOfClass(Player.class, territory(apiaryPos, radius));
    }

    /// Wets farmland within the territory radius using the same moisture property water
    /// normally spreads -- a Boggy apiary keeps nearby crops watered without a water source.
    private static void hydrate(ServerLevel level, BlockPos apiaryPos, int territoryRadius) {
        forEachBlock(level, apiaryPos, territoryRadius, (cursor, state) -> {
            if (state.is(Blocks.FARMLAND) && state.hasProperty(BlockStateProperties.MOISTURE)) {
                level.setBlock(cursor, state.setValue(BlockStateProperties.MOISTURE, 7), 2);
            }
        });
    }

    private static void hurtLiving(ServerLevel level, BlockPos apiaryPos, int radius, float damage) {
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, territory(apiaryPos, radius))) {
            entity.hurtServer(level, level.damageSources().magic(), damage);
        }
    }

    /// Only bites when the hive is actually cold, matching upstream's temperature guard --
    /// otherwise a Glacial hive would be a free ice farm in any climate.
    private static void freeze(ServerLevel level, BlockPos apiaryPos, int radius) {
        if (EnvironmentTemperature.celsius(level, apiaryPos) > FREEZING_CELSIUS) {
            return;
        }

        BlockPos target = randomIn(level, apiaryPos, radius);
        BlockState state = level.getBlockState(target);

        if (state.is(Blocks.WATER) || (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && state.isAir())) {
            level.setBlock(target, Blocks.ICE.defaultBlockState(), 2);
            return;
        }

        BlockState snow = Blocks.SNOW.defaultBlockState();
        if (level.isEmptyBlock(target) && snow.canSurvive(level, target)) {
            level.setBlock(target, snow, 2);
        }
    }

    private static void hurtIntruders(ServerLevel level, BlockPos apiaryPos, int radius, float damage) {
        for (Player player : playersIn(level, apiaryPos, radius)) {
            player.hurtServer(level, level.damageSources().magic(), damage);
        }
    }

    private static void ignite(ServerLevel level, BlockPos apiaryPos, int radius) {
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, territory(apiaryPos, radius))) {
            entity.igniteForSeconds(4.0F);
        }
    }

    private static void blessPlayers(ServerLevel level, BlockPos apiaryPos, int radius,
                                     Holder<MobEffect> effect, int durationTicks, int amplifier) {
        for (Player player : playersIn(level, apiaryPos, radius)) {
            player.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, true, false));
        }
    }

    /// Bone-meals one eligible block per pass rather than the whole territory at once --
    /// an Agrarian apiary should accelerate a field, not flatten the growth mechanic.
    private static void fertilise(ServerLevel level, BlockPos apiaryPos, int radius) {
        BlockPos target = randomIn(level, apiaryPos, radius);
        BlockState state = level.getBlockState(target);
        if (state.getBlock() instanceof BonemealableBlock bonemealable
            && bonemealable.isValidBonemealTarget(level, target, state)
            && bonemealable.isBonemealSuccess(level, level.getRandom(), target, state)) {
            bonemealable.performBonemeal(level, level.getRandom(), target, state);
        }
    }

    private static void spreadMushrooms(ServerLevel level, BlockPos apiaryPos, int radius) {
        BlockPos target = randomIn(level, apiaryPos, radius);
        if (level.isEmptyBlock(target) && level.getBlockState(target.below()).isSolidRender()) {
            BlockState mushroom = level.getRandom().nextBoolean()
                ? Blocks.BROWN_MUSHROOM.defaultBlockState()
                : Blocks.RED_MUSHROOM.defaultBlockState();
            if (mushroom.canSurvive(level, target)) {
                level.setBlock(target, mushroom, 2);
            }
        }
    }

    private static void repel(ServerLevel level, BlockPos apiaryPos, int radius) {
        Vec3 centre = Vec3.atCenterOf(apiaryPos);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, territory(apiaryPos, radius))) {
            if (entity instanceof Player) {
                continue;
            }

            Vec3 away = entity.position().subtract(centre);
            if (away.lengthSqr() > 1.0E-4) {
                entity.push(away.normalize().scale(0.35));
            }
        }
    }

    private static void snow(ServerLevel level, BlockPos apiaryPos, int radius) {
        BlockPos target = randomIn(level, apiaryPos, radius);
        BlockState snow = Blocks.SNOW.defaultBlockState();
        if (level.isEmptyBlock(target) && snow.canSurvive(level, target)) {
            level.setBlock(target, snow, 2);
        }
    }

    private static BlockPos randomIn(ServerLevel level, BlockPos apiaryPos, int radius) {
        return apiaryPos.offset(
            level.getRandom().nextIntBetweenInclusive(-radius, radius),
            level.getRandom().nextIntBetweenInclusive(-2, 2),
            level.getRandom().nextIntBetweenInclusive(-radius, radius));
    }

    private static void forEachBlock(ServerLevel level, BlockPos apiaryPos, int radius, BlockVisitor visitor) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    cursor.set(apiaryPos.getX() + dx, apiaryPos.getY() + dy, apiaryPos.getZ() + dz);
                    visitor.visit(cursor, level.getBlockState(cursor));
                }
            }
        }
    }

    @FunctionalInterface
    private interface BlockVisitor {
        void visit(BlockPos pos, BlockState state);
    }
}
