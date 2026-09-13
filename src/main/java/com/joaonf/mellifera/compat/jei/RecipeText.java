package com.joaonf.mellifera.compat.jei;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/// Text on a JEI page, broken to fit the room it has.
///
/// WHY IT EXISTS. These pages were drawing every line with a single `graphics.text` at a fixed
/// position, which draws the whole string whatever its length -- so a line longer than the gap
/// between the slots and the category's right edge simply ran out of the page and over JEI's
/// own ingredient list. The Isolator was the worst of them: "The bee is used up; all 8 genes
/// come out" is about three times the width it had.
///
/// Widening the categories alone would not have fixed it. The lines are translated, and a
/// translator has no way to know what a category is wide enough for -- a German or Portuguese
/// rendering of the same sentence is routinely half again as long. So the pages ask for the
/// width they have and the text is wrapped into it, and the category heights below are sized
/// for the wrapped worst case rather than for the English one line.
final class RecipeText {
    /// The near-black every one of these pages was already using.
    static final int COLOR = 0xFF202020;

    /// Vanilla's line height. Font.lineHeight is 9; the extra pixel is the leading the pages
    /// were already spacing their fixed lines by.
    static final int LINE_HEIGHT = 10;

    private RecipeText() {}

    /// Draws `text` at (x, y), wrapped so no line passes `maxWidth`, and hands back the y the
    /// line after it would start at -- so a caller can stack lines without knowing how many
    /// each one turned into.
    static int draw(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int maxWidth) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            graphics.text(font, line, x, y, COLOR, false);
            y += LINE_HEIGHT;
        }

        return y;
    }
}
