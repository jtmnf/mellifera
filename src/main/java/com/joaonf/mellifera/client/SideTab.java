package com.joaonf.mellifera.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;

/// A retractable panel hanging off the right edge of a container window, in the style of
/// Thermal Expansion's side tabs: a closed square you click to slide open.
///
/// Everything that is the same between one tab and the next lives here -- the chrome, the
/// slide, where it sits, what a click on the header means -- so a new tab is a subclass with
/// a size and a body to draw, not another copy of the bevel code. That mattered as soon as
/// there were two: the objective tab and the frame tab have nothing in common except being
/// tabs, and the half of them that is "being a tab" is by far the larger half.
///
/// Deliberately not a Vanilla AbstractWidget. Widgets are added in init() and take part in
/// the screen's focus order, and a tab that steals tab-key focus is worse than one drawn by
/// hand. What this needs from a widget -- a rectangle, a click, a tooltip -- is three methods.
public abstract class SideTab {
    /// Closed, only this square sticks out past the window's edge.
    public static final int TAB_WIDTH = 22;
    public static final int TAB_HEIGHT = 24;

    /// Ticks the slide takes. Short enough not to be in the way, long enough to read as a
    /// panel opening rather than a texture swap.
    private static final int OPEN_TICKS = 4;

    protected static final int SLOT_SIZE = 18;
    protected static final int PADDING = 6;

    /// Dark panel, light text. Vanilla's font draws a drop shadow by default, so dark text
    /// on a mid-tone background is a dark glyph on its own dark smear; light-on-dark is what
    /// the shadow is designed for.
    ///
    /// Everything is drawn as a bevel rather than a flat rectangle: one dark outline, a lit
    /// top/left edge and a shaded bottom/right one. That is the whole visual grammar of a
    /// Vanilla GUI -- panels come out of the screen, slots go into it -- and a flat filled
    /// box beside a bevelled window reads as a placeholder whatever colours it uses.
    protected static final int OUTLINE = 0xFF080605;
    protected static final int FACE = 0xF01D1811;
    protected static final int BEVEL_LIGHT = 0xFF473C2D;
    protected static final int BEVEL_DARK = 0xFF0F0C08;
    protected static final int SLOT_FACE = 0xFF120F0A;

    protected static final int TITLE_COLOR = 0xFFE0D8C8;
    protected static final int ACCENT_COLOR = 0xFFF4C542;
    protected static final int EMPTY_COLOR = 0xFF8A8070;
    protected static final int GOOD_COLOR = 0xFF7BD86B;
    protected static final int SEPARATOR = 0xFF2E271D;

    /// The stripe down the panel's left edge, where it meets the window.
    private static final int ACCENT_IDLE = 0xFF473C2D;

    /// Hovers are overlays, not colour swaps: a translucent wash keeps the bevel and the
    /// text underneath visible. SLOT_HOVER is Vanilla's own 0x80FFFFFF, unchanged.
    private static final int PANEL_HOVER = 0x18FFFFFF;
    protected static final int SLOT_HOVER = 0x80FFFFFF;


    /// Which edge of the window a tab hangs off.
    ///
    /// Both sides exist because the two kinds of panel answer different questions: what the
    /// hive is *set to* sits on the right, and whether it *can work at all* sits on the left,
    /// where the eye lands first. Keeping them apart means a player never has to remember
    /// which tab was which.
    public enum Side {
        LEFT,
        RIGHT
    }

    protected final Font font;
    private final String titleKey;
    private final Side side;

    private boolean open;

    /// How far through the slide the panel is, in whole ticks. Advanced by tick() and
    /// interpolated against the frame's partial tick at render time, which is the only way
    /// to get a smooth slide: partialTick is a fraction *within* the current tick, not
    /// elapsed time, so accumulating it per frame would run the animation at the frame rate.
    private int openTicks;

    /// Where the tab meets the window -- its left edge on the right side, its right edge on
    /// the left side. Recomputed every frame from the screen's own origin so it follows a
    /// window resize without an init() hook.
    protected int x;
    protected int y;

    protected SideTab(Font font, String titleKey, Side side) {
        this.font = font;
        this.titleKey = titleKey;
        this.side = side;
    }

    /// The panel's left edge for a given drawn width. A right-hand tab grows away from the
    /// window; a left-hand one grows back towards the screen edge, so its origin moves as it
    /// opens while the seam stays put.
    protected int left(int width) {
        return side == Side.RIGHT ? x : x - width;
    }

    /// The left edge at the panel's settled size, which is what body content lays out from.
    protected int contentLeft() {
        return left(isOpen() ? openWidth() : TAB_WIDTH);
    }

    protected abstract int openWidth();

    protected abstract int openHeight();

    /// Drawn in the middle of the closed tab, and while the panel is sliding.
    protected abstract void renderTabIcon(GuiGraphicsExtractor graphics, int iconX, int iconY);

    /// Everything below the header, drawn only once the panel is fully open.
    protected abstract void renderBody(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

    /// True when the accent stripe should light up -- the tab carrying state a closed panel
    /// should still advertise.
    protected boolean accented() {
        return false;
    }

    /// A click inside the open panel that is not on the header. Return true to consume it.
    protected boolean clickedBody(double mouseX, double mouseY) {
        return false;
    }

    /// Points the panel must *not* swallow, because something behind it needs them.
    ///
    /// A panel holding real Slots cannot consume every click inside itself: the container
    /// handles slot clicks, and it only gets the ones this class lets through. Without this
    /// the frame slots could be filled by shift-clicking but never emptied, because taking
    /// an item out is a click that lands squarely inside the panel.
    protected boolean transparentAt(double mouseX, double mouseY) {
        return false;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    /// Anchors the tab against its side of the window, `offsetY` below the window's top.
    public void reposition(int guiLeft, int guiTop, int imageWidth, int offsetY) {
        this.x = side == Side.RIGHT ? guiLeft + imageWidth : guiLeft;
        this.y = guiTop + PADDING + offsetY;
    }

    /// The screen space this tab occupies, for JEI to lay its ingredient list out around
    /// (see ApiaryGuiHandler). Without it JEI draws its list over the panel, and JEI owns
    /// the mouse in its own area, so anything underneath becomes unreachable.
    ///
    /// Reports the settled size rather than the interpolated one on purpose: `open` flips
    /// the instant the tab is clicked, so JEI reflows once and the panel then slides into
    /// space already cleared, instead of JEI relaying out on every frame of the slide.
    public Rect2i bounds() {
        int width = open ? openWidth() : TAB_WIDTH;
        int height = open ? openHeight() : TAB_HEIGHT;
        // Grows by the outline on the three sides that have one; the seam side is flush
        // against the window's own border and carries none.
        return new Rect2i(left(width), y - 1, width + 1, height + 2);
    }

    /// How far down the next tab in the strip should start.
    public int stackHeight() {
        return (open ? openHeight() : TAB_HEIGHT) + 3;
    }

    public void tick() {
        openTicks = Mth.clamp(openTicks + (open ? 1 : -1), 0, OPEN_TICKS);
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float openness = Mth.clamp((openTicks + (open ? partialTick : -partialTick)) / OPEN_TICKS, 0.0F, 1.0F);
        int width = Math.round(TAB_WIDTH + (openWidth() - TAB_WIDTH) * openness);
        int height = Math.round(TAB_HEIGHT + (openHeight() - TAB_HEIGHT) * openness);

        int panelLeft = left(width);
        raisedPanel(graphics, panelLeft, y, width, height, side);

        // The accent stripe sits on the seam, which is the lit bevel on either side now that
        // the frame is mirrored -- so it costs the same pixel on the left as on the right.
        int accentX = side == Side.RIGHT ? panelLeft : panelLeft + width - 1;
        graphics.fill(accentX, y, accentX + 1, y + height, accented() ? ACCENT_COLOR : ACCENT_IDLE);

        if (inToggleArea(mouseX, mouseY)) {
            // Clamped to the width actually drawn this frame: `open` flips the instant it is
            // clicked but the slide takes four ticks, so an unclamped strip would paint the
            // hover out past the panel's own edge mid-animation.
            int stripHeight = Math.min(open ? headerHeight() : TAB_HEIGHT, height);
            graphics.fill(panelLeft + 1, y + 1, panelLeft + width - 1, y + stripHeight, PANEL_HOVER);
        }

        if (openness < 1.0F) {
            renderTabIcon(graphics, panelLeft + (width - 16) / 2, y + (TAB_HEIGHT - 16) / 2);
            return;
        }

        graphics.text(font, Component.translatable(titleKey), panelLeft + PADDING, y + PADDING, TITLE_COLOR);
        graphics.fill(panelLeft + 1, y + headerHeight(), panelLeft + width - 1, y + headerHeight() + 1, SEPARATOR);
        renderBody(graphics, mouseX, mouseY);
    }

    /// Returns true when the click belonged to this tab, so the screen can stop there.
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (open && clickedBody(mouseX, mouseY)) {
            return true;
        }

        if (inToggleArea(mouseX, mouseY)) {
            open = !open;
            return true;
        }

        // A click anywhere else inside an open panel is still the panel's -- otherwise it
        // would fall through to the inventory behind it -- unless it is over something the
        // panel is only drawing the frame for.
        return open && !transparentAt(mouseX, mouseY)
            && contains(mouseX, mouseY, left(openWidth()), y, openWidth(), openHeight());
    }

    /// What toggles the panel.
    ///
    /// Closed, the whole tab. Open, only the title strip: the tab rectangle overlaps whatever
    /// the body puts directly under the header, and a click landing in that overlap belongs
    /// to the body, not to collapsing the panel out from under the player.
    private boolean inToggleArea(double mouseX, double mouseY) {
        return !open
            ? contains(mouseX, mouseY, left(TAB_WIDTH), y, TAB_WIDTH, TAB_HEIGHT)
            : contains(mouseX, mouseY, left(openWidth()), y, openWidth(), headerHeight());
    }

    protected int headerHeight() {
        return PADDING + font.lineHeight + 2;
    }

    /// Where the body's first row of content starts.
    protected int bodyY() {
        return y + headerHeight() + 3;
    }

    // -- chrome -----------------------------------------------------------------------------

    /// A panel that stands out of the screen, mirrored to the side it hangs off.
    ///
    /// The two edges that flip are the vertical ones. A right-hand tab attaches on its left
    /// and points away to the right, so its outer edge -- the one that carries the outline
    /// and the shadow -- is the right one. A left-hand tab is the same panel reflected: it
    /// attaches on its right and its outer edge is the left one, so the outline and the
    /// shadow go there and the lit bevel goes on the seam.
    ///
    /// Top and bottom do not flip. A mirror is horizontal, and the light stays where it is
    /// for every other widget in the mod: above.
    ///
    /// Neither side gets an outline on its seam edge. That edge butts against the window's
    /// own border, and a second dark line there draws a seam through what should read as one
    /// continuous piece of chrome.
    protected static void raisedPanel(GuiGraphicsExtractor graphics, int left, int top, int width, int height, Side side) {
        int right = left + width;
        int bottom = top + height;
        boolean seamOnLeft = side == Side.RIGHT;

        graphics.fill(seamOnLeft ? left : left - 1, top - 1, seamOnLeft ? right + 1 : right, bottom + 1, OUTLINE);
        graphics.fill(left, top, right, bottom, FACE);

        graphics.fill(left, top, right - 1, top + 1, BEVEL_LIGHT);
        graphics.fill(left + 1, bottom - 1, right, bottom, BEVEL_DARK);

        if (seamOnLeft) {
            graphics.fill(left, top, left + 1, bottom, BEVEL_LIGHT);
            graphics.fill(right - 1, top + 1, right, bottom, BEVEL_DARK);
        } else {
            graphics.fill(right - 1, top, right, bottom, BEVEL_LIGHT);
            graphics.fill(left, top + 1, left + 1, bottom, BEVEL_DARK);
        }
    }

    /// The same bevel inverted, which is all that separates a slot from a panel in Vanilla's
    /// visual language: shaded top and left, lit bottom and right, so it reads as pressed
    /// into the surface and as somewhere an item belongs.
    public static void insetSlot(GuiGraphicsExtractor graphics, int left, int top, int size) {
        insetRect(graphics, left, top, size, size);
    }

    /// A slot's bevel at any proportion, for the things that are pressed into a panel without
    /// being square -- the energy well is 6 by 48. One definition of the inset look, so a well
    /// and a slot cannot end up lit from different directions.
    public static void insetRect(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
        int right = left + width;
        int bottom = top + height;

        graphics.fill(left, top, right, bottom, SLOT_FACE);
        graphics.fill(left, top, right - 1, top + 1, BEVEL_DARK);
        graphics.fill(left, top, left + 1, bottom, BEVEL_DARK);
        graphics.fill(left + 1, bottom - 1, right, bottom, BEVEL_LIGHT);
        graphics.fill(right - 1, top + 1, right, bottom, BEVEL_LIGHT);
    }

    /// Draws text cut off at `available` pixels. Names and status lines both run past the
    /// panel's own border otherwise, and text spilling over a bevel looks like a bug.
    protected void clipped(GuiGraphicsExtractor graphics, Component text, int textX, int textY, int available, int color) {
        FormattedText fitted = font.substrByWidth(text, available);
        graphics.text(font, Language.getInstance().getVisualOrder(fitted), textX, textY, color);
    }

    protected static boolean contains(double pointX, double pointY, int left, int top, int width, int height) {
        return pointX >= left && pointX < left + width && pointY >= top && pointY < top + height;
    }

    protected @Nullable Component tooltipAt(int mouseX, int mouseY) {
        return null;
    }

    /// Exposed so the screen can ask every tab for a tooltip without knowing what it holds.
    public @Nullable Component tooltip(int mouseX, int mouseY) {
        return open ? tooltipAt(mouseX, mouseY) : null;
    }
}
