package com.joaonf.mellifera.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.Foraging;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.bee.CombProduct;
import com.joaonf.mellifera.bee.EffectAllele;
import com.joaonf.mellifera.bee.FrameType;
import com.joaonf.mellifera.bee.ItemProduct;
import com.joaonf.mellifera.bee.MutationEngine;
import com.joaonf.mellifera.bee.MutationObjective;
import com.joaonf.mellifera.bee.QueenGenomeData;
import com.joaonf.mellifera.bee.ToleranceAllele;
import com.joaonf.mellifera.config.MelliferaOutputConfig;
import com.joaonf.mellifera.item.DroneBeeItem;
import com.joaonf.mellifera.item.FrameItem;
import com.joaonf.mellifera.item.HoneyCombItem;
import com.joaonf.mellifera.item.PrincessBeeItem;
import com.joaonf.mellifera.item.QueenBeeItem;
import com.joaonf.mellifera.registry.MelliferaBeeMutations;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaItems;
import com.joaonf.mellifera.temperature.EnvironmentTemperature;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/// The whole bee simulation, kept apart from the block that hosts it.
///
/// Kept separate from ApiaryBlockEntity, which is nothing but a slot layout and a menu.
/// The split is worth keeping now that the Apiary is the only housing left: everything in
/// here is parameterised on slot counts and a handful of overridable knobs, so a second
/// kind of hive is a subclass rather than a second copy of mating, production, frames,
/// lifespan and brood -- and two copies of that would drift apart the first time either
/// was touched, leaving them quietly disagreeing about what a bee does, which is the one
/// thing a genetics mod cannot afford.
///
/// Slot layout is fixed in shape and only variable in size: queen/princess (0), drone (1),
/// `frameSlots` frames, then `outputSlots` product slots.
///
/// serverTick, in order: mate a waiting princess+drone into a queen; gate production on
/// EnvironmentTemperature.celsius(level, pos) falling inside the species' band (widened by
/// ToleranceAllele) unless the housing ignores climate; advance the production timer (fixed
/// +1/tick, SpeedAllele instead scales how big progressTotal is, set once at mating -- this
/// avoids SpeedAllele's multiplier being crushed to +-1 by per-tick integer rounding); run
/// the queen's BeeEffect and a flowering pass on their own slower cadences; and once lifespan
/// runs out, replace her with a freshly bred princess and up to `fertility` drones, each an
/// independent MutationEngine roll.
public abstract class BeeHousingBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_QUEEN = 0;
    public static final int SLOT_DRONE = 1;
    public static final int SLOT_FRAME_START = 2;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_PROGRESS_TOTAL = 1;
    public static final int DATA_QUEEN_PRESENT = 2;
    public static final int DATA_LIFESPAN = 3;

    /// How many blocks worth foraging the last survey found. On the menu's data channel rather
    /// than the block entity's update packet: it is wanted by the one player who has the window
    /// open, not by everyone in render distance.
    public static final int DATA_FLOWERS = 4;
    public static final int NUM_DATA_VALUES = 5;

    /// Ticks a Normal-speed queen takes to fill the production timer. Other speeds scale
    /// this at mating time (see tryMate), not the per-tick increment.
    private static final int BASE_PRODUCTION_TICKS = 200;
    private static final int EFFECT_INTERVAL_TICKS = 200;
    private static final int FLOWERING_INTERVAL_TICKS = 100;

    /// Ticks between temperature samples. One second: short enough that walking a torch up to a
    /// hive still feels immediate, long enough to make the scan behind it irrelevant. See
    /// inClimate.
    private static final int CLIMATE_INTERVAL_TICKS = 20;

    /// How many bonemeal attempts a flowering pass makes at FloweringAllele.NORMAL (1.0x).
    private static final int BASE_FLOWERING_ATTEMPTS = 4;
    private static final int FLOWERING_VERTICAL_RANGE = 2;

    /// Ticks between forage surveys, and how far above and below the hive one looks.
    ///
    /// Ten seconds, on the same reasoning as the climate sample: what it asks is how flowery the
    /// neighbourhood is, and a neighbourhood does not change in a tick. A player who plants a
    /// bed of poppies wants to see it counted while they are still standing there, and ten
    /// seconds is well inside that.
    private static final int FORAGE_INTERVAL_TICKS = 200;
    private static final int FORAGE_VERTICAL_RANGE = 4;

    /// Flowers within territory needed for a hive to work at full speed.
    ///
    /// Twelve is about one flower per twenty columns at the smallest territory (radius 4, 81
    /// columns), which is a garden bed rather than a meadow. Deliberately reachable by hand: the
    /// point is to reward planting, not to gate the mod behind biome luck.
    ///
    /// None at all is not a slow hive, it is a stopped one -- see serverTick. That was the other
    /// way round until the Status panel could say why, which was the only thing making a dead
    /// hive indistinguishable from a broken mod.
    public static final int FLOWERS_FOR_FULL_SPEED = 12;

    /// The slowest a hive with *some* forage runs, at one flower in range.
    ///
    /// The floor for a hive that is working badly, not for one that is not working at all --
    /// nothing in reach stops it outright, and so does dusk, and so does rain (see serverTick).
    /// This is only the shape of the curve between one flower and enough of them.
    private static final float SPARSE_SPEED = 0.15F;

    private static final int[] NO_SLOTS = new int[0];

    private static final String TAG_WORKING = "working";
    private static final String TAG_SPECIES_COLOR = "species_color";
    private static final String TAG_TERRITORY = "territory";
    private static final String TAG_OBJECTIVE = "objective";

    private final int frameSlots;
    private final int outputSlots;
    private final int outputStart;
    private final int totalSlots;

    /// Every slot is offered to automation; canTakeItemThroughFace is what actually keeps
    /// bees and frames from being pulled out.
    private final int[] automationSlots;

    private NonNullList<ItemStack> items;

    private int progress;
    private int progressTotal;
    private int lifespanRemaining;
    private int effectCooldown;
    private int floweringCooldown;
    private boolean queenPresent;

    /// The last climate verdict and how long it stands for. See inClimate.
    private int climateCooldown;
    private boolean climateOk;

    /// Set by the Mellifera debug stick. Honoured once by the next tick, which then runs the
    /// ordinary pulse/death path -- the tool never produces bees itself, so it cannot drift
    /// away from what the real simulation does.
    private boolean forceCycle;
    private boolean forceDeath;

    /// The species this housing is aiming for, or null for none. Set from the GUI's
    /// objective panel (dragged in from JEI) and honoured by every mutation roll -- see
    /// MutationObjective for what it actually does to the odds.
    private @Nullable Identifier objective;

    /// The only three things the *client* is told about a hive, and the only reason this
    /// block entity has an update packet at all: enough to draw bees around a working hive
    /// and tint them by species. Deliberately not the inventory -- broadcasting a queen, her
    /// full genome and a bay of combs to everyone in render distance every time a comb is
    /// produced would be an absurd amount of traffic for a cosmetic effect.
    ///
    /// `working` is set only by serverTick, which bails on canRun() before ever reaching it.
    private boolean working;
    private int speciesColor;
    private int territory;

    /// Blocks within territory that a forager would fly to, resurveyed every
    /// FORAGE_INTERVAL_TICKS.
    ///
    /// Deliberately not saved, like climateOk: a hive whose chunk has just loaded surveys on its
    /// first tick, which is what a cooldown of zero means. Saving it would only preserve a stale
    /// answer about a neighbourhood that may have been mown, built over or set on fire while the
    /// chunk was unloaded.
    private int flowersInRange;
    private int forageCooldown;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case DATA_PROGRESS -> progress;
                case DATA_PROGRESS_TOTAL -> progressTotal;
                case DATA_LIFESPAN -> lifespanRemaining;
                case DATA_FLOWERS -> flowersInRange;
                default -> queenPresent ? 1 : 0;
            };
        }

        @Override
        public void set(int id, int value) {
            switch (id) {
                case DATA_PROGRESS -> progress = value;
                case DATA_PROGRESS_TOTAL -> progressTotal = value;
                case DATA_LIFESPAN -> lifespanRemaining = value;
                case DATA_FLOWERS -> flowersInRange = value;
                default -> queenPresent = value != 0;
            }
        }

        @Override
        public int getCount() {
            return NUM_DATA_VALUES;
        }
    };

    protected BeeHousingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int frameSlots, int outputSlots) {
        super(type, pos, state);
        this.frameSlots = frameSlots;
        this.outputSlots = outputSlots;
        this.outputStart = SLOT_FRAME_START + frameSlots;
        this.totalSlots = outputStart + outputSlots;
        this.automationSlots = IntStream.range(0, totalSlots).toArray();
        this.items = NonNullList.withSize(totalSlots, ItemStack.EMPTY);
    }

    // -- per-housing knobs ------------------------------------------------------------------
    //
    // Left as overridable hooks with a single implementation behind them. They cost nothing
    // and they are the seam a second kind of housing would slot into; collapsing them into
    // constants would have to be undone the day one is added.

    /// Blocks of reach added on top of the genome's TerritoryAllele.
    protected int territoryBonus() {
        return 0;
    }

    /// Degrees this housing adds to, or takes off, the ambient temperature before judging it
    /// against the queen's band. Zero unless the housing itself does something to the air.
    protected float temperatureOffset() {
        return 0.0F;
    }

    /// Where this housing's climate is read. The block above it by default, because the housing's
    /// own block is opaque and EnvironmentTemperature would see no sky from inside it.
    protected BlockPos climatePosition() {
        return worldPosition.above();
    }

    /// Multiplies how fast the production timer fills, stacking with installed frames.
    protected float housingSpeedMultiplier() {
        return 1.0F;
    }

    /// False disables the simulation entirely without disabling the block entity.
    protected boolean canRun() {
        return true;
    }

    /// True when the queen is sheltered from the species' temperature band, letting a
    /// Tropical line work in a tundra.
    ///
    /// A property of what is installed rather than of the building. This used to be the
    /// Alveary's whole reason to exist and it was overridden to a constant there; with the
    /// Alveary gone it lives on an Insulation frame instead, which means it can be added to
    /// and taken out of any hive, and costs one of the three frame slots for as long as it
    /// is in.
    private boolean ignoresClimate() {
        return anyFrame(FrameType::insulates);
    }

    public int frameSlots() {
        return frameSlots;
    }

    public int outputSlots() {
        return outputSlots;
    }

    /// How many of the frame and output slots are currently usable.
    ///
    /// Separate from frameSlots/outputSlots, which are the container's *allocated* size and
    /// never change. A stacked apiary grows and shrinks as blocks are added and taken away,
    /// and rebuilding the inventory each time would mean rewriting the NBT layout under a
    /// live block and finding somewhere to put whatever was in the slots that vanished. So
    /// the container is always the biggest it could ever need to be and the extra slots are
    /// simply switched off: nothing can be put in them, nothing is produced into them, and
    /// the menu does not draw them.
    ///
    /// The one thing this does not do by itself is empty a slot that has just been switched
    /// off -- see ApiaryBlockEntity.dropInactive, which is called when a tower shrinks.
    public int activeFrameSlots() {
        return frameSlots;
    }

    public int activeOutputSlots() {
        return outputSlots;
    }

    public int outputStart() {
        return outputStart;
    }

    protected ContainerData containerData() {
        return data;
    }

    // -- breeding objective -----------------------------------------------------------------

    public @Nullable Identifier objective() {
        return objective;
    }

    /// Null (or an id no longer in the registry) clears it. Nothing about the current queen
    /// changes: the objective only ever affects mutation rolls, which happen when she dies
    /// and her brood is bred, so retargeting mid-cycle takes effect on the next generation.
    ///
    /// Broadcasts as well as saving, because the objective panel draws what the server has
    /// rather than what the player clicked: it reaches the client through the update tag
    /// (see getUpdateTag), and without a re-broadcast the panel would sit on a stale value
    /// until something else about the hive changed.
    public void setObjective(@Nullable Identifier species) {
        Identifier resolved = species != null && MelliferaBeeSpecies.REGISTRY.containsKey(species) ? species : null;
        if (Objects.equals(objective, resolved)) {
            return;
        }

        objective = resolved;
        setChanged();

        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // -- simulation -------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, BeeHousingBlockEntity housing) {
        if (!housing.canRun()) {
            return;
        }

        housing.tryMate();

        boolean nowPresent = housing.items.get(SLOT_QUEEN).getItem() instanceof QueenBeeItem;
        if (nowPresent != housing.queenPresent) {
            housing.queenPresent = nowPresent;
            housing.setChanged();
        }

        if (!nowPresent) {
            housing.syncActivity(level, pos, state, false, 0, 0);
            return;
        }

        QueenGenomeData queenData = housing.items.get(SLOT_QUEEN).getOrDefault(MelliferaDataComponents.QUEEN_GENOME.get(), QueenGenomeData.defaultGenome());
        BeeGenome ownGenome = queenData.own();
        BeeSpecies species = MelliferaBeeSpecies.get(ownGenome.species().active());

        // Surveyed before the gate, not inside it, because a hive that has stopped for want of
        // flowers is exactly the hive that has to notice the ones planted in front of it.
        housing.surveyForage(level, pos, housing.territoryRadius(ownGenome));

        // Every condition is a gate, not a discount: a hive with nothing to forage, or with its
        // foragers in for the night or the rain, stops. Nothing is coming in, so nothing comes
        // out. The one thing that still scales rather than stops is *how much* forage there is,
        // because thin forage is less of a good thing rather than a thing being wrong.
        boolean climate = housing.ignoresClimate() || housing.inClimate(level, pos, ownGenome, species);
        boolean producing = climate
            && housing.flowersInRange > 0
            && Foraging.flying(level, pos);
        housing.syncActivity(level, pos, state, producing, species.primaryColor(), housing.territoryRadius(ownGenome));

        if (producing) {
            housing.progress++;
            float speed = housing.frameSpeedMultiplier()
                * housing.housingSpeedMultiplier()
                * housing.forageSpeedMultiplier();
            int threshold = Math.max(1, Math.round(housing.progressTotal / speed));
            boolean forced = housing.forceCycle || housing.forceDeath;
            if ((forced || housing.progress >= threshold) && housing.producePulse(ownGenome, level.getRandom())) {
                housing.progress = 0;
                housing.depleteFrames();
                boolean terminated = housing.anyFrame(FrameType::terminates);
                housing.lifespanRemaining = (housing.forceDeath || terminated) ? 0 : housing.lifespanRemaining - 1;
                housing.forceCycle = false;
                housing.forceDeath = false;
                if (housing.lifespanRemaining <= 0) {
                    housing.die(level, pos, queenData);
                }
            }

            housing.setChanged();
        }

        housing.tickEffect(level, pos, ownGenome);
        housing.tickFlowering(level, pos, ownGenome);
    }

    /// Whether the temperature here is inside this queen's band, resampled once a second rather
    /// than on every tick.
    ///
    /// EnvironmentTemperature.celsius scans a sphere of blocks and blends nine biome lookups,
    /// and this used to run twenty times a second on every working hive in the world. What it
    /// asks is whether a slow number is still inside a band: the biome cannot change, the
    /// altitude cannot, the sun takes twenty real minutes to cross its range, and a torch
    /// carried up to the hive deserves to be noticed within the second rather than within the
    /// tick. Nothing a player can observe separates the two, and a second's worth of ticks is a
    /// twentieth of the work.
    ///
    /// Deliberately not saved: a hive whose chunk has just loaded samples on its first tick,
    /// which is what a cooldown of zero means. tryMate resets it so a queen is never judged by
    /// the band of the one before her.
    private boolean inClimate(Level level, BlockPos pos, BeeGenome genome, BeeSpecies species) {
        if (--climateCooldown > 0) {
            return climateOk;
        }

        climateCooldown = CLIMATE_INTERVAL_TICKS;

        // The housing's own contribution goes on the *measurement*, not on the band: a ventilated
        // hive is a cooler place, it is not a queen who has learnt to like heat. Widening the band
        // instead would have been indistinguishable here and wrong everywhere the number is shown.
        float celsius = EnvironmentTemperature.celsius(level, climatePosition()) + temperatureOffset();
        ToleranceAllele tolerance = genome.tolerance().active();
        climateOk = celsius >= species.minCelsius() - tolerance.widenBelow()
            && celsius <= species.maxCelsius() + tolerance.widenAbove();
        return climateOk;
    }

    // -- client-visible activity --------------------------------------------------------------

    /// Pushes the three cosmetic fields to everyone watching, but *only* when one of them
    /// actually changed. This runs every tick on every hive in the world, so an unconditional
    /// block update here would be a packet per hive per tick; in practice a value only moves
    /// when a queen is mated, dies, or the climate crosses her band, which is a handful of
    /// packets per hive per lifetime.
    ///
    /// Colour and territory are left untouched while idle so that a hive whose queen has just
    /// died does not have to re-send them the moment the next one is mated in.
    private void syncActivity(Level level, BlockPos pos, BlockState state, boolean nowWorking, int color, int radius) {
        boolean changed = nowWorking != working
            || (nowWorking && (color != speciesColor || radius != territory));
        if (!changed) {
            return;
        }

        boolean startedOrStopped = nowWorking != working;
        working = nowWorking;
        if (nowWorking) {
            speciesColor = color;
            territory = radius;
        }

        setChanged();
        // Same state in and out: this exists purely to make the chunk holder re-broadcast the
        // block entity's update tag (see getUpdateTag), not to change the block.
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);

        if (startedOrStopped) {
            onWorkingChanged(level, pos, state, nowWorking);
        }
    }

    /// Called when a hive starts or stops producing, and only then -- not when its colour or
    /// territory move. A housing that shows the difference on the block itself overrides this;
    /// the base does nothing, because nothing here knows what block it is sitting in.
    protected void onWorkingChanged(Level level, BlockPos pos, BlockState state, boolean nowWorking) {}

    /// Whether this housing should have foragers in the air around it.
    ///
    /// The questions the renderer would otherwise have to ask separately, and the only ones it is
    /// allowed to ask: is the hive producing, is this block entity the one that runs the hive at
    /// all (a three-high apiary has three of them -- see ApiaryBlockEntity.canRun), and are bees
    /// flying at this hour and in this weather.
    ///
    /// That last one costs nothing to sync because it is not synced: Foraging.flying reads an
    /// environment attribute, and the time of day and the weather are things the client already
    /// knows. Server and client reach the same answer by asking the same question of the same
    /// world, which is the only arrangement in which the bees a player watches cannot contradict
    /// the hive they belong to.
    ///
    /// Everything past this point is drawn from the two synced cosmetic fields below and from
    /// blocks the client already has; there is no inventory and no genome on this side.
    public boolean showsBees() {
        return working && canRun() && level != null && Foraging.flying(level, worldPosition);
    }

    /// The producing species' colour, for tinting those bees. Meaningless unless showsBees().
    public int speciesColor() {
        return speciesColor;
    }

    /// The queen's territory radius, which is how far those bees may forage.
    public int territory() {
        return territory;
    }

    private void tryMate() {
        ItemStack princessStack = items.get(SLOT_QUEEN);
        ItemStack droneStack = items.get(SLOT_DRONE);
        if (!(princessStack.getItem() instanceof PrincessBeeItem) || !(droneStack.getItem() instanceof DroneBeeItem)) {
            return;
        }

        BeeGenome princessGenome = princessStack.getOrDefault(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.defaultGenome());
        BeeGenome droneGenome = droneStack.getOrDefault(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.defaultGenome());

        // A princess does NOT change when she mates -- she keeps her own genome verbatim and
        // merely remembers the drone's. Mendelian mixing belongs to the *offspring* (see
        // breed()), not to her. Blending here is what made a Meadows princess mated to a
        // Forest drone come out as a Forest queen, which is nonsense: it destroyed the line
        // you were breeding the moment you mated it.
        BeeGenome ownGenome = princessGenome;

        ItemStack queenStack = new ItemStack(MelliferaItems.QUEEN_BEE.get());
        queenStack.set(MelliferaDataComponents.QUEEN_GENOME.get(), new QueenGenomeData(ownGenome, droneGenome));

        items.set(SLOT_QUEEN, queenStack);

        // One drone per mating, not the whole stack. A stack of 16 drones should be 16
        // matings' worth of fuel, not one.
        droneStack.shrink(1);

        lifespanRemaining = ownGenome.lifespan().active().cycles();
        progress = 0;
        progressTotal = Math.round(BASE_PRODUCTION_TICKS / ownGenome.speed().active().multiplier());
        effectCooldown = EFFECT_INTERVAL_TICKS;
        floweringCooldown = FLOWERING_INTERVAL_TICKS;
        // Zero, not the interval: this queen's band is her own, and she must not spend her first
        // second being judged by the last one's. See inClimate.
        climateCooldown = 0;
        setChanged();
    }

    /// Inserts up to `fertility` combs into free/matching output slots. Returns false (and
    /// leaves progress at its post-threshold value, so the caller retries next tick) if not
    /// even one comb found room -- a full output bay stalls the pulse instead of losing it.
    /// Rolls the queen's species' whole product table, not one guaranteed comb: each entry
    /// carries its own chance straight from Forestry (0.20 for a branch root, 0.55 for
    /// Fiendish), and a species with two products -- Austere's Parched *and* Powdery -- can
    /// yield both in one pulse. Fertility caps how many combs a single pulse can insert.
    ///
    /// Returns true if anything at all landed, which is what advances the cycle: a pulse
    /// that rolled nothing still counts as work done, otherwise a low-chance queen would
    /// never age.
    private boolean producePulse(BeeGenome genome, RandomSource random) {
        BeeSpecies species = MelliferaBeeSpecies.get(genome.species().active());

        // Productivity frames buy extra passes over the comb table, not a bigger budget.
        //
        // A bigger budget would do nothing for most bees: the loop below visits each of the
        // species' products once, and nearly every species has fewer products than a queen's
        // fertility already allows, so the budget is not what limits output. Another pass is.
        //
        // The fractional part is rolled rather than truncated, so +15% really is +15% on
        // average instead of rounding away to nothing on a single frame.
        float passes = frameCombMultiplier();
        int whole = (int) passes;
        for (int pass = 0; pass < whole; pass++) {
            rollCombs(genome, random);
        }
        if (random.nextFloat() < passes - whole) {
            rollCombs(genome, random);
        }

        // Non-comb output (royal jelly, pollen, ice shards, peat...). Rolled outside the
        // fertility budget on purpose: these are rare side-drops, and letting them compete
        // with combs for the same budget would quietly cut a queen's comb rate.
        for (ItemProduct extra : MelliferaOutputConfig.productsOf(genome.species().active())) {
            ItemStack rolled = extra.roll(random);
            if (!rolled.isEmpty()) {
                insertOutput(rolled);
            }
        }

        return true;
    }

    /// One sweep of the species' comb table, bounded by the queen's fertility.
    private void rollCombs(BeeGenome genome, RandomSource random) {
        int budget = genome.fertility().active().offspring();
        for (CombProduct product : MelliferaOutputConfig.combsOf(genome.species().active())) {
            if (budget <= 0) {
                return;
            }

            if (random.nextFloat() < product.chance() && insertComb(product.comb())) {
                budget--;
            }
        }
    }

    private void insertOutput(ItemStack stack) {
        for (int slot = outputStart; slot < outputStart + activeOutputSlots(); slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                items.set(slot, stack);
                return;
            }

            if (ItemStack.isSameItemSameComponents(existing, stack)
                && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                existing.grow(stack.getCount());
                return;
            }
        }
    }

    private boolean insertComb(Identifier combType) {
        for (int slot = outputStart; slot < outputStart + activeOutputSlots(); slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                ItemStack comb = new ItemStack(MelliferaItems.HONEY_COMB.get());
                comb.set(MelliferaDataComponents.COMB_TYPE.get(), combType);
                items.set(slot, comb);
                return true;
            }

            if (existing.getItem() instanceof HoneyCombItem
                && existing.getCount() < existing.getMaxStackSize()
                && combType.equals(existing.get(MelliferaDataComponents.COMB_TYPE.get()))) {
                existing.grow(1);
                return true;
            }
        }

        return false;
    }

    /// The frame working in this slot, or null when the slot is empty or its frame is spent.
    /// The one definition of "which frames are actually working"; everything frame-driven asks
    /// this and nothing else.
    ///
    /// A getter rather than the forEachFrame(Consumer) this used to be. Two of the five callers
    /// below run on every tick of every working hive, and each call was allocating a boxing array
    /// for the accumulator plus a capturing lambda -- so the shape that read most naturally was
    /// also the one that put two objects per hive per tick on the heap to add up at most nine
    /// floats. Plain loops over a nullable getter cost nothing and read no worse.
    private @Nullable FrameType frameAt(int slot) {
        ItemStack frame = items.get(slot);
        if (frame.getItem() instanceof FrameItem frameItem
            && frame.getOrDefault(MelliferaDataComponents.FRAME_WEAR.get(), FrameItem.FRESH_WEAR) > 0.0F) {
            return frameItem.type();
        }

        return null;
    }

    private int frameEnd() {
        return SLOT_FRAME_START + activeFrameSlots();
    }

    private float frameSpeedMultiplier() {
        float bonus = 0.0F;
        for (int slot = SLOT_FRAME_START; slot < frameEnd(); slot++) {
            FrameType type = frameAt(slot);
            if (type != null) {
                bonus += type.speedBonus();
            }
        }

        return 1.0F + bonus;
    }

    /// How many sweeps of the comb table this hive gets per pulse. Frames add, so three
    /// Productivity frames are 1.45 and not 1.15 cubed.
    private float frameCombMultiplier() {
        float bonus = 0.0F;
        for (int slot = SLOT_FRAME_START; slot < frameEnd(); slot++) {
            FrameType type = frameAt(slot);
            if (type != null) {
                bonus += type.combBonus();
            }
        }

        return 1.0F + bonus;
    }

    private float mutationMultiplier() {
        float multiplier = 1.0F;
        for (int slot = SLOT_FRAME_START; slot < frameEnd(); slot++) {
            FrameType type = frameAt(slot);
            if (type != null) {
                multiplier *= type.mutationMultiplier();
            }
        }

        return multiplier;
    }

    /// Dominant and Recessive frames pull in opposite directions; installing both cancels
    /// back to ordinary random inheritance rather than letting slot order silently decide.
    private FrameType.Inheritance inheritanceMode() {
        boolean active = false;
        boolean inactive = false;

        for (int slot = SLOT_FRAME_START; slot < frameEnd(); slot++) {
            FrameType type = frameAt(slot);
            if (type == null) {
                continue;
            }

            if (type.inheritance() == FrameType.Inheritance.ACTIVE) {
                active = true;
            } else if (type.inheritance() == FrameType.Inheritance.INACTIVE) {
                inactive = true;
            }
        }

        if (active == inactive) {
            return FrameType.Inheritance.RANDOM;
        }

        return active ? FrameType.Inheritance.ACTIVE : FrameType.Inheritance.INACTIVE;
    }

    /// Stops at the first frame that answers yes, which the Consumer form could not do.
    private boolean anyFrame(Predicate<FrameType> test) {
        for (int slot = SLOT_FRAME_START; slot < frameEnd(); slot++) {
            FrameType type = frameAt(slot);
            if (type != null && test.test(type)) {
                return true;
            }
        }

        return false;
    }

    /// Wears every occupied frame down by one pulse's worth, pulling any that's now spent --
    /// a frame is consumed by being used, not by sitting there.
    private void depleteFrames() {
        for (int slot = SLOT_FRAME_START; slot < SLOT_FRAME_START + activeFrameSlots(); slot++) {
            ItemStack frame = items.get(slot);
            if (!(frame.getItem() instanceof FrameItem)) {
                continue;
            }

            // An Unbreaking frame is exempt from wear entirely, not merely slowed.
            if (FrameItem.neverWears(frame)) {
                continue;
            }

            float wear = frame.getOrDefault(MelliferaDataComponents.FRAME_WEAR.get(), FrameItem.FRESH_WEAR)
                - ((FrameItem) frame.getItem()).type().wearPerPulse();
            if (wear <= 0.0F) {
                items.set(slot, ItemStack.EMPTY);
            } else {
                frame.set(MelliferaDataComponents.FRAME_WEAR.get(), wear);
            }
        }
    }

    private void die(Level level, BlockPos pos, QueenGenomeData queenData) {
        ServerLevel serverLevel = (ServerLevel) level;
        RandomSource random = level.getRandom();
        int fertility = queenData.own().fertility().active().offspring();

        // The queen is gone: clear the input slots and put the whole brood in the output.
        //
        // Deliberately NOT back into SLOT_QUEEN/SLOT_DRONE. Doing that made the housing
        // instantly re-mate the new princess with her own brother on the very next tick, so
        // a queen was always present -- she looked like she never died -- and any offspring
        // that had actually mutated was consumed before the player could ever see it. In
        // Forestry the brood lands in the output and the hive sits idle until you choose
        // which princess and which drone to pair, which is the entire point of breeding.
        // Only the queen is cleared. The drone slot keeps whatever is left of the player's
        // stack: mating already consumed exactly one drone, and wiping the slot here threw
        // away the other 63 of a full stack. With the princess going to the output the queen
        // slot is empty anyway, so nothing can re-mate on its own.
        items.set(SLOT_QUEEN, ItemStack.EMPTY);

        FrameType.Inheritance mode = inheritanceMode();
        float mutationBoost = mutationMultiplier();
        boolean automate = anyFrame(FrameType::automates);

        // Resolved once for the whole brood rather than per offspring: it is the same
        // objective for all of them, and building it walks the entire mutation table.
        MutationObjective goal = objective == null ? null : MutationObjective.of(objective, MelliferaBeeMutations.all());

        ItemStack princess = breed(serverLevel, pos, random, queenData, MelliferaItems.PRINCESS_BEE.get(), mode, mutationBoost, goal);
        if (automate) {
            items.set(SLOT_QUEEN, princess);
        } else {
            outputOrDrop(level, pos, princess);
        }

        List<ItemStack> drones = new ArrayList<>(fertility);
        for (int i = 0; i < fertility; i++) {
            drones.add(breed(serverLevel, pos, random, queenData, MelliferaItems.DRONE_BEE.get(), mode, mutationBoost, goal));
        }

        // With an Automation frame, the drone slot gets first refusal on whichever sibling
        // is furthest along towards the objective, rather than on whichever happened to be
        // bred first. Everything after that is unchanged, which is what keeps the ordinary
        // rules intact: the slot still only accepts a bee it can legitimately hold, and the
        // siblings it turns down still go to the output.
        for (ItemStack drone : bestFirst(drones, goal)) {
            if (automate && offerToDroneSlot(drone)) {
                continue;
            }

            outputOrDrop(level, pos, drone);
        }

        queenPresent = false;
        lifespanRemaining = 0;
        progress = 0;
        progressTotal = 0;
    }

    /// Breeds one offspring stack: ordinary Mendelian inheritance, then a single mutation
    /// roll on top.
    ///
    /// Deliberately does *not* stamp a glint on freshly mutated bees. The glint belongs to
    /// the species itself in Forestry (`setHasEffect()` -- Imperial, Industrious, Austere
    /// and the other branch tops always shimmer, however you obtained them), so it is
    /// resolved at render time from the genome instead; see PrincessBeeItem.isFoil.
    private static ItemStack breed(ServerLevel level, BlockPos pos, RandomSource random, QueenGenomeData queenData,
                                   Item item, FrameType.Inheritance mode, float mutationMultiplier,
                                   @Nullable MutationObjective objective) {
        BeeGenome inherited = BeeGenome.inherited(random, queenData.own(), queenData.mate(), mode);
        BeeGenome result = MutationEngine.tryMutate(level, pos, random, inherited, MelliferaBeeMutations.all(), mutationMultiplier, objective);

        ItemStack stack = new ItemStack(item);
        stack.set(MelliferaDataComponents.BEE_GENOME.get(), result);
        return stack;
    }

    /// The brood ordered so the bee closest to the objective is offered the drone slot
    /// first. Returned untouched when there is no objective, which leaves the no-objective
    /// case bit-for-bit what it was: bred order, first one fills the slot, identical
    /// siblings stack onto it.
    ///
    /// Reordering only when an objective is set is the point. The rule that the drone slot
    /// takes only a genetically identical bee exists so automation cannot quietly re-mate
    /// the queen with a bee the player never chose -- but an objective *is* the player
    /// choosing, stated in advance. Honouring it here is the difference between an
    /// Automation frame that runs a line towards a goal and one that just keeps the hive
    /// warm with whatever came out first.
    ///
    /// Stable sort, so siblings the objective ranks equally keep their bred order.
    private static List<ItemStack> bestFirst(List<ItemStack> drones, @Nullable MutationObjective goal) {
        if (goal == null || drones.size() < 2) {
            return drones;
        }

        List<ItemStack> ordered = new ArrayList<>(drones);
        ordered.sort(Comparator.comparingInt(drone -> {
            BeeGenome genome = drone.get(MelliferaDataComponents.BEE_GENOME.get());
            return genome == null ? MutationObjective.UNREACHABLE : goal.steps(genome);
        }));

        return ordered;
    }

    /// Puts a drone back in the drone slot only if it genuinely belongs there: the slot is
    /// empty, or the drone already sitting there is the same bee down to its genome and has
    /// room to stack.
    ///
    /// Siblings out of one brood are independent mutation rolls, so they are not necessarily
    /// identical. Pairing whichever one happened to come out first would quietly re-mate the
    /// queen with a bee the player never chose, which is the whole thing the manual flow
    /// exists to prevent.
    private boolean offerToDroneSlot(ItemStack drone) {
        ItemStack existing = items.get(SLOT_DRONE);
        if (existing.isEmpty()) {
            items.set(SLOT_DRONE, drone);
            return true;
        }

        if (ItemStack.isSameItemSameComponents(existing, drone)
            && existing.getCount() + drone.getCount() <= existing.getMaxStackSize()) {
            existing.grow(drone.getCount());
            return true;
        }

        return false;
    }

    /// Brood goes to a free output slot, or on the floor if the bay is full -- never into
    /// an input slot, and never silently deleted.
    ///
    /// Merges into a matching stack before taking a fresh slot. A brood is several siblings
    /// at once and identical ones were each claiming a slot of their own, so a fertile queen
    /// could fill the whole seven-slot bay with what is really two or three distinct bees --
    /// and then start dropping the rest on the floor.
    ///
    /// isSameItemSameComponents is exactly the right test and needs no special casing for
    /// bees: the genome rides on the stack as a data component, so two siblings merge only
    /// when they are genetically identical, and the mutated one that is the entire point of
    /// the brood is never swallowed into a pile of its ordinary siblings.
    private void outputOrDrop(Level level, BlockPos pos, ItemStack stack) {
        for (int slot = outputStart; slot < outputStart + activeOutputSlots(); slot++) {
            ItemStack existing = items.get(slot);
            if (ItemStack.isSameItemSameComponents(existing, stack)
                && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                existing.grow(stack.getCount());
                return;
            }
        }

        for (int slot = outputStart; slot < outputStart + activeOutputSlots(); slot++) {
            if (items.get(slot).isEmpty()) {
                items.set(slot, stack);
                return;
            }
        }

        Block.popResource(level, pos.above(), stack);
    }

    /// Reach of the queen's effect and flowering pass: her genome plus whatever the housing
    /// itself adds.
    private int territoryRadius(BeeGenome genome) {
        return genome.territory().active().radius() + territoryBonus();
    }

    private void tickEffect(Level level, BlockPos pos, BeeGenome genome) {
        EffectAllele effect = genome.effect().active();
        if (effect == EffectAllele.NONE) {
            return;
        }

        effectCooldown--;
        if (effectCooldown > 0) {
            return;
        }

        effectCooldown = EFFECT_INTERVAL_TICKS;
        effect.effect().apply((ServerLevel) level, pos, territoryRadius(genome));
    }

    /// How much of full speed this hive's surroundings are worth.
    ///
    /// Only reached when the hive is producing at all, which means there is at least one flower
    /// in range -- so this never returns zero and the threshold below can never divide by it.
    /// Everything that can stop a hive is a gate in serverTick, not a number here.
    ///
    /// This is what makes the foragers mean something. Before it, a hive in a desert and a hive
    /// in a meadow produced identically, and the bees a player watched fly out to real flowers
    /// were pure decoration over a simulation that had never heard of flowers.
    private float forageSpeedMultiplier() {
        float flowers = Math.min(1.0F, (float) flowersInRange / FLOWERS_FOR_FULL_SPEED);
        return SPARSE_SPEED + (1.0F - SPARSE_SPEED) * flowers;
    }

    /// Counts what a forager would fly to, over the same box the flowering pass works.
    ///
    /// A full sweep rather than the sampling HiveSwarm does, because the two are answering
    /// different questions: a bee only has to find *a* flower, and picking the nearest every time
    /// would make it look like a machine, whereas this has to produce a number that does not
    /// jitter between surveys. Sampling here would make a hive's speed flicker with nothing in
    /// the world having changed.
    ///
    /// Counting stops at the cap, so the cost is bounded by how flowery the place is rather than
    /// by how large the territory is: a meadow is a handful of lookups, and only a genuinely
    /// barren site pays for the whole box, once every ten seconds.
    private void surveyForage(Level level, BlockPos pos, int radius) {
        if (--forageCooldown > 0) {
            return;
        }

        forageCooldown = FORAGE_INTERVAL_TICKS;

        int found = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius && found < FLOWERS_FOR_FULL_SPEED; x++) {
            for (int z = -radius; z <= radius && found < FLOWERS_FOR_FULL_SPEED; z++) {
                for (int y = -FORAGE_VERTICAL_RANGE; y <= FORAGE_VERTICAL_RANGE; y++) {
                    cursor.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (Foraging.attracts(level.getBlockState(cursor))) {
                        found++;
                        break; // one per column, so a tall sunflower is not worth two poppies
                    }
                }
            }
        }

        flowersInRange = found;
    }

    /// Nudges a random crop/sapling within territory toward its next growth stage, the same
    /// way bonemeal would. Attempt count scales with FloweringAllele; radius and cadence
    /// don't, so the trait stays legible as "how often it helps" rather than "how far."
    private void tickFlowering(Level level, BlockPos pos, BeeGenome genome) {
        floweringCooldown--;
        if (floweringCooldown > 0) {
            return;
        }

        floweringCooldown = FLOWERING_INTERVAL_TICKS;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int radius = territoryRadius(genome);
        int attempts = Math.max(1, Math.round(BASE_FLOWERING_ATTEMPTS * genome.flowering().active().multiplier()));
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int i = 0; i < attempts; i++) {
            cursor.set(
                pos.getX() + random.nextInt(-radius, radius + 1),
                pos.getY() + random.nextInt(-FLOWERING_VERTICAL_RANGE, FLOWERING_VERTICAL_RANGE + 1),
                pos.getZ() + random.nextInt(-radius, radius + 1));

            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof BonemealableBlock bonemealable
                && bonemealable.isValidBonemealTarget(level, cursor, state)
                && bonemealable.isBonemealSuccess(level, random, cursor, state)) {
                bonemealable.performBonemeal(serverLevel, random, cursor, state);
            }
        }
    }

    // -- container ------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return items.size();
    }

    /// A loop rather than a stream, because this is not ours to call sparingly: Vanilla's hopper
    /// logic asks every container it touches whether it is empty, on its own cadence, and a
    /// stream pipeline per question is an allocation to answer something the first non-empty slot
    /// already settles.
    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) {
            setChanged();
        }

        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        items.set(slot, itemStack);
        itemStack.limitSize(getMaxStackSize(itemStack));
        setChanged();
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /// Whether a slot index is one the housing is currently using at all -- see
    /// activeFrameSlots. A switched-off slot accepts nothing from any source.
    private boolean withinActive(int slot) {
        if (slot >= SLOT_FRAME_START && slot < outputStart) {
            return slot < SLOT_FRAME_START + activeFrameSlots();
        }

        if (slot >= outputStart) {
            return slot < outputStart + activeOutputSlots();
        }

        return true;
    }

    /// Static, so the *client* menu can enforce the same rule.
    ///
    /// The client builds its menu over a plain SimpleContainer, which accepts anything, so
    /// a rule that lived only on the block entity was invisible client-side: a drone would
    /// visibly drop into the queen slot and only jump to the drone slot once the server
    /// corrected it.
    ///
    /// Takes the frame count because the output bay starts right after the frames, and each
    /// housing has a different number of them.
    protected static boolean isValidForSlot(int slot, ItemStack itemStack, int frameSlots) {
        Item item = itemStack.getItem();
        if (slot == SLOT_QUEEN) {
            return item instanceof PrincessBeeItem || item instanceof QueenBeeItem;
        }

        if (slot == SLOT_DRONE) {
            return item instanceof DroneBeeItem;
        }

        if (slot >= SLOT_FRAME_START && slot < SLOT_FRAME_START + frameSlots) {
            return item instanceof FrameItem;
        }

        // Output slots are production output only, same as a furnace's output slot.
        return false;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack itemStack) {
        return canRun() && withinActive(slot) && isValidForSlot(slot, itemStack, frameSlots);
    }

    // -- debug ------------------------------------------------------------------------

    /// Runs one production cycle on the next tick instead of waiting it out.
    public void debugCycle() {
        forceCycle = true;
        setChanged();
    }

    /// Ends the queen on the next tick: she produces, then dies and leaves her brood, so
    /// the mutation roll happens exactly as it normally would.
    public void debugKillQueen() {
        forceDeath = true;
        setChanged();
    }

    public boolean hasQueen() {
        return queenPresent;
    }

    // -- automation ------------------------------------------------------------------------

    /// Hoppers and pipes see every slot, but only the output bay may be *taken* from.
    ///
    /// Without this a hopper underneath would happily suck out the queen herself, the
    /// frames you just installed, and the princess/drones the moment a queen dies -- which
    /// silently destroys the line you were breeding. Harvesting the honey should never cost
    /// you the hive.
    @Override
    public int[] getSlotsForFace(Direction direction) {
        return canRun() ? automationSlots : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack itemStack, @Nullable Direction direction) {
        return canPlaceItem(slot, itemStack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
        return canRun() && slot >= outputStart && slot < outputStart + activeOutputSlots();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
            && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            Containers.dropContents(level, pos, this);
        }
    }

    /// Spills everything and resets the cycle. Used when a multiblock housing loses a block
    /// and stops being a hive: the contents must not vanish with the structure.
    protected void spillAndReset() {
        if (level != null && !level.isClientSide()) {
            Containers.dropContents(level, worldPosition, this);
        }

        items.clear();
        progress = 0;
        progressTotal = 0;
        lifespanRemaining = 0;
        queenPresent = false;
        forceCycle = false;
        forceDeath = false;

        // A block that stops being the controller will never run serverTick again, so this is
        // the last chance to tell the client to stop drawing bees around it.
        if (working) {
            working = false;
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // -- persistence ----------------------------------------------------------------------

    /// Only the cosmetic fields and the objective, hand-built rather than routed through
    /// saveAdditional: the update tag is what gets broadcast to every player in render
    /// distance, and the inventory has no business being in it.
    ///
    /// The client reads these straight back out through loadAdditional -- NeoForge's default
    /// onDataPacket/handleUpdateTag both funnel the tag into loadWithComponents -- which is
    /// why the keys here have to match the ones loadAdditional expects.
    ///
    /// The objective travels here rather than in a container data slot, which is where it
    /// used to live. A data slot is a short on the wire (see ClientboundContainerSetDataPacket),
    /// so the only thing that fits in one is an index into the species registry -- and an
    /// index means the two sides have to agree on registration order, which holds exactly as
    /// long as every species is declared in Java. It is also strictly more state than
    /// necessary: the id is already in this tag's neighbourhood, already survives a restart,
    /// and is already what the disk format uses.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_WORKING, working);
        tag.putInt(TAG_SPECIES_COLOR, speciesColor);
        tag.putInt(TAG_TERRITORY, territory);
        if (objective != null) {
            tag.putString(TAG_OBJECTIVE, objective.toString());
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putBoolean(TAG_WORKING, working);
        output.putInt(TAG_SPECIES_COLOR, speciesColor);
        output.putInt(TAG_TERRITORY, territory);
        output.putInt("progress", progress);
        output.putInt("progress_total", progressTotal);
        output.putInt("lifespan_remaining", lifespanRemaining);
        output.putInt("effect_cooldown", effectCooldown);
        output.putInt("flowering_cooldown", floweringCooldown);
        if (objective != null) {
            // The id, which is now what every copy of this value is: on disk here, on the
            // wire in getUpdateTag, and in the payload that sets it. It used to be the only
            // one -- the two network hops carried a registry index -- and an index is stable
            // only for as long as every species is declared in Java and both sides therefore
            // build the registry in the same order.
            output.putString(TAG_OBJECTIVE, objective.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(totalSlots, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        // Also the client's entry point for the update tag, which carries these three and
        // nothing else -- every other field below simply falls back to its default there,
        // which is correct: the client neither has nor needs the simulation's state.
        working = input.getBooleanOr(TAG_WORKING, false);
        speciesColor = input.getIntOr(TAG_SPECIES_COLOR, 0);
        territory = input.getIntOr(TAG_TERRITORY, 0);
        progress = input.getIntOr("progress", 0);
        progressTotal = input.getIntOr("progress_total", 0);
        lifespanRemaining = input.getIntOr("lifespan_remaining", 0);
        effectCooldown = input.getIntOr("effect_cooldown", 0);
        floweringCooldown = input.getIntOr("flowering_cooldown", 0);
        // Parse rather than assume: a saved objective naming a species that no longer
        // exists (mod list changed under the world) resolves to nothing and is dropped,
        // which is the same thing setObjective would have done with it.
        objective = Identifier.tryParse(input.getStringOr(TAG_OBJECTIVE, ""));
        if (objective != null && !MelliferaBeeSpecies.REGISTRY.containsKey(objective)) {
            objective = null;
        }
        queenPresent = items.get(SLOT_QUEEN).getItem() instanceof QueenBeeItem;
    }
}
