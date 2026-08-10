package com.joaonf.mellifera.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.block.ApiaryBlockEntity;
import com.joaonf.mellifera.menu.ApiaryMenu;
import com.joaonf.mellifera.network.SetObjectivePayload;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Background texture and slot geometry come from Forestry's real Apiary GUI (LGPL v3,
// ForestryMC/ForestryMC), recoloured -- see ApiaryMenu for the source note.
public class ApiaryScreen extends AbstractContainerScreen<ApiaryMenu> {
    /// One window per height. A stacked hive's is wider because its honeycomb is: the extra
    /// cells have to touch the original cluster to render as hexagons at all, and there was
    /// no room to their left in a 176-wide window.
    private static final Identifier[] BACKGROUNDS = {
        Identifier.fromNamespaceAndPath(Mellifera.MODID, "textures/gui/container/apiary.png"),
        Identifier.fromNamespaceAndPath(Mellifera.MODID, "textures/gui/container/apiary_2.png"),
        Identifier.fromNamespaceAndPath(Mellifera.MODID, "textures/gui/container/apiary_3.png"),
    };

    private static final int WIDTH = 176;
    private static final int HEIGHT = 190;

    // On the same row as the title (AbstractContainerScreen draws it at its own
    // titleLabelX/Y, 8/6), right after the word "Apiary" -- not a new line underneath it.
    private static final int STATUS_GAP = 5;
    private static final int STATUS_Y_NUDGE = 1;
    private static final float STATUS_SCALE = 0.75F;
    private static final int QUEEN_COLOR = 0xFFF4C542;
    private static final int NO_QUEEN_COLOR = 0xFF6E6658;

    // The gauge groove in Forestry's own art: interior is (21,37) to (22,82).
    private static final int BAR_X = 21;
    private static final int BAR_Y = 37;
    private static final int BAR_WIDTH = 2;
    private static final int BAR_HEIGHT = 46;
    private static final int BAR_FILL = 0xFFE0A526;

    private @Nullable ObjectivePanel objectivePanel;
    private @Nullable FramePanel framePanel;
    private @Nullable WorkPanel workPanel;

    /// Every tab, in the order clicks are offered to them. Rebuilt only on first init.
    private final List<SideTab> tabs = new ArrayList<>();

    public ApiaryScreen(ApiaryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.levels() > 1 ? WIDTH + ApiaryMenu.WIDE_SHIFT : WIDTH, HEIGHT);
    }

    private Identifier background() {
        return BACKGROUNDS[Math.max(0, Math.min(BACKGROUNDS.length - 1, menu.levels() - 1))];
    }

    /// Exposed for JEI's ghost-ingredient handler, which needs the drop rectangle and a way
    /// to hand a dragged bee over. Null before init() has run.
    public @Nullable ObjectivePanel objectivePanel() {
        return objectivePanel;
    }

    /// Every tab's rectangle, so JEI can lay its ingredient list out around all of them
    /// rather than just the one it used to know about.
    public List<Rect2i> tabBounds() {
        return tabs.stream().map(SideTab::bounds).toList();
    }

    /// Points the apiary at a species, or clears it with null. Sends the change to the server
    /// as a SetObjectivePayload and does not touch any local state: the objective comes back
    /// down in the hive's update tag, so the panel always draws what the server actually has
    /// rather than what was clicked here.
    public void setObjective(@Nullable BeeSpecies species) {
        Identifier id = species == null ? null : MelliferaBeeSpecies.REGISTRY.getKey(species);
        ClientPacketDistributor.sendToServer(new SetObjectivePayload(menu.apiaryPos(), Optional.ofNullable(id)));
    }

    @Override
    protected void init() {
        super.init();
        // Kept across re-inits rather than rebuilt: init() runs again on every window
        // resize, and a panel the player had slid open should not snap shut because they
        // dragged the game window's corner.
        if (tabs.isEmpty()) {
            objectivePanel = new ObjectivePanel(font, new ObjectiveSource());
            framePanel = new FramePanel(font, menu.frameSlots());
            workPanel = new WorkPanel(font, new MenuView());
            tabs.add(workPanel);
            tabs.add(objectivePanel);
            tabs.add(framePanel);
        }

        // Positioned here as well as at render time because JEI asks for the tabs' bounds as
        // soon as the screen opens, to lay its ingredient list out around them -- before the
        // first frame has been drawn. Without this it would be told they sit at the screen's
        // top-left corner and would leave its list covering the real ones.
        layout();
    }

    /// Stacks the right-hand tabs down the window's edge and parks the left-hand one.
    ///
    /// Run every frame rather than once, because the stack shifts as tabs open: the frame tab
    /// has to sit below whatever height the objective tab currently is, and that changes mid
    /// animation.
    private void layout() {
        int rightOffset = 0;
        for (SideTab tab : tabs) {
            if (tab == workPanel) {
                tab.reposition(leftPos, topPos, imageWidth, 0);
                continue;
            }

            tab.reposition(leftPos, topPos, imageWidth, rightOffset);
            rightOffset += tab.stackHeight();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractBackground(graphics, mouseX, mouseY, partial);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, background(), x, y, 0.0F, 0.0F,
            imageWidth, imageHeight, imageWidth, imageHeight);

        renderProgressBar(graphics, x, y);
        renderStatus(graphics, x, y);
    }

    /// Drawn here rather than in extractBackground or extractRenderState, and the choice is
    /// load-bearing in both directions.
    ///
    /// Not extractBackground: the panel has to sit above the slot layer the base class draws
    /// in between those two calls.
    ///
    /// Not extractRenderState either, which was the first attempt and put the panel in front
    /// of anything being dragged. AbstractContainerScreen.extractRenderState runs
    /// extractContents, then extractCarriedItem -- which opens a *new stratum* for the item
    /// floating on the cursor -- and only then extractTooltip. Drawing after all of that
    /// landed the panel in the floating item's own stratum, on top of it, so a bee dragged
    /// over the tab disappeared behind the very thing it was being dropped on. Hooking
    /// extractContents keeps the panel in the window's stratum, under every later one.
    ///
    /// The panel's tooltip still wins despite being set earlier than extractTooltip's:
    /// tooltips are deferred to the end of the frame, and the base class only sets one when
    /// a slot is hovered -- which cannot be true out here, past the window's right edge.
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        layout();
        if (framePanel != null) {
            menu.setFramesVisible(framePanel.isOpen());
        }

        // The tabs are drawn *before* super, and that ordering is the whole reason the frame
        // panel works. super.extractContents is what renders the menu's slots, and the frame
        // slots live inside this panel -- drawing the panel afterwards painted its own
        // background straight over the frames sitting in it, so the slots looked empty no
        // matter what was in them.
        //
        // Nothing is lost by going first: every tab hangs outside the window's rectangle, so
        // there is nothing of the window's own for them to be covered by.
        for (SideTab tab : tabs) {
            tab.render(graphics, mouseX, mouseY, partial);
        }

        super.extractContents(graphics, mouseX, mouseY, partial);

        for (SideTab tab : tabs) {
            Component tooltip = tab.tooltip(mouseX, mouseY);
            if (tooltip != null) {
                graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
        }
    }

    /// Reads the objective and the loaded pair straight off the menu's own slots, so the
    /// panel updates the moment a bee is swapped -- no waiting on the server and nothing
    /// extra on the wire. Stays true while a queen is working, because the second parent
    /// comes from her rather than from the drone slot she already emptied.
    private final class ObjectiveSource implements ObjectivePanel.Source {
        @Override
        public @Nullable BeeSpecies objective() {
            Identifier id = objectiveId();
            return id == null ? null : MelliferaBeeSpecies.REGISTRY.getValue(id);
        }

        /// Read off the block entity rather than the menu: the objective travels in the
        /// hive's update tag as an id (see BeeHousingBlockEntity.getUpdateTag), because the
        /// menu's data slots are shorts and could only ever have carried a registry index.
        /// The block entity is on the client too -- it is in a loaded chunk, since the player
        /// is standing in front of it with its screen open.
        @Override
        public @Nullable Identifier objectiveId() {
            if (minecraft == null || minecraft.level == null) {
                return null;
            }

            return minecraft.level.getBlockEntity(menu.apiaryPos()) instanceof ApiaryBlockEntity apiary
                ? apiary.objective()
                : null;
        }

        @Override
        public @Nullable Identifier crossParentA() {
            return menu.crossParentA();
        }

        @Override
        public @Nullable Identifier crossParentB() {
            return menu.crossParentB();
        }

        @Override
        public void setObjective(@Nullable BeeSpecies species) {
            ApiaryScreen.this.setObjective(species);
        }
    }

    private final class MenuView implements WorkPanel.ApiaryMenuView {
        @Override
        public int flowers() {
            return menu.flowers();
        }

        @Override
        public boolean queenPresent() {
            return menu.queenPresent();
        }

        @Override
        public ItemStack queenStack() {
            return menu.queenStack();
        }

        @Override
        public List<Slot> frameSlots() {
            return menu.frameSlots();
        }

        @Override
        public BlockPos apiaryPos() {
            return menu.apiaryPos();
        }
    }

    /// containerTick, not tick: AbstractContainerScreen makes tick() final and routes it
    /// here after its own stillValid check.
    @Override
    protected void containerTick() {
        super.containerTick();
        tabs.forEach(SideTab::tick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (SideTab tab : tabs) {
            if (!tab.mouseClicked(event.x(), event.y())) {
                continue;
            }

            closeOthers(tab);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    /// Only one tab per side stays open.
    ///
    /// Two open tabs on the same edge would overlap once the upper one is taller than its own
    /// stack slot, and the frame tab's slots are real Slots -- so an overlap is not merely
    /// ugly, it puts clickable slots underneath another panel's chrome.
    private void closeOthers(SideTab opened) {
        if (!opened.isOpen()) {
            return;
        }

        boolean left = opened == workPanel;
        for (SideTab tab : tabs) {
            if (tab != opened && (tab == workPanel) == left) {
                tab.setOpen(false);
            }
        }
    }

    private void renderStatus(GuiGraphicsExtractor graphics, int x, int y) {
        boolean queenPresent = menu.queenPresent();

        // Showing the cycles left matters: a queen takes minutes to die, and without a
        // number ticking down the apiary is indistinguishable from a broken one.
        Component text = queenPresent
            ? Component.translatable("gui.mellifera.apiary.queen_present", menu.lifespanRemaining())
            : Component.translatable("gui.mellifera.apiary.no_queen");

        // titleLabelX/Y (8, 6) is where AbstractContainerScreen draws "Apiary" itself, at
        // scale 1.0 -- start right where that text ends, not on a line of our own.
        int afterTitle = x + titleLabelX + font.width(title) + STATUS_GAP;
        int statusY = y + titleLabelY + STATUS_Y_NUDGE;

        graphics.pose().pushMatrix();
        graphics.pose().translate(afterTitle, statusY);
        graphics.pose().scale(STATUS_SCALE, STATUS_SCALE);
        graphics.text(font, text, 0, 0, queenPresent ? QUEEN_COLOR : NO_QUEEN_COLOR);
        graphics.pose().popMatrix();
    }

    // Fills bottom-up, like a brewing stand's fuel gauge.
    private void renderProgressBar(GuiGraphicsExtractor graphics, int x, int y) {
        int total = menu.progressTotal();
        if (total <= 0) {
            return;
        }

        int filled = Math.round(BAR_HEIGHT * Math.min(1.0F, (float) menu.progress() / total));
        if (filled > 0) {
            int top = y + BAR_Y + (BAR_HEIGHT - filled);
            graphics.fill(x + BAR_X, top, x + BAR_X + BAR_WIDTH, y + BAR_Y + BAR_HEIGHT, BAR_FILL);
        }
    }
}
