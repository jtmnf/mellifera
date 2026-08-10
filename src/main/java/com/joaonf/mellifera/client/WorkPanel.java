package com.joaonf.mellifera.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.bee.BeeStacks;
import com.joaonf.mellifera.bee.FrameType;
import com.joaonf.mellifera.bee.ToleranceAllele;
import com.joaonf.mellifera.item.FrameItem;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.temperature.EnvironmentTemperature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/// Why the hive is or is not working, on the left of the window.
///
/// The apiary has exactly two failure modes and neither of them says anything: with no queen
/// it sits still, and with a queen outside her temperature band it also sits still, and the
/// two look identical. A player who has just carried a Tropical princess into a tundra gets
/// no feedback at all -- the hive simply never produces, and nothing on screen distinguishes
/// that from a hive that is merely slow.
///
/// So the closed tab is a single verdict, and opening it lists the checks with a tick or a
/// cross each, plus the numbers for the one check that has any: the actual temperature here
/// against the band this queen accepts.
///
/// Everything is computed on the client from what the menu already carries. The temperature
/// model reads the world's blocks and biome, which the client has in full, so this needs no
/// new data slot and updates the instant the player walks a torch up to the hive.
public class WorkPanel extends SideTab {
    private static final int OPEN_WIDTH = 132;
    private static final int OPEN_HEIGHT = 62;

    private static final int ROW_HEIGHT = 11;
    private static final int BAD_COLOR = 0xFFE06A5A;

    /// One line of the verdict: whether it passes and what to say about it.
    public record Check(boolean ok, Component label) {}

    private final ApiaryMenuView menu;

    /// The verdict, worked out once a tick instead of once a frame.
    ///
    /// checks() reads the world through EnvironmentTemperature, which scans a sphere of blocks
    /// and blends nine biome lookups, and render asks for it twice a frame: once for the accent
    /// stripe (SideTab.render) and once for either the tab glyph or the rows. At a hundred
    /// frames a second that was tens of thousands of block reads to draw two lines of text whose
    /// contents change when the player carries a torch over, not when the frame flips.
    ///
    /// Exactly the reasoning MelliferaTemperatureHud already states for the same call, and the
    /// same fix. A tick of latency is not observable here: the panel is a readout of a number
    /// that takes twenty real minutes to swing across its own range.
    private @Nullable List<Check> cached;
    private boolean working;

    /// What this panel needs from the menu, kept as a narrow interface so the panel has no
    /// opinion about the rest of the screen.
    public interface ApiaryMenuView {
        boolean queenPresent();

        ItemStack queenStack();

        List<Slot> frameSlots();

        BlockPos apiaryPos();
    }

    public WorkPanel(Font font, ApiaryMenuView menu) {
        super(font, "gui.mellifera.work.title", Side.LEFT);
        this.menu = menu;
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
        return working();
    }

    public boolean working() {
        if (cached == null) {
            refresh();
        }

        return working;
    }

    /// The cached verdict, computed on the spot the first time it is asked for -- a panel drawn
    /// in the same frame it was built must not come up blank waiting for a tick.
    public List<Check> checks() {
        List<Check> current = cached;
        return current != null ? current : refresh();
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    private List<Check> refresh() {
        List<Check> current = evaluate();

        boolean all = true;
        for (Check check : current) {
            all &= check.ok();
        }

        cached = current;
        working = all;
        return current;
    }

    /// The same two gates BeeHousingBlockEntity.serverTick applies, in the same order, plus
    /// the frame that can switch the second one off.
    ///
    /// Deliberately mirrors the simulation rather than reporting a flag it sends: a panel fed
    /// by its own copy of the rules can drift from them, but it can also be read next to them
    /// and checked. If these ever disagree, this is the copy that is wrong.
    private List<Check> evaluate() {
        List<Check> checks = new ArrayList<>();

        boolean queen = menu.queenPresent();
        checks.add(new Check(queen, Component.translatable(
            queen ? "gui.mellifera.work.queen_ok" : "gui.mellifera.work.queen_missing")));

        if (!queen) {
            return checks;
        }

        if (insulated()) {
            checks.add(new Check(true, Component.translatable("gui.mellifera.work.insulated")));
            return checks;
        }

        BeeGenome genome = BeeStacks.genomeOf(menu.queenStack());
        Level level = Minecraft.getInstance().level;
        if (genome == null || level == null) {
            return checks;
        }

        BeeSpecies species = MelliferaBeeSpecies.get(genome.species().active());
        ToleranceAllele tolerance = genome.tolerance().active();
        float low = species.minCelsius() - tolerance.widenBelow();
        float high = species.maxCelsius() + tolerance.widenAbove();
        float here = EnvironmentTemperature.celsius(level, menu.apiaryPos());

        boolean ok = here >= low && here <= high;
        checks.add(new Check(ok, Component.translatable(
            ok ? "gui.mellifera.work.climate_ok" : "gui.mellifera.work.climate_bad",
            Math.round(here), Math.round(low), Math.round(high))));

        return checks;
    }

    private boolean insulated() {
        return menu.frameSlots().stream()
            .map(Slot::getItem)
            .anyMatch(stack -> stack.getItem() instanceof FrameItem frame && frame.type() == FrameType.INSULATION);
    }

    /// Closed, the tab is the verdict and nothing else: a tick or a cross, big enough to read
    /// without opening anything.
    @Override
    protected void renderTabIcon(GuiGraphicsExtractor graphics, int iconX, int iconY) {
        mark(graphics, iconX + 4, iconY + 4, working());
    }

    @Override
    protected void renderBody(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int rowY = bodyY();
        for (Check check : checks()) {
            mark(graphics, contentLeft() + PADDING, rowY + 1, check.ok());
            clipped(graphics, check.label(), contentLeft() + PADDING + 12, rowY,
                OPEN_WIDTH - PADDING * 2 - 12, check.ok() ? TITLE_COLOR : BAD_COLOR);
            rowY += ROW_HEIGHT;
        }
    }

    /// A tick or a cross drawn from filled pixels rather than a texture.
    ///
    /// Two glyphs of eight pixels each is less than the cost of an atlas entry, and drawing
    /// them by hand keeps them the same weight as the panel's own bevels -- a font glyph
    /// scaled into this space reads thinner than everything around it.
    private static void mark(GuiGraphicsExtractor graphics, int left, int top, boolean ok) {
        int color = ok ? GOOD_COLOR : BAD_COLOR;
        if (ok) {
            for (int i = 0; i < 3; i++) {
                graphics.fill(left + i, top + 3 + i, left + i + 1, top + 4 + i + 1, color);
            }
            for (int i = 0; i < 4; i++) {
                graphics.fill(left + 3 + i, top + 5 - i, left + 4 + i, top + 6 - i, color);
            }
            return;
        }

        for (int i = 0; i < 7; i++) {
            graphics.fill(left + i, top + i, left + i + 1, top + i + 1, color);
            graphics.fill(left + 6 - i, top + i, left + 7 - i, top + i + 1, color);
        }
    }

    @Override
    protected @Nullable Component tooltipAt(int mouseX, int mouseY) {
        return null;
    }
}
