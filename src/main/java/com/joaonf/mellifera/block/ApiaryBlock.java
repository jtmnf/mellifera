package com.joaonf.mellifera.block;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/// One apiary, or several stacked into one hive.
///
/// The column is discovered rather than declared: there is no "form the multiblock" step and
/// no controller item. Put an apiary on top of another and they are one hive; break the top
/// one and the rest carry on. A structure a player can build by accident and take apart
/// without ceremony is worth more here than one that has to be assembled correctly, because
/// the thing being built is a stack of boxes and everyone already knows how those work.
public class ApiaryBlock extends BaseEntityBlock {
    public static final MapCodec<ApiaryBlock> CODEC = simpleCodec(ApiaryBlock::new);

    /// Where this block sits in its column, so the texture can close the seams.
    public enum Part implements StringRepresentable {
        SINGLE("single"),
        BOTTOM("bottom"),
        MIDDLE("middle"),
        TOP("top");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /// What the hive is painted, if anything. Cosmetic, and deliberately on the blockstate
    /// rather than in the block entity: the state is its own synchronisation, so there is no
    /// packet to write and no way for a client to draw a colour the server does not have.
    ///
    /// A column is always one colour -- see paintColumn and onPlace.
    public enum Tint implements StringRepresentable {
        NONE("none", null),
        WHITE("white", DyeColor.WHITE),
        ORANGE("orange", DyeColor.ORANGE),
        MAGENTA("magenta", DyeColor.MAGENTA),
        LIGHT_BLUE("light_blue", DyeColor.LIGHT_BLUE),
        YELLOW("yellow", DyeColor.YELLOW),
        LIME("lime", DyeColor.LIME),
        PINK("pink", DyeColor.PINK),
        GRAY("gray", DyeColor.GRAY),
        LIGHT_GRAY("light_gray", DyeColor.LIGHT_GRAY),
        CYAN("cyan", DyeColor.CYAN),
        PURPLE("purple", DyeColor.PURPLE),
        BLUE("blue", DyeColor.BLUE),
        BROWN("brown", DyeColor.BROWN),
        GREEN("green", DyeColor.GREEN),
        RED("red", DyeColor.RED),
        BLACK("black", DyeColor.BLACK);

        /// What an undyed hive is drawn with.
        ///
        /// The Apiary's board textures are stored as luminance -- greys -- rather than as wood.
        /// A tint multiplies, and multiplying this mod's board tone (#A98649, warm, with barely
        /// any blue in it) by a blue dye is arithmetic rather than taste: #3C44AA came out
        /// #5B4B3A, which is brown. Nothing done to the dye first can put blue into a texel that
        /// has none. On a neutral base every dye reads as itself, and "undyed" stops being a
        /// special case: it is this multiply instead of a dye's.
        ///
        /// Fitted by least squares across the whole board ramp, so the wood looks as it did
        /// before any of this existed. tools/gen_apiary_textures.py carries the same number and
        /// its preview is what it was fitted against; the two have to move together.
        public static final int UNDYED_TAN = 0xE0B366;

        /// How far a dye is lifted towards white before it multiplies. The neutral base already
        /// costs about a third of the value, so a raw dye lands darker than the paint pot
        /// suggests; this puts some of it back without washing the colour out.
        public static final float PAINT_MIX = 0.25F;

        private static final Map<DyeColor, Tint> BY_DYE = Arrays.stream(values())
            .filter(tint -> tint.dye != null)
            .collect(Collectors.toUnmodifiableMap(tint -> tint.dye, tint -> tint));

        private final String name;
        private final @Nullable DyeColor dye;

        Tint(String name, @Nullable DyeColor dye) {
            this.name = name;
            this.dye = dye;
        }

        public static Tint of(DyeColor dye) {
            return BY_DYE.get(dye);
        }

        /// The colour the board layer is multiplied by. Opaque, because a tint that is not is a
        /// hive you can see through.
        public int paintColor() {
            return ARGB.opaque(dye == null
                ? UNDYED_TAN
                : ARGB.srgbLerp(PAINT_MIX, dye.getTextureDiffuseColor(), 0xFFFFFFFF));
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<Tint> TINT = EnumProperty.create("tint", Tint.class);

    /// Whether the hive is standing on scaffolding, in which case it grows legs down into it.
    ///
    /// A beekeeper puts a hive on a trestle to keep it off wet ground, and scaffolding is the
    /// closest thing the game already has to one. Only the Apiary's own model changes: the
    /// scaffolding underneath stays scaffolding, still climbable and still breakable, because
    /// a decoration is not worth taking a vanilla block's behaviour away for.
    ///
    /// Only ever true on the bottom of a column -- anything higher has an Apiary below it, not
    /// scaffolding -- so it never has to be reconciled with PART.
    public static final BooleanProperty STAND = BooleanProperty.create("stand");

    public ApiaryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(PART, Part.SINGLE)
            .setValue(TINT, Tint.NONE)
            .setValue(STAND, false));
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, TINT, STAND);
    }

    /// The variant is decided at placement, not a tick later.
    ///
    /// updateShape only fires on a block when a *neighbour* changes, so a newly placed one is
    /// never told about itself: dropping an apiary on top of another placed it as SINGLE, with
    /// a lid and a base of its own, and it stayed that way until something else in the column
    /// happened to change. Reading the column here means the seam closes the instant the block
    /// lands.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(PART, partAt(context.getLevel(), context.getClickedPos()))
            .setValue(STAND, standsOn(context.getLevel(), context.getClickedPos()));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ApiaryBlockEntity(pos, state);
    }

    // Breeding/production is simulated entirely server-side, and there is nothing left for a
    // client ticker to do: the ambient bees are driven by their own renderer, on the render
    // thread, from the synced activity flag (see HiveBeeRenderer).
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, MelliferaBlockEntities.APIARY.get(), ApiaryBlockEntity::serverTick);
    }

    /// True for the block that owns the hive: the bottom of its column.
    public static boolean isController(BlockGetter level, BlockPos pos) {
        return !isApiary(level, pos.below());
    }

    /// Walks down to the controller from anywhere in the column.
    public static BlockPos controllerOf(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        while (isApiary(level, cursor.below())) {
            cursor.move(Direction.DOWN);
        }

        return cursor.immutable();
    }

    private static boolean isApiary(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ApiaryBlock;
    }

    /// Dye paints the hive; a water bucket washes it off. Anything else falls through to
    /// useWithoutItem and opens the window, which is what a click on an Apiary has always done.
    ///
    /// TRY_WITH_EMPTY_HAND rather than PASS on the way out, so a click holding something the
    /// hive does not care about still opens it instead of trying to place it. That is already
    /// the block's behaviour -- Block.useItemOn's own default -- and it is why a water bucket
    /// held against an Apiary never emptied into the world in the first place.
    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        // getColor, not an instanceof DyeItem: it reads the DYE component and then the dye item
        // tags, so a dye added by another mod paints the hive without knowing this code exists.
        DyeColor dye = DyeColor.getColor(stack);
        if (dye != null) {
            return paint(state, level, pos, player, stack, Tint.of(dye), SoundEvents.DYE_USE, true);
        }

        if (stack.is(Items.WATER_BUCKET)) {
            // The bucket is the tool, not the reagent: nothing is emptied, so nothing is
            // consumed and no water is placed. Paint is cosmetic and costs a player nothing to
            // reapply, so making the undo cost a bucket of water would only be a tax on
            // changing your mind.
            return paint(state, level, pos, player, stack, Tint.NONE, SoundEvents.SPONGE_ABSORB, false);
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    private static InteractionResult paint(BlockState state, Level level, BlockPos pos, Player player,
                                           ItemStack stack, Tint tint, SoundEvent sound, boolean consume) {
        // The whole column decides, not the block under the cursor. Reading only the clicked
        // block would refuse to wash a tower whose bottom super happened to be plain already --
        // and leave the two above it painted, which is the one outcome a player asking for
        // "clean" cannot be given.
        if (!columnNeedsPaint(level, pos, tint)) {
            // Already this colour, all the way up. Open the hive rather than eat the click.
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        paintColumn(level, pos, tint);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (consume && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
    }

    /// Paints every Apiary in the column, however tall it is.
    ///
    /// The whole column, not the first MAX_LEVELS of it: partAt makes a block MIDDLE whenever
    /// there is an Apiary above and below, so a player can stack more than the three that
    /// actually work, and a tower with an unpainted hat on it would be the one thing worse than
    /// no paint at all.
    ///
    /// UPDATE_CLIENTS rather than UPDATE_ALL: nothing about a colour concerns the neighbours,
    /// and the parts have not changed, so all this owes anyone is the re-render.
    public static void paintColumn(Level level, BlockPos pos, Tint tint) {
        BlockPos.MutableBlockPos cursor = controllerOf(level, pos).mutable();
        while (isApiary(level, cursor)) {
            BlockState state = level.getBlockState(cursor);
            if (state.getValue(TINT) != tint) {
                level.setBlock(cursor, state.setValue(TINT, tint), Block.UPDATE_CLIENTS);
            }

            cursor.move(Direction.UP);
        }
    }

    /// Whether any Apiary in the column is not already this colour.
    ///
    /// Walks the same blocks paintColumn would write, so the two cannot disagree about what a
    /// click is going to do -- which matters because this is also what the client answers with
    /// before the server has been asked.
    private static boolean columnNeedsPaint(BlockGetter level, BlockPos pos, Tint tint) {
        BlockPos.MutableBlockPos cursor = controllerOf(level, pos).mutable();
        while (isApiary(level, cursor)) {
            if (level.getBlockState(cursor).getValue(TINT) != tint) {
                return true;
            }

            cursor.move(Direction.UP);
        }

        return false;
    }

    /// The colour of the column this position is joining, or null if it is not joining one.
    private static @Nullable Tint columnTint(BlockGetter level, BlockPos pos) {
        for (BlockPos neighbour : new BlockPos[] { pos.below(), pos.above() }) {
            if (level.getBlockState(neighbour).getBlock() instanceof ApiaryBlock) {
                return level.getBlockState(neighbour).getValue(TINT);
            }
        }

        return null;
    }

    /// Any block of the column opens the hive, and the hive is always the controller's.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos controller = controllerOf(level, pos);
        if (level.getBlockEntity(controller) instanceof ApiaryBlockEntity apiary) {
            // The height rides along with the position: the menu lays its slots out in its
            // constructor, before any ContainerData has synced, so it has to arrive here.
            player.openMenu(apiary, buffer -> {
                buffer.writeBlockPos(controller);
                buffer.writeByte(apiary.levels());
            });
        }

        return InteractionResult.CONSUME;
    }

    /// Recomputes the whole column whenever any part of it changes.
    ///
    /// Driven off neighbour updates rather than a tick, because a column only ever changes
    /// when a block is placed or broken, and both of those already send one. Walking the
    /// column is at most three block lookups.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        // Only vertical neighbours can change what part of a column this is, or whether there
        // is still scaffolding under it.
        if (directionToNeighbour.getAxis() != Direction.Axis.Y) {
            return state;
        }

        return state
            .setValue(PART, partAt(level, pos))
            .setValue(STAND, standsOn(level, pos));
    }

    /// Recomputes the column's height when a block joins it, and keeps the column one colour.
    ///
    /// Two directions, and which one wins is decided by the newcomer: a plain Apiary added to a
    /// painted tower is painted to match, and a painted one added to a plain tower paints it.
    /// Either way the answer to "what colour is this hive" stays a single colour, which is the
    /// only rule a player will have in their head.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshColumn(level, pos);

        // Server only: onPlace runs on both sides, and the client has no business rewriting
        // states it will be told about anyway.
        if (level.isClientSide()) {
            return;
        }

        // Only for a block that has just arrived. onPlace also fires when an Apiary is merely
        // rewritten in place, which is exactly what paintColumn does to every block it touches,
        // and letting the adoption below run then made washing a tower impossible: the first
        // super set to NONE looked up, found its neighbours still painted, and obediently
        // painted the whole column back. A one-high hive has no neighbour to consult, which is
        // why it was the only one that could be cleaned.
        if (oldState.getBlock() instanceof ApiaryBlock) {
            return;
        }

        Tint joined = columnTint(level, pos);
        if (joined != null && joined != state.getValue(TINT)) {
            paintColumn(level, pos, state.getValue(TINT) == Tint.NONE ? joined : state.getValue(TINT));
        }
    }

    /// Which piece of the column this position is.
    private static Part partAt(BlockGetter level, BlockPos pos) {
        boolean below = isApiary(level, pos.below());
        boolean above = isApiary(level, pos.above());

        if (below && above) {
            return Part.MIDDLE;
        }
        if (below) {
            return Part.TOP;
        }
        if (above) {
            return Part.BOTTOM;
        }

        return Part.SINGLE;
    }

    /// Whether this position is sitting on something worth growing legs for.
    ///
    /// Scaffolding by name rather than by tag: this is one deliberate pairing between two
    /// blocks, not a category, and a tag would invite the question of what a hive on a fence
    /// or a wall is supposed to look like -- which is a different set of legs, not this one.
    private static boolean standsOn(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(Blocks.SCAFFOLDING);
    }

    /// Tells the controller how tall it is now. Called after any change to the column.
    public static void refreshColumn(LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof Level level)) {
            return;
        }

        BlockPos controller = controllerOf(level, pos);
        if (!(level.getBlockEntity(controller) instanceof ApiaryBlockEntity apiary)) {
            return;
        }

        int levels = 1;
        BlockPos.MutableBlockPos cursor = controller.mutable().move(Direction.UP);
        while (levels < ApiaryBlockEntity.MAX_LEVELS && isApiary(level, cursor)) {
            levels++;
            cursor.move(Direction.UP);
        }

        apiary.setLevels(level, levels);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
        // The block below just became the top of a shorter tower, or a lone apiary again.
        refreshColumn(level, pos.below());
    }
}
