package com.joaonf.mellifera.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.bee.BeeStacks;
import com.joaonf.mellifera.bee.Foraging;
import com.joaonf.mellifera.bee.FrameType;
import com.joaonf.mellifera.bee.ToleranceAllele;
import com.joaonf.mellifera.block.BeeHousingBlockEntity;
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
/// A hive that is doing nothing and a hive that is doing something slowly look identical from
/// outside, and the apiary now has four reasons to be one or the other: no queen, a queen out of
/// her temperature band, nothing in flower within her territory, and foragers in for the night
/// or the rain. Only the first two stop it. The other two swing production by a factor of ten,
/// which is far too much to leave a player to infer from a comb arriving late.
///
/// So the closed tab is a single verdict -- a cross if something has stopped it, a bang if
/// something is slowing it, a tick if neither -- and opening it lists the checks, each a short
/// label with its figures indented underneath.
///
/// Almost all of it is computed on the client from what it already has. The temperature model
/// reads blocks and biome, the hour and the weather are the client's own, and only the flower
/// count comes across, on the menu's data channel where it is paid for by the one player looking
/// at it rather than by everyone in render distance.
public class WorkPanel extends SideTab {
    private static final int OPEN_WIDTH = 132;

    private static final int ROW_HEIGHT = 11;
    private static final int DETAIL_HEIGHT = 10;
    private static final int MARK_WIDTH = 12;

    private static final int BAD_COLOR = 0xFFE06A5A;
    private static final int WARN_COLOR = 0xFFE0C45A;

    /// Dimmer than TITLE_COLOR, because a detail line is read second or not at all.
    private static final int DETAIL_COLOR = 0xFF9A9285;

    /// Three outcomes, not two.
    ///
    /// A hive with three flowers in reach is not broken and is not fine either: it works, at a
    /// quarter speed, and a cross would send the player looking for a fault that is not there
    /// while a tick would leave them wondering why the comb is slow. WARN is the state the mod
    /// gained the day production started depending on the neighbourhood.
    ///
    /// Only BAD counts against the tab's verdict -- see refresh.
    public enum Status {
        OK, WARN, BAD
    }

    /// One line of the verdict: how it stands, what to call it, and the numbers behind it.
    ///
    /// The numbers are a separate line on purpose. "Too cold or hot: 12°C (needs 5 to 25)" was a
    /// single string a hundred and forty pixels wide in a panel a hundred and eight wide, so it
    /// was silently cut off mid-word by `clipped`, which is exactly the "looks like a bug" the
    /// method's own note warns about. A short label the eye can scan, with the figures indented
    /// under it, fits and reads better than either half did.
    public record Check(Status status, Component label, @Nullable Component detail) {
        public Check(Status status, Component label) {
            this(status, label, null);
        }
    }

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

        /// Blocks worth foraging inside the hive's territory, as of its last survey.
        int flowers();

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

    /// Grown to fit whatever the verdict turned out to be. The list is not a fixed length any
    /// more -- a hive with no queen says one thing and a working one says four, some with figures
    /// under them -- and a fixed box would either clip the long case or leave a hole in the short
    /// one.
    @Override
    protected int openHeight() {
        int height = headerHeight() + 3 + PADDING;
        for (Check check : checks()) {
            height += ROW_HEIGHT + (check.detail() == null ? 0 : DETAIL_HEIGHT);
        }

        return height;
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
            all &= check.status() != Status.BAD;
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
        checks.add(new Check(queen ? Status.OK : Status.BAD, Component.translatable(
            queen ? "gui.mellifera.work.queen_ok" : "gui.mellifera.work.queen_missing")));

        if (!queen) {
            return checks;
        }

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return checks;
        }

        climate(checks, level);
        forage(checks, level);
        return checks;
    }

    /// The gate that stops a hive dead, and the only one with figures worth printing.
    private void climate(List<Check> checks, Level level) {
        if (insulated()) {
            checks.add(new Check(Status.OK,
                Component.translatable("gui.mellifera.work.climate"),
                Component.translatable("gui.mellifera.work.insulated")));
            return;
        }

        BeeGenome genome = BeeStacks.genomeOf(menu.queenStack());
        if (genome == null) {
            return;
        }

        BeeSpecies species = MelliferaBeeSpecies.get(genome.species().active());
        ToleranceAllele tolerance = genome.tolerance().active();
        float low = species.minCelsius() - tolerance.widenBelow();
        float high = species.maxCelsius() + tolerance.widenAbove();
        float here = EnvironmentTemperature.celsius(level, menu.apiaryPos());

        boolean ok = here >= low && here <= high;
        checks.add(new Check(
            ok ? Status.OK : Status.BAD,
            Component.translatable(ok
                ? "gui.mellifera.work.climate"
                : here < low ? "gui.mellifera.work.climate_cold" : "gui.mellifera.work.climate_hot"),
            Component.translatable("gui.mellifera.work.climate_range",
                Math.round(here), Math.round(low), Math.round(high))));
    }

    /// The two things that decide how fast a working hive works: what there is to forage, and
    /// whether anyone is out foraging it.
    ///
    /// Neither can stop a hive, so neither is ever BAD. Between them they swing production by a
    /// factor of ten, which is far too much to leave a player to infer from a comb arriving late.
    private void forage(List<Check> checks, Level level) {
        int flowers = menu.flowers();
        int wanted = BeeHousingBlockEntity.FLOWERS_FOR_FULL_SPEED;
        boolean enough = flowers >= wanted;
        checks.add(new Check(
            enough ? Status.OK : Status.WARN,
            Component.translatable(enough
                ? "gui.mellifera.work.forage_ok"
                : "gui.mellifera.work.forage_poor"),
            Component.translatable("gui.mellifera.work.forage_count", flowers, wanted)));

        // Asked of the client's own world, which is where the answer lives: the hive syncs
        // nothing about the hour or the weather because both sides can already see them. Same
        // call the foragers themselves are gated on -- see BeeHousingBlockEntity.showsBees.
        boolean flying = Foraging.flying(level, menu.apiaryPos());
        checks.add(new Check(
            flying ? Status.OK : Status.WARN,
            Component.translatable(flying
                ? "gui.mellifera.work.foragers_out"
                : "gui.mellifera.work.foragers_in"),
            flying ? null : Component.translatable(level.isRaining()
                ? "gui.mellifera.work.foragers_rain"
                : "gui.mellifera.work.foragers_dark")));
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
        mark(graphics, iconX + 4, iconY + 4, verdict());
    }

    /// The worst thing the panel has to say, which is what the closed tab shows.
    ///
    /// Worst rather than "is it working": a hive standing in a desert at midnight is working, and
    /// saying only that would be true and useless. The bang is the tab's way of being worth
    /// opening.
    private Status verdict() {
        Status worst = Status.OK;
        for (Check check : checks()) {
            if (check.status() == Status.BAD) {
                return Status.BAD;
            }
            if (check.status() == Status.WARN) {
                worst = Status.WARN;
            }
        }

        return worst;
    }

    @Override
    protected void renderBody(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = contentLeft() + PADDING;
        int textLeft = left + MARK_WIDTH;
        int available = OPEN_WIDTH - PADDING * 2 - MARK_WIDTH;

        int rowY = bodyY();
        for (Check check : checks()) {
            mark(graphics, left, rowY + 1, check.status());
            clipped(graphics, check.label(), textLeft, rowY, available, color(check.status()));
            rowY += ROW_HEIGHT;

            // Indented under its label and dimmer than it: the figures are what you look at
            // after the line has told you which way to feel about them.
            if (check.detail() != null) {
                clipped(graphics, check.detail(), textLeft, rowY, available, DETAIL_COLOR);
                rowY += DETAIL_HEIGHT;
            }
        }
    }

    private static int color(Status status) {
        return switch (status) {
            case OK -> TITLE_COLOR;
            case WARN -> WARN_COLOR;
            case BAD -> BAD_COLOR;
        };
    }

    /// A tick or a cross drawn from filled pixels rather than a texture.
    ///
    /// Two glyphs of eight pixels each is less than the cost of an atlas entry, and drawing
    /// them by hand keeps them the same weight as the panel's own bevels -- a font glyph
    /// scaled into this space reads thinner than everything around it.
    private static void mark(GuiGraphicsExtractor graphics, int left, int top, Status status) {
        int color = switch (status) {
            case OK -> GOOD_COLOR;
            case WARN -> WARN_COLOR;
            case BAD -> BAD_COLOR;
        };

        switch (status) {
            case OK -> {
                for (int i = 0; i < 3; i++) {
                    graphics.fill(left + i, top + 3 + i, left + i + 1, top + 4 + i + 1, color);
                }
                for (int i = 0; i < 4; i++) {
                    graphics.fill(left + 3 + i, top + 5 - i, left + 4 + i, top + 6 - i, color);
                }
            }
            // A bar and a dot: the one glyph that reads as "look at this" without reading as
            // "this is broken", which is the whole distinction WARN exists to draw.
            case WARN -> {
                graphics.fill(left + 3, top, left + 5, top + 5, color);
                graphics.fill(left + 3, top + 6, left + 5, top + 8, color);
            }
            case BAD -> {
                for (int i = 0; i < 7; i++) {
                    graphics.fill(left + i, top + i, left + i + 1, top + i + 1, color);
                    graphics.fill(left + 6 - i, top + i, left + 7 - i, top + i + 1, color);
                }
            }
        }
    }

    @Override
    protected @Nullable Component tooltipAt(int mouseX, int mouseY) {
        return null;
    }
}
