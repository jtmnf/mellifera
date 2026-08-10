package com.joaonf.mellifera.client;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.BeeMutation;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.bee.MutationObjective;
import com.joaonf.mellifera.client.special.BeePortraitRenderState;
import com.joaonf.mellifera.registry.MelliferaBeeMutations;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/// The breeding-objective tab: one ghost slot naming the species this apiary is aiming for.
///
/// Ghost, not a real slot, and that is the point rather than a compromise: the objective is
/// a species id the apiary remembers, so it has to be settable to bees the player does not
/// own and has never bred -- which is exactly when telling the hive what to aim for is worth
/// anything. It arrives by being dragged out of JEI; see ApiaryGhostHandler.
public class ObjectivePanel extends SideTab {
    /// The placeholder bee: vanilla's own bee model, darkened to a shadow of itself.
    ///
    /// This used to blit a `princess_outline.png` mask, which was the right answer while the
    /// castes were flat sprites. They are not any more -- the bees became the vanilla model
    /// recoloured at runtime (see BeeSpecialRenderer) and the sprite sheet went with them, so
    /// the blit was pointing at a file that no longer exists and the slot drew the missing
    /// texture. Drawing the model the objective itself would use keeps the empty slot and the
    /// filled one showing the same shape, and still ships no art.
    private static final int GHOST_ABDOMEN = 0x8A8070;
    private static final int GHOST_TINT = 0xFF4A4034;

    /// Model units to GUI pixels, matched to the 16px the item icon occupies in the slot.
    private static final float GHOST_SCALE = 16.0F * 0.78F;
    private static final int GHOST_SIZE = 16;

    /// Nudges the ghost down so it sits on the same line as a real bee icon does.
    ///
    /// The two are centred by different machinery and neither is centred on the box: an item
    /// icon is posed by BeeSpecialRenderer, which drops it two pixels itself, while a
    /// picture-in-picture portrait is centred by BeePortraitRenderer on the frame's midpoint.
    /// A bee hovers with its abdomen low, so the model's own centre of mass sits below the
    /// middle of the frame and the portrait reads as riding high in the slot. In pixels, so
    /// it can be tuned a pixel at a time -- positive is down.
    private static final int GHOST_Y_NUDGE = 3;

    private static final int OPEN_WIDTH = 124;
    private static final int OPEN_HEIGHT = 56;

    /// Where the two bees currently loaded stand relative to the objective.
    ///
    /// This line is the reason the panel is worth opening. An objective only moves the odds
    /// of a mutation roll, and those roll once per brood, so from outside a working objective
    /// and a broken one look identical for several generations -- long enough to conclude the
    /// feature does nothing.
    public enum PathStatus {
        NO_OBJECTIVE,
        /// The apiary already holds the objective species. Checked before everything else:
        /// the bee you were breeding for does not usually cross into anything further, so
        /// without this the panel answers a finished line with "no cross".
        REACHED,
        NO_PAIR,
        ON_PATH,
        OFF_PATH,
        NO_CROSS
    }

    public record Path(PathStatus status, @Nullable BeeSpecies next) {
        private static final Path NO_OBJECTIVE = new Path(PathStatus.NO_OBJECTIVE, null);
        private static final Path REACHED = new Path(PathStatus.REACHED, null);
        private static final Path NO_PAIR = new Path(PathStatus.NO_PAIR, null);
        private static final Path OFF_PATH = new Path(PathStatus.OFF_PATH, null);
        private static final Path NO_CROSS = new Path(PathStatus.NO_CROSS, null);
    }

    private final Source source;

    /// What the panel needs from the screen, so it never reaches for the menu itself.
    public interface Source {
        @Nullable BeeSpecies objective();

        @Nullable Identifier objectiveId();

        @Nullable Identifier crossParentA();

        @Nullable Identifier crossParentB();

        void setObjective(@Nullable BeeSpecies species);
    }

    public ObjectivePanel(Font font, Source source) {
        super(font, "gui.mellifera.objective.title", Side.RIGHT);
        this.source = source;
    }

    @Override
    protected int openWidth() {
        return OPEN_WIDTH;
    }

    @Override
    protected int openHeight() {
        return OPEN_HEIGHT;
    }

    @Override
    protected boolean accented() {
        return source.objective() != null;
    }

    /// Works the verdict out from the objective and whatever is in the two input slots.
    ///
    /// Needs nothing the client does not already have: the mutation table is a static list
    /// built identically on both sides, and the slot contents are in the menu.
    public static Path pathOf(@Nullable Identifier objective, @Nullable Identifier princess, @Nullable Identifier drone) {
        if (objective == null) {
            return Path.NO_OBJECTIVE;
        }

        // Either slot holding the objective means the line is done. Either, not both -- a
        // lone Imperial princess is as much a success as a mated pair of them.
        if (objective.equals(princess) || objective.equals(drone)) {
            return Path.REACHED;
        }

        if (princess == null || drone == null) {
            return Path.NO_PAIR;
        }

        MutationObjective goal = MutationObjective.of(objective, MelliferaBeeMutations.all());
        BeeMutation next = goal == null ? null : goal.nextStep(princess, drone, MelliferaBeeMutations.all());
        if (next != null) {
            return new Path(PathStatus.ON_PATH, MelliferaBeeSpecies.REGISTRY.getValue(next.result()));
        }

        return MutationObjective.hasAnyCross(princess, drone, MelliferaBeeMutations.all()) ? Path.OFF_PATH : Path.NO_CROSS;
    }

    private Path path() {
        return pathOf(source.objectiveId(), source.crossParentA(), source.crossParentB());
    }

    /// The rectangle a JEI drag may be dropped on: the ghost slot when open, the whole tab
    /// when closed. Dropping on a closed tab is worth supporting -- a player dragging a bee
    /// across the screen should not have to stop and open a panel first.
    public Rect2i dropTarget() {
        return isOpen()
            ? new Rect2i(slotX(), slotY(), SLOT_SIZE, SLOT_SIZE)
            : new Rect2i(contentLeft(), y, TAB_WIDTH, TAB_HEIGHT);
    }

    @Override
    protected void renderTabIcon(GuiGraphicsExtractor graphics, int iconX, int iconY) {
        BeeSpecies objective = source.objective();
        if (objective != null) {
            graphics.fakeItem(beeStack(objective), iconX, iconY);
            return;
        }

        ghostBee(graphics, iconX, iconY);
    }

    @Override
    protected void renderBody(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        BeeSpecies objective = source.objective();
        int slotX = slotX();
        int slotY = slotY();

        insetSlot(graphics, slotX, slotY, SLOT_SIZE);

        if (objective != null) {
            graphics.fakeItem(beeStack(objective), slotX + 1, slotY + 1);
        } else {
            ghostBee(graphics, slotX + 1, slotY + 1);
        }

        // Vanilla's own slot hover, drawn over the interior after the item.
        if (contains(mouseX, mouseY, slotX, slotY, SLOT_SIZE, SLOT_SIZE)) {
            graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, SLOT_HOVER);
        }

        int textX = slotX + SLOT_SIZE + PADDING;
        int textY = slotY + (SLOT_SIZE - font.lineHeight) / 2;
        int available = contentLeft() + OPEN_WIDTH - PADDING - textX;

        if (objective == null) {
            clipped(graphics, Component.translatable("gui.mellifera.objective.empty"), textX, textY, available, EMPTY_COLOR);
        } else {
            clipped(graphics, Component.translatable(objective.translationKey()), textX, textY, available, ACCENT_COLOR);
        }

        renderStatus(graphics);
    }

    /// A darkened bee where a bee would go.
    ///
    /// An empty inset square says "something goes here" but not what, and this slot takes
    /// something the player cannot put there by hand at all -- it only accepts a JEI drag.
    /// Showing the shape of what belongs is the difference between an obvious gesture and an
    /// undiscoverable one.
    ///
    /// Picture-in-picture rather than a fake item stack because there is no such thing as a
    /// speciesless bee item: every stack resolves to some species' colour, so drawing one
    /// here would put a real, specific bee in a slot that is meant to read as empty. The
    /// portrait takes an arbitrary colour and an arbitrary tint, so the placeholder can be a
    /// bee nobody can breed.
    private static void ghostBee(GuiGraphicsExtractor graphics, int iconX, int iconY) {
        graphics.submitPictureInPictureRenderState(new BeePortraitRenderState(
            GHOST_ABDOMEN,
            false,
            true,
            GHOST_TINT,
            (Util.getMillis() % 1_000_000L) / 50.0F,
            iconX,
            iconY + GHOST_Y_NUDGE,
            iconX + GHOST_SIZE,
            iconY + GHOST_SIZE + GHOST_Y_NUDGE,
            GHOST_SCALE,
            graphics.peekScissorStack()));
    }

    /// What the pair currently loaded is being pushed towards. ON_PATH and REACHED are the
    /// only ones coloured -- the other three are all ways of saying the objective is doing
    /// nothing for what is in the apiary now, and colouring them like a result would lie.
    private void renderStatus(GuiGraphicsExtractor graphics) {
        Path path = path();
        Component text = switch (path.status()) {
            case NO_OBJECTIVE -> Component.translatable("gui.mellifera.objective.status.none");
            case REACHED -> Component.translatable("gui.mellifera.objective.status.reached");
            case NO_PAIR -> Component.translatable("gui.mellifera.objective.status.no_pair");
            case ON_PATH -> Component.translatable("gui.mellifera.objective.status.next",
                Component.translatable(path.next() == null ? "" : path.next().translationKey()));
            case OFF_PATH -> Component.translatable("gui.mellifera.objective.status.off_path");
            case NO_CROSS -> Component.translatable("gui.mellifera.objective.status.no_cross");
        };

        int color = switch (path.status()) {
            case ON_PATH -> ACCENT_COLOR;
            case REACHED -> GOOD_COLOR;
            default -> EMPTY_COLOR;
        };

        clipped(graphics, text, contentLeft() + PADDING, statusY(), OPEN_WIDTH - PADDING * 2, color);
    }

    @Override
    protected @Nullable Component tooltipAt(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY, slotX(), slotY(), SLOT_SIZE, SLOT_SIZE)) {
            return null;
        }

        return Component.translatable(source.objective() == null
            ? "gui.mellifera.objective.hint"
            : "gui.mellifera.objective.clear");
    }

    /// Clicking the ghost slot clears the objective.
    @Override
    protected boolean clickedBody(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY, slotX(), slotY(), SLOT_SIZE, SLOT_SIZE)) {
            return false;
        }

        source.setObjective(null);
        return true;
    }

    private int slotX() {
        return contentLeft() + PADDING;
    }

    private int slotY() {
        return bodyY();
    }

    private int statusY() {
        return slotY() + SLOT_SIZE + 4;
    }

    /// A pure individual of the species, purely to have something to draw. Never leaves this
    /// class -- the objective on the server is an id, not a stack.
    private static ItemStack beeStack(BeeSpecies species) {
        Identifier id = MelliferaBeeSpecies.REGISTRY.getKey(species);
        ItemStack stack = new ItemStack(MelliferaItems.PRINCESS_BEE.get());
        if (id != null) {
            stack.set(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.pure(id));
        }

        return stack;
    }
}
