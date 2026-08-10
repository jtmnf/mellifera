package com.joaonf.mellifera.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeBranch;
import com.joaonf.mellifera.bee.BeeMutation;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.bee.BeeTemplate;
import com.joaonf.mellifera.bee.CombProduct;
import com.joaonf.mellifera.bee.MutationCondition;
import com.joaonf.mellifera.client.special.BeePortraitRenderState;
import com.joaonf.mellifera.config.MelliferaOutputConfig;
import com.joaonf.mellifera.registry.MelliferaBeeBranches;
import com.joaonf.mellifera.registry.MelliferaBeeMutations;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaCombTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;

/// The mutation browser -- what NEI/JEI used to give you for Forestry's bees.
///
/// Reads MelliferaBeeSpecies and MelliferaBeeMutations directly rather than keeping its own copy
/// of the breeding tree, so it cannot drift out of date: if a mutation exists in the game it
/// is listed here, with the same chance and the same conditions the engine actually rolls
/// against. Both tables are plain static Java built identically on client and server, so no
/// syncing is involved.
///
/// The left-hand list is a two-level tree: one collapsible header per branch (see
/// [com.joaonf.mellifera.registry.MelliferaBeeBranches]) with its bees underneath. It is
/// still a flat list of [Row]s underneath -- headers and bees share one index space -- so
/// scrolling, hit-testing and the scrollbar work exactly as they did when it really was flat.
///
/// The detail pane is laid out into a list of positioned [Segment]s instead of being drawn
/// straight to the screen. Rendering and hit-testing then read the same geometry, which is
/// what makes the species names inside "Bred from"/"Leads to" clickable without their hot
/// zones drifting away from the glyphs. Layout is rebuilt only when the selection changes,
/// not every frame.
public class BeeGuideScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 216;
    private static final int LIST_WIDTH = 110;
    private static final int ROW_HEIGHT = 12;
    private static final int LINE_HEIGHT = 10;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int INDENT = 6;

    /// The bee portrait: a square in the top-right of the detail pane, with the header text
    /// flowing beside it rather than under it. Big enough to actually read the species colour
    /// off the abdomen, which a 16px item slot never was.
    private static final int PORTRAIT_SIZE = 76;
    private static final int PORTRAIT_GAP = 6;
    /// Model units to GUI pixels. A bee is a little under a block across, so a shade under
    /// the box size leaves the wingtips inside the frame at the angle it is posed at.
    private static final float PORTRAIT_SCALE = PORTRAIT_SIZE * 0.78F;

    private static final int PANEL = 0xF01A1512;
    private static final int BORDER = 0xFF6B573A;
    private static final int ROW_SELECTED = 0xFF4A3B24;
    private static final int ROW_HOVER = 0xFF2E2618;
    private static final int TEXT = 0xFFE8DCC0;
    private static final int LABEL = 0xFF9C8C6A;
    private static final int DIM = 0xFF7A6B4F;
    private static final int LINK = 0xFFD8A657;
    private static final int LINK_HOVER = 0xFFFFE0A0;
    private static final int SEARCH_BG = 0xFF120E0A;
    private static final int SCROLL_TRACK = 0xFF241C12;
    private static final int SCROLL_THUMB = 0xFF6B573A;

    /// One positioned run of text in the detail pane. `y` is relative to the top of the
    /// detail viewport so that scrolling is a single subtraction at draw time, and a
    /// non-null `link` marks the run as a jump to another species.
    private record Segment(FormattedCharSequence text, int x, int y, int width, int color, Identifier link) {}

    /// A line in the left-hand list: a branch header when `species` is null, one of its bees
    /// otherwise. Flattening the tree into rows up front is what keeps hit-testing and the
    /// scrollbar as simple as they were when the list was flat -- both still index rows.
    private record Row(BeeBranch branch, @Nullable Identifier species) {
        boolean isHeader() {
            return species == null;
        }
    }

    private final List<Identifier> allSpecies = new ArrayList<>();
    private final List<Identifier> visibleSpecies = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Set<BeeBranch> expanded = new HashSet<>();
    private final List<Segment> detail = new ArrayList<>();

    private EditBox search;

    private int left;
    private int top;
    private int scroll;
    private int detailScroll;
    private int detailHeight;
    private Identifier selected;
    private boolean draggingScrollbar;

    /// Cursor for the inline text flow used while building [#detail].
    private int flowX;
    private int flowY;

    public BeeGuideScreen() {
        super(Component.translatable("gui.mellifera.bee_guide.title"));
        // Grouped by branch, and inside a branch in breeding order -- so the list is itself a
        // reading of the tree: the wild bees at the top of Honey, each line's prize bee at
        // the bottom of its own group. A flat list of 57 names in tier order put unrelated
        // bees next to each other purely because they take the same number of crosses, which
        // is true and useless when what you are looking for is "the cold ones".
        //
        // Alphabetical was never the alternative: the search box does that better.
        for (BeeBranch branch : MelliferaBeeBranches.all()) {
            allSpecies.addAll(branch.species());
        }

        visibleSpecies.addAll(allSpecies);
        selected = allSpecies.isEmpty() ? null : allSpecies.get(0);
        // Everything else starts closed. Twenty headers is a table of contents; twenty
        // headers with all 57 bees under them is the flat list again with extra rows in it.
        expandOwnerOf(selected);
        rebuildRows();
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;

        // Handing the previous box to the constructor carries the typed query across a
        // window resize, which re-runs init() from scratch.
        search = new EditBox(font, left + 5, top + 20, LIST_WIDTH - 10, 11, search,
            Component.translatable("gui.mellifera.bee_guide.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(TEXT);
        search.setHint(Component.translatable("gui.mellifera.bee_guide.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(this::applyFilter);
        addRenderableWidget(search);
        setInitialFocus(search);

        applyFilter(search.getValue());
        rebuildDetail();
    }

    // ---------------------------------------------------------------- geometry

    private int listTop() {
        return top + 36;
    }

    private int listBottom() {
        return top + HEIGHT - 5;
    }

    private int visibleRows() {
        return (listBottom() - listTop()) / ROW_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRows());
    }

    private int rowRight() {
        return left + LIST_WIDTH - SCROLLBAR_WIDTH - 3;
    }

    private int detailLeft() {
        return left + LIST_WIDTH + 7;
    }

    private int detailWidth() {
        return WIDTH - LIST_WIDTH - 14;
    }

    private int detailTop() {
        return top + 19;
    }

    private int detailBottom() {
        return top + HEIGHT - 5;
    }

    private int maxDetailScroll() {
        return Math.max(0, detailHeight - (detailBottom() - detailTop()));
    }

    private int portraitLeft() {
        return detailLeft() + detailWidth() - PORTRAIT_SIZE;
    }

    /// How wide text may run at a given point in the detail flow. Everything level with the
    /// portrait has to stop short of it; below the portrait the full pane is available again.
    /// `y` is in the detail pane's own coordinates, the same ones [Segment#y] uses.
    private int flowWidth(int y) {
        return y < PORTRAIT_SIZE ? detailWidth() - PORTRAIT_SIZE - PORTRAIT_GAP : detailWidth();
    }

    // ---------------------------------------------------------------- filtering

    /// Matches the localised name and the registry path, so both "Imperial" and the
    /// `mellifera:imperial` id a pack author would type find the same bee. The branch's own
    /// name matches too: "frozen" is a reasonable thing to type when what you want is the
    /// three cold bees, and it is the one word none of them are called.
    private void applyFilter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        visibleSpecies.clear();
        for (BeeBranch branch : MelliferaBeeBranches.all()) {
            boolean wholeBranch = !needle.isEmpty() && name(branch).toLowerCase(Locale.ROOT).contains(needle);
            for (Identifier id : branch.species()) {
                if (needle.isEmpty() || wholeBranch || matches(id, needle)) {
                    visibleSpecies.add(id);
                }
            }
        }

        // A hit inside a closed branch opens it, rather than the list forcing every branch
        // open while a query is live. Same rows either way, but the arrows keep telling the
        // truth and a click on a header still collapses it mid-search.
        if (!needle.isEmpty()) {
            visibleSpecies.forEach(this::expandOwnerOf);
        }

        rebuildRows();
        scroll = 0;
    }

    private static boolean matches(Identifier id, String needle) {
        return displayName(id).getString().toLowerCase(Locale.ROOT).contains(needle)
            || id.getPath().contains(needle);
    }

    /// Flattens the branches into the rows the list actually draws: a header for every branch
    /// with something to show, followed by its bees when it is open. A branch with no bee
    /// left after filtering drops out entirely, header and all.
    private void rebuildRows() {
        rows.clear();
        for (BeeBranch branch : MelliferaBeeBranches.all()) {
            List<Identifier> members = new ArrayList<>();
            for (Identifier id : branch.species()) {
                if (visibleSpecies.contains(id)) {
                    members.add(id);
                }
            }

            if (members.isEmpty()) {
                continue;
            }

            rows.add(new Row(branch, null));
            if (expanded.contains(branch)) {
                for (Identifier id : members) {
                    rows.add(new Row(branch, id));
                }
            }
        }

        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private void expandOwnerOf(@Nullable Identifier id) {
        BeeBranch branch = id == null ? null : MelliferaBeeBranches.of(id);
        if (branch != null) {
            expanded.add(branch);
        }
    }

    private int rowOf(Identifier id) {
        for (int i = 0; i < rows.size(); i++) {
            if (id.equals(rows.get(i).species())) {
                return i;
            }
        }

        return -1;
    }

    /// Selecting from a mutation link can name a bee the current query filters out, or one
    /// sitting in a branch the player has closed, so both are undone rather than leaving the
    /// list pointing at nothing.
    private void select(Identifier id) {
        selected = id;
        if (!visibleSpecies.contains(id) && search != null && !search.getValue().isEmpty()) {
            search.setValue(""); // responder re-runs applyFilter, which rebuilds the rows
        }

        expandOwnerOf(id);
        rebuildRows();

        int index = rowOf(id);
        if (index >= 0) {
            if (index < scroll) {
                scroll = index;
            } else if (index >= scroll + visibleRows()) {
                scroll = index - visibleRows() + 1;
            }
            scroll = Mth.clamp(scroll, 0, maxScroll());
        }
        rebuildDetail();
    }

    /// Opens a closed branch, or closes an open one. Closing the branch the selected bee is
    /// in is allowed: the detail pane keeps showing it, so nothing is lost by tidying the
    /// list around it.
    private void toggle(BeeBranch branch) {
        if (!expanded.remove(branch)) {
            expanded.add(branch);
        }

        rebuildRows();
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.fill(left, top, left + WIDTH, top + 1, BORDER);
        graphics.fill(left, top + HEIGHT - 1, left + WIDTH, top + HEIGHT, BORDER);
        graphics.fill(left, top, left + 1, top + HEIGHT, BORDER);
        graphics.fill(left + WIDTH - 1, top, left + WIDTH, top + HEIGHT, BORDER);
        graphics.fill(left + LIST_WIDTH, top + 1, left + LIST_WIDTH + 1, top + HEIGHT - 1, BORDER);

        graphics.text(font, title, left + 6, top + 6, TEXT);
        graphics.text(font, Component.translatable("gui.mellifera.bee_guide.count", visibleSpecies.size(),
            allSpecies.size()), left + LIST_WIDTH + 7, top + 6, LABEL);

        // The box itself is drawn unbordered so it can sit in this recessed well rather
        // than the vanilla grey frame, which clashes with the parchment palette.
        graphics.fill(left + 3, top + 17, left + LIST_WIDTH - 3, top + 32, SEARCH_BG);
        graphics.fill(left + 3, top + 31, left + LIST_WIDTH - 3, top + 32, BORDER);

        renderList(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
        renderDetail(graphics, mouseX, mouseY);

        // Widgets last: Screen's own implementation draws the renderables, and the search
        // box has to land on top of the panel background rather than under it.
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (rows.isEmpty()) {
            graphics.text(font, Component.translatable("gui.mellifera.bee_guide.no_results"),
                left + 6, listTop() + 4, DIM);
            return;
        }

        for (int row = 0; row < visibleRows(); row++) {
            int index = row + scroll;
            if (index >= rows.size()) {
                break;
            }

            Row entry = rows.get(index);
            int rowY = listTop() + row * ROW_HEIGHT;
            boolean hovered = mouseX >= left + 2 && mouseX < rowRight()
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (!entry.isHeader() && entry.species().equals(selected)) {
                graphics.fill(left + 2, rowY, rowRight(), rowY + ROW_HEIGHT, ROW_SELECTED);
            } else if (hovered) {
                graphics.fill(left + 2, rowY, rowRight(), rowY + ROW_HEIGHT, ROW_HOVER);
            }

            if (entry.isHeader()) {
                renderHeaderRow(graphics, entry.branch(), rowY);
            } else {
                renderSpeciesRow(graphics, entry.species(), rowY);
            }
        }
    }

    private void renderHeaderRow(GuiGraphicsExtractor graphics, BeeBranch branch, int rowY) {
        arrow(graphics, left + 4, rowY + 3, expanded.contains(branch));

        Component label = Component.translatable(branch.translationKey()).copy().withStyle(ChatFormatting.BOLD);
        graphics.text(font, clip(label, rowRight() - (left + 13) - 2), left + 13, rowY + 2, LABEL);
    }

    private void renderSpeciesRow(GuiGraphicsExtractor graphics, Identifier id, int rowY) {
        BeeSpecies bee = MelliferaBeeSpecies.get(id);
        // A swatch of the species' own colour, so the list reads at a glance.
        graphics.fill(left + INDENT + 4, rowY + 2, left + INDENT + 11, rowY + 9, 0xFF000000 | bee.primaryColor());
        graphics.text(font, clip(displayName(id), rowRight() - (left + INDENT + 14) - 2),
            left + INDENT + 14, rowY + 2, TEXT);
    }

    /// The open/closed marker, drawn as pixels rather than as a glyph. The obvious characters
    /// for it are outside the range Vanilla's default font covers, so a triangle typed into
    /// the lang file would come out as a missing-glyph box on some fonts and not others.
    private static void arrow(GuiGraphicsExtractor graphics, int x, int y, boolean open) {
        for (int step = 0; step < 3; step++) {
            if (open) {
                // Pointing down: a row per step, each one two pixels narrower.
                graphics.fill(x + step, y + step, x + 5 - step, y + step + 1, LABEL);
            } else {
                // Pointing right: a column per step, each one two pixels shorter.
                graphics.fill(x + step, y + step, x + step + 1, y + 5 - step, LABEL);
            }
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics) {
        int x0 = left + LIST_WIDTH - SCROLLBAR_WIDTH - 2;
        int x1 = x0 + SCROLLBAR_WIDTH;
        int trackTop = listTop();
        int trackHeight = listBottom() - trackTop;
        graphics.fill(x0, trackTop, x1, trackTop + trackHeight, SCROLL_TRACK);

        int max = maxScroll();
        if (max <= 0) {
            graphics.fill(x0, trackTop, x1, trackTop + trackHeight, SCROLL_THUMB);
            return;
        }

        int thumbHeight = Math.max(12, trackHeight * visibleRows() / rows.size());
        int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / max;
        graphics.fill(x0, thumbY, x1, thumbY + thumbHeight, SCROLL_THUMB);
    }

    private void renderDetail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (detail.isEmpty()) {
            return;
        }

        // Scissoring is the backstop for anything the layout could not shorten -- a long
        // wrapped run near the bottom edge is cut cleanly instead of spilling out of the
        // panel and over the world.
        graphics.enableScissor(detailLeft(), detailTop(), detailLeft() + detailWidth(), detailBottom());
        renderPortrait(graphics);
        for (Segment segment : detail) {
            int x = detailLeft() + segment.x();
            int y = detailTop() + segment.y() - detailScroll;
            if (y + LINE_HEIGHT < detailTop() || y > detailBottom()) {
                continue;
            }

            int color = segment.color();
            if (segment.link() != null && mouseX >= x && mouseX < x + segment.width()
                && mouseY >= y - 1 && mouseY < y + LINE_HEIGHT - 1) {
                color = LINK_HOVER;
                graphics.fill(x, y + 8, x + segment.width(), y + 9, LINK_HOVER);
            }
            graphics.text(font, segment.text(), x, y, color);
        }
        graphics.disableScissor();

        int max = maxDetailScroll();
        if (max > 0) {
            int x1 = left + WIDTH - 3;
            int trackHeight = detailBottom() - detailTop();
            int thumbHeight = Math.max(12, trackHeight * trackHeight / detailHeight);
            int thumbY = detailTop() + (trackHeight - thumbHeight) * detailScroll / max;
            graphics.fill(x1 - 2, thumbY, x1, thumbY + thumbHeight, SCROLL_THUMB);
        }
    }

    /// The selected bee, drawn large enough to be worth looking at.
    ///
    /// Scrolls with the text rather than staying pinned, so the header keeps its relationship
    /// to the picture it belongs to and nothing ever slides underneath it. It is submitted
    /// inside the detail pane's scissor, which the picture-in-picture blit honours, so it is
    /// clipped by the pane like everything else.
    ///
    /// The clock is the same wrapped wall clock the item icons use, so the portrait and the
    /// icons beat their wings in step -- and off wall time rather than level time, since the
    /// guide opens over the world but does not pause it.
    private void renderPortrait(GuiGraphicsExtractor graphics) {
        if (selected == null || !MelliferaBeeSpecies.REGISTRY.containsKey(selected)) {
            return;
        }

        int y0 = detailTop() - detailScroll;
        int y1 = y0 + PORTRAIT_SIZE;
        if (y1 < detailTop() || y0 > detailBottom()) {
            return;
        }

        graphics.submitPictureInPictureRenderState(new BeePortraitRenderState(
            MelliferaBeeSpecies.get(selected).primaryColor(),
            false,
            true,
            (Util.getMillis() % 1_000_000L) / 50.0F,
            portraitLeft(),
            y0,
            portraitLeft() + PORTRAIT_SIZE,
            y1,
            PORTRAIT_SCALE,
            graphics.peekScissorStack()));
    }

    // ---------------------------------------------------------------- detail layout

    private void rebuildDetail() {
        detail.clear();
        detailScroll = 0;
        detailHeight = 0;
        flowX = 0;
        flowY = 0;

        if (selected == null || !MelliferaBeeSpecies.REGISTRY.containsKey(selected)) {
            return;
        }

        Identifier id = selected;
        BeeSpecies bee = MelliferaBeeSpecies.get(id);

        paragraph(displayName(id).copy().withStyle(ChatFormatting.BOLD), 0, TEXT);
        flowY += 3;

        paragraph(Component.translatable("gui.mellifera.bee_guide.climate",
            (int) bee.minCelsius() + "°C .. " + (int) bee.maxCelsius() + "°C"), 0, LABEL);
        paragraph(Component.translatable("gui.mellifera.bee_guide.inheritance",
            Component.translatable(bee.dominant()
                ? "gui.mellifera.bee_guide.dominant"
                : "gui.mellifera.bee_guide.recessive")), 0, LABEL);
        paragraph(Component.translatable("gui.mellifera.bee_guide.produces", products(id)), 0, LABEL);

        // Clear the portrait before the traits table: it is two columns, and squeezing them
        // into the leftover strip beside the bee would cost more than the blank space does.
        // Everything from here down has the full pane width, which is what traits() and the
        // mutation rows assume.
        flowY = Math.max(flowY + 4, PORTRAIT_SIZE + 4);
        section("gui.mellifera.bee_guide.traits");
        traits(bee.template());

        flowY += 4;
        section("gui.mellifera.bee_guide.bred_from");
        boolean any = false;
        for (BeeMutation mutation : MelliferaBeeMutations.all()) {
            if (mutation.result().equals(id)) {
                mutationRow(mutation, mutation.parentA(), mutation.parentB(), null);
                any = true;
            }
        }
        if (!any) {
            paragraph(Component.translatable("gui.mellifera.bee_guide.no_recipe"), INDENT, DIM);
        }

        flowY += 4;
        section("gui.mellifera.bee_guide.leads_to");
        any = false;
        for (BeeMutation mutation : MelliferaBeeMutations.all()) {
            if (mutation.parentA().equals(id) || mutation.parentB().equals(id)) {
                Identifier partner = mutation.parentA().equals(id) ? mutation.parentB() : mutation.parentA();
                mutationRow(mutation, id, partner, mutation.result());
                any = true;
            }
        }
        if (!any) {
            paragraph(Component.translatable("gui.mellifera.bee_guide.no_offspring"), INDENT, DIM);
        }

        detailHeight = flowY;
    }

    private void section(String key) {
        paragraph(Component.translatable(key).withStyle(ChatFormatting.BOLD), 0, LABEL);
    }

    /// Traits go in two columns because all seven chromosomes stacked vertically would push
    /// the mutation lists -- the part people actually came for -- off the first screenful.
    private void traits(BeeTemplate template) {
        Component[] rows = {
            trait("speed", template.speed()),
            trait("lifespan", template.lifespan()),
            trait("fertility", template.fertility()),
            trait("territory", template.territory()),
            trait("tolerance", template.tolerance()),
            trait("effect", template.effect()),
            trait("flowering", template.flowering()),
        };

        int column = detailWidth() / 2;
        for (int i = 0; i < rows.length; i++) {
            FormattedCharSequence text = clip(rows[i], column - 4);
            detail.add(new Segment(text, INDENT + (i % 2) * column, flowY, font.width(text), TEXT, null));
            if (i % 2 == 1 || i == rows.length - 1) {
                flowY += LINE_HEIGHT;
            }
        }
    }

    private static Component trait(String name, StringRepresentable allele) {
        return Component.translatable("tooltip.mellifera.bee." + name,
            Component.translatable("allele.mellifera." + name + "." + allele.getSerializedName()));
    }

    /// A null `result` means the row is a recipe *for* the selected bee, so only the two
    /// parents are shown; otherwise it is a recipe the selected bee feeds into and the
    /// offspring is spelled out after the arrow.
    private void mutationRow(BeeMutation mutation, Identifier first, Identifier second, Identifier result) {
        flowX = INDENT;
        inline(displayName(first), LINK, first);
        inline(Component.literal(" + "), DIM, null);
        inline(displayName(second), LINK, second);
        if (result != null) {
            inline(Component.literal(" → "), DIM, null);
            inline(displayName(result), LINK, result);
        }
        inline(Component.literal("  " + chance(mutation.baseChance())), TEXT, null);
        flowY += LINE_HEIGHT;

        Component condition = condition(mutation.condition());
        if (condition != null) {
            paragraph(condition, INDENT + 6, DIM);
        }
    }

    /// Appends a wrapped block of text; every line becomes its own segment so the caller
    /// never has to know how many rows it ended up taking.
    private void paragraph(Component text, int indent, int color) {
        for (FormattedCharSequence line : font.split(text, flowWidth(flowY) - indent)) {
            detail.add(new Segment(line, indent, flowY, font.width(line), color, null));
            flowY += LINE_HEIGHT;
        }
    }

    /// Appends one run on the current line, breaking to the next when it would run past the
    /// right edge. Keeps the clickable species names as whole, individually hit-testable
    /// runs, which wrapping the joined string would not.
    private void inline(Component text, int color, Identifier link) {
        int width = font.width(text);
        if (flowX > INDENT && flowX + width > detailWidth()) {
            flowY += LINE_HEIGHT;
            flowX = INDENT + 6;
        }
        detail.add(new Segment(text.getVisualOrderText(), flowX, flowY, width, color, link));
        flowX += width;
    }

    // ---------------------------------------------------------------- formatting

    private static Component products(Identifier species) {
        StringBuilder products = new StringBuilder();
        for (CombProduct product : MelliferaOutputConfig.combsOf(species)) {
            if (products.length() > 0) {
                products.append(", ");
            }
            products.append(Component.translatable(MelliferaCombTypes.get(product.comb()).translationKey()).getString())
                .append(" ").append(chance(product.chance()));
        }
        return Component.literal(products.toString());
    }

    /// Sub-1% mutation odds are real in the deeper branches; rounding them to a flat "0%"
    /// would read as "impossible".
    private static String chance(float value) {
        float percent = value * 100.0F;
        return percent < 1.0F
            ? String.format(Locale.ROOT, "%.1f%%", percent)
            : Math.round(percent) + "%";
    }

    /// Conditions are the difference between "this never works" and "this needs the Nether",
    /// so they belong on the row rather than buried.
    private static Component condition(MutationCondition condition) {
        return switch (condition) {
            case MutationCondition.RequiresBiomeTag tag ->
                Component.translatable("gui.mellifera.bee_guide.cond.biome", pretty(tag.tag().location().getPath()));
            case MutationCondition.RequiresClimate climate ->
                Component.translatable("gui.mellifera.bee_guide.cond.climate",
                    (int) climate.minCelsius(), (int) climate.maxCelsius());
            case MutationCondition.RequiresDateRange date ->
                Component.translatable("gui.mellifera.bee_guide.cond.date",
                    date.startMonth() + "/" + date.startDay(), date.endMonth() + "/" + date.endDay());
            case MutationCondition.RequiresBlockNearby block ->
                Component.translatable("gui.mellifera.bee_guide.cond.block", pretty(block.tag().location().getPath()));
            case MutationCondition.RequiresFluidNearby fluid ->
                Component.translatable("gui.mellifera.bee_guide.cond.fluid", pretty(fluid.tag().location().getPath()));
            case MutationCondition.None ignored -> null;
        };
    }

    /// Tag paths are the only user-facing string here with no lang key of its own, so they
    /// get title-cased rather than shown as raw `is_nether/foo`.
    private static String pretty(String path) {
        String tail = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        return tail.isEmpty() ? path : Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
    }

    /// Truncates at a word boundary by borrowing the wrapper and keeping only its first row.
    private FormattedCharSequence clip(Component text, int width) {
        List<FormattedCharSequence> lines = font.split(text, width);
        return lines.isEmpty() ? text.getVisualOrderText() : lines.get(0);
    }

    private static Component displayName(Identifier id) {
        return Component.translatable(MelliferaBeeSpecies.get(id).translationKey());
    }

    private static String name(BeeBranch branch) {
        return Component.translatable(branch.translationKey()).getString();
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Widgets first: otherwise a click on the search box would be swallowed by the
        // list hit-test below, which covers the same column.
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        double mouseX = event.x();
        double mouseY = event.y();

        if (mouseX >= left + LIST_WIDTH - SCROLLBAR_WIDTH - 2 && mouseX < left + LIST_WIDTH - 2
            && mouseY >= listTop() && mouseY < listBottom()) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return true;
        }

        if (mouseX >= left + 2 && mouseX < rowRight() && mouseY >= listTop() && mouseY < listBottom()) {
            int index = (int) ((mouseY - listTop()) / ROW_HEIGHT) + scroll;
            if (index >= 0 && index < rows.size()) {
                Row entry = rows.get(index);
                if (entry.isHeader()) {
                    toggle(entry.branch());
                } else {
                    select(entry.species());
                }
                return true;
            }
        }

        if (mouseX >= detailLeft() && mouseX < left + WIDTH && mouseY >= detailTop() && mouseY < detailBottom()) {
            for (Segment segment : detail) {
                if (segment.link() == null) {
                    continue;
                }
                int x = detailLeft() + segment.x();
                int y = detailTop() + segment.y() - detailScroll;
                if (mouseX >= x && mouseX < x + segment.width() && mouseY >= y - 1 && mouseY < y + LINE_HEIGHT - 1) {
                    select(segment.link());
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            dragScrollbar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    private void dragScrollbar(double mouseY) {
        int trackHeight = listBottom() - listTop();
        int thumbHeight = Math.max(12, rows.isEmpty()
            ? trackHeight
            : trackHeight * visibleRows() / rows.size());
        int span = Math.max(1, trackHeight - thumbHeight);
        double fraction = (mouseY - listTop() - thumbHeight / 2.0) / span;
        scroll = Mth.clamp((int) Math.round(fraction * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int step = (int) Math.signum(scrollY);
        if (x >= detailLeft() && x < left + WIDTH) {
            detailScroll = Mth.clamp(detailScroll - step * LINE_HEIGHT, 0, maxDetailScroll());
        } else {
            scroll = Mth.clamp(scroll - step, 0, maxScroll());
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
