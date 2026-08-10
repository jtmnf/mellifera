package com.joaonf.mellifera.registry;

import com.joaonf.mellifera.Mellifera;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/// Liquid honey: what a Squeezer makes out of honey drops.
///
/// WHY A FLUID AND NOT MORE ITEMS. The mod already turns combs into drops and drops into bottles, and
/// another item in that chain would just be a fourth noun. A fluid is the form the rest of the modded
/// world can actually take: a tank stores it, a pipe moves it, and any machine that wants honey can
/// ask for it without knowing this mod exists. That interoperability is the whole point of the
/// Squeezer -- see SqueezerBlockEntity.
///
/// FOUR THINGS MAKE A FLUID, and all four are needed or it half-exists: a FluidType (its physical
/// behaviour), a source and a flowing Fluid (the still block and the spreading one), a LiquidBlock (so
/// it can sit in the world) and a bucket (so a player can carry it without a pipe). Registering the
/// pair without the block leaves a fluid that cannot be placed; without the bucket, one that cannot be
/// held.
///
/// Honey is thick, and each of the three things that say so is tuned separately: it spreads slowly, it
/// spreads a short way, and moving through it is heavy. Vanilla's own thick fluid is lava, so lava is
/// what the numbers here are read against rather than water.
///
/// Overdo any of them and it stops being a fluid a player will use -- and one of them has already been
/// overdone once. A player standing in honey must still be able to walk out of it, however slowly; see
/// HoneyFluidType.move, which is the only place that decides how heavy it feels.
public final class MelliferaFluids {
    public static final DeferredRegister<FluidType> TYPES =
        DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, Mellifera.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, Mellifera.MODID);

    /// How a spill behaves, against Vanilla's two references: water finds a hole 4 blocks away, thins by
    /// 1 a block and steps every 5 ticks; slow lava is 2, 2 and 30.
    ///
    /// Honey at 2, 2 and 15 is now most of the way to lava, and deliberately: at water's own thinning
    /// rate a bucket ran eight blocks and read as orange water with a limp. Thinning by 2 halves that
    /// to four, which is the difference between a spill and a puddle -- and a puddle is what a thick
    /// fluid makes. The step rate is three times water's rather than lava's six; a full 30 was tried
    /// first and did not read as thick, it read as broken.
    private static final int SLOPE_FIND_DISTANCE = 2;
    private static final int LEVEL_DECREASE_PER_BLOCK = 2;
    private static final int TICK_RATE = 15;

    /// Honey really is about 1.42 g/cm3 and thousands of times more viscous than water. Neither
    /// number changes how this fluid behaves on its own -- Vanilla's fluid physics reads neither --
    /// but any pipe, pump or tank that sorts fluids by them will sort honey correctly, which is the
    /// whole reason the Squeezer produces a fluid at all.
    private static final int DENSITY = 1420;
    private static final int VISCOSITY = 6000;

    public static final DeferredHolder<FluidType, FluidType> HONEY_TYPE = TYPES.register("honey",
        () -> new HoneyFluidType(FluidType.Properties.create()
            .descriptionId("fluid.mellifera.honey")
            // How hard the current shoves an entity along; water's own is 0.014. Honey barely moves,
            // so what it does to you is hold you, not carry you.
            .motionScale(0.006D)
            .density(DENSITY)
            .viscosity(VISCOSITY)
            // You can climb out. Everything about honey says you should not be able to, and a player
            // who drops into a pool and cannot rise out of it has found a trap, not a fluid. The drag
            // in HoneyFluidType.move is what makes getting out slow; this is what makes it possible.
            .canSwim(true)
            .canDrown(false)
            .canPushEntity(true)
            .canExtinguish(true)
            .supportsBoating(false)
            .fallDistanceModifier(0.0F)));

    /// Movement inside honey, which a custom fluid has to provide for itself.
    ///
    /// This is not a refinement -- without it an entity in honey cannot move *at all*. Vanilla's
    /// LivingEntity.travelInFluid handles exactly two cases, water and lava, and for anything else it
    /// defers to FluidType.move; the default returns false, meaning "not handled", and the fallback
    /// for a fluid that is not flagged water-like is no movement whatsoever. So a fluid that does not
    /// override this is one you stand in, frozen, until you break the block.
    ///
    /// The shape is vanilla's own travelInLava, which is the game's model of a fluid too thick to
    /// swim: accelerate gently, damp hard, sink at a quarter gravity. Only the horizontal damping is
    /// ours, and it is the single number that means "how thick".
    ///
    /// Not `isWaterLike(true)`, which would have got movement for free and a great deal else besides:
    /// that flag also damages water-sensitive mobs, stops sun-burning mobs igniting, powers conduits,
    /// arms riptide tridents and plays underwater music. Honey is not water with a different colour.
    private static final class HoneyFluidType extends FluidType {
        /// Vanilla's own figure for water and for lava: how hard an entity may push against a fluid.
        /// Depth Strider deliberately does not enter into it -- nothing helps you through honey.
        private static final float ACCELERATION = 0.02F;

        /// What is left of your speed each tick. Water keeps about 0.8 and lava 0.5, and the terminal
        /// speed that falls out of it is acceleration / (1 - drag): a bit over one block a second
        /// here, against lava's 0.8 and a walk's 4.3. Slow enough to be worth avoiding, fast enough
        /// that crossing a spill is a decision rather than a wait.
        private static final double DRAG = 0.62;

        /// Vertical damping, water's and lava's alike.
        private static final double VERTICAL_DRAG = 0.8;

        /// Sinking. A quarter of normal gravity, as lava uses -- honey holds you up nearly as well.
        private static final double GRAVITY_SCALE = 0.25;

        private HoneyFluidType(Properties properties) {
            super(properties);
        }

        @Override
        public boolean move(LivingEntity entity, Vec3 movement, double gravity) {
            entity.moveRelative(ACCELERATION, movement);
            entity.move(MoverType.SELF, entity.getDeltaMovement());
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(DRAG, VERTICAL_DRAG, DRAG));

            if (gravity != 0.0) {
                entity.setDeltaMovement(entity.getDeltaMovement().add(0.0, -gravity * GRAVITY_SCALE, 0.0));
            }

            return true;
        }
    }

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HONEY = FLUIDS.register("honey",
        () -> new BaseFlowingFluid.Source(MelliferaFluids.properties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> HONEY_FLOWING = FLUIDS.register("honey_flowing",
        () -> new BaseFlowingFluid.Flowing(MelliferaFluids.properties()));

    /// Water's behaviour, recoloured. A liquid block is not a block a player interacts with, so
    /// everything that matters here is that it is replaceable, has no collision and drops nothing.
    ///
    /// Deliberately no speedFactor, which is how soul sand and Vanilla's honey *block* slow you down.
    /// It was set here, and it braked twice over without saying so: Entity.move applies it inside the
    /// movement HoneyFluidType.move performs, so the block's factor and the fluid's drag multiplied
    /// together and a player in honey stopped dead. Thickness belongs in one place, and that place is
    /// the fluid.
    public static final DeferredBlock<LiquidBlock> HONEY_BLOCK = MelliferaBlocks.BLOCKS.registerBlock("honey",
        properties -> new LiquidBlock(HONEY.get(), properties),
        p -> p.mapColor(MapColor.COLOR_ORANGE)
            .replaceable()
            .noCollision()
            .strength(100.0F)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY));

    public static final DeferredItem<Item> HONEY_BUCKET = MelliferaItems.ITEMS.registerItem("honey_bucket",
        properties -> new BucketItem(HONEY.get(), properties.craftRemainder(Items.BUCKET).stacksTo(1)));

    private MelliferaFluids() {}

    /// The properties both fluids share. Built fresh for each because BaseFlowingFluid.Properties is
    /// consumed by the constructor, and the suppliers inside it are what let the source and the
    /// flowing fluid name each other before either exists.
    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(HONEY_TYPE, HONEY, HONEY_FLOWING)
            .block(HONEY_BLOCK)
            .bucket(HONEY_BUCKET)
            .slopeFindDistance(SLOPE_FIND_DISTANCE)
            .levelDecreasePerBlock(LEVEL_DECREASE_PER_BLOCK)
            .tickRate(TICK_RATE);
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
