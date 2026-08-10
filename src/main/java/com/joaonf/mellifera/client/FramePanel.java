package com.joaonf.mellifera.client;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.FrameType;
import com.joaonf.mellifera.item.FrameItem;
import com.joaonf.mellifera.registry.MelliferaItems;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/// The apiary's three frame slots, moved out of the window and onto a tab.
///
/// They used to sit in a column inside the main window, between the queen and the comb
/// cluster, which spent a third of the window's width on three slots that are empty in most
/// hives most of the time. Out here they cost nothing when the tab is shut, and the window
/// closes up around what is actually being watched.
///
/// The slots themselves stay real Slots owned by ApiaryMenu; this panel only draws the
/// chrome around them.
///
/// They cannot be moved: Slot.x and Slot.y are final. So they are built at this panel's
/// coordinates from the start and hidden with isActive() when the tab is shut, which is the
/// only mechanism Vanilla offers -- an inactive slot is neither drawn nor hit-tested. The
/// screen sets that flag; it never leaves the client, so the server keeps treating the slots
/// as ordinary and hoppers and shift-clicking go on working with the tab shut.
public class FramePanel extends SideTab {
    private static final int OPEN_WIDTH = 82;

    /// Three rows of three: one row per apiary in the stack. The panel is always this tall
    /// and the rows simply light up as the hive grows -- a panel that changed size would move
    /// the slots, and Slot coordinates are fixed at construction.
    private static final int OPEN_HEIGHT = 92;

    private static final int COLUMNS = 3;
    private static final int PITCH = SLOT_SIZE + 4;

    private final List<Slot> frameSlots;

    public FramePanel(Font font, List<Slot> frameSlots) {
        super(font, "gui.mellifera.frames.title", Side.RIGHT);
        this.frameSlots = frameSlots;
    }

    @Override
    protected int openWidth() {
        return OPEN_WIDTH;
    }

    @Override
    protected int openHeight() {
        return OPEN_HEIGHT;
    }

    /// Lit whenever a frame is installed, so a shut tab still says the hive is being helped.
    @Override
    protected boolean accented() {
        return frameSlots.stream().anyMatch(Slot::hasItem);
    }


    @Override
    protected void renderTabIcon(GuiGraphicsExtractor graphics, int iconX, int iconY) {
        // Whatever is in the first occupied slot, so the closed tab shows the hive's own
        // frame rather than a generic one; a plain frame when there is nothing in at all.
        ItemStack icon = frameSlots.stream().map(Slot::getItem).filter(stack -> !stack.isEmpty())
            .findFirst().orElse(new ItemStack(MelliferaItems.FRAME.get()));
        graphics.fakeItem(icon, iconX, iconY);

        if (!accented()) {
            // Nothing installed: wash the icon back so it reads as a label for the tab and
            // not as a frame the player already owns.
            graphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x90000000);
        }
    }

    @Override
    protected void renderBody(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < frameSlots.size(); i++) {
            // A slot the hive has not grown into yet is not drawn at all. isActive is the
            // same flag the container uses to hide it, so the box and the slot can never
            // disagree about which frames this hive has.
            if (!frameSlots.get(i).isActive()) {
                continue;
            }

            int sx = slotX(i);
            int sy = slotY(i);
            insetSlot(graphics, sx, sy, SLOT_SIZE);

            // Only the empty ones get a ghost. The occupied ones are drawn by the container
            // itself, after this panel -- see ApiaryScreen.extractContents for why the order
            // is that way round.
            if (!frameSlots.get(i).hasItem()) {
                ghostFrame(graphics, sx + 1, sy + 1);
            }

            if (contains(mouseX, mouseY, sx, sy, SLOT_SIZE, SLOT_SIZE)) {
                graphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, SLOT_HOVER);
            }
        }
    }

    /// A dimmed frame in an empty slot, the way Vanilla marks an empty armour slot.
    ///
    /// An empty inset square says "something goes here" but not what, and a frame slot is the
    /// one place in this GUI where that is a real question -- nothing else in the apiary takes
    /// a frame, and nothing tells you frames exist.
    private static void ghostFrame(GuiGraphicsExtractor graphics, int iconX, int iconY) {
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("mellifera", "textures/item/base_frame_wood.png"),
            iconX, iconY, 0.0F, 0.0F, 16, 16, 16, 16, 0x50FFFFFF);
    }

    @Override
    protected @Nullable Component tooltipAt(int mouseX, int mouseY) {
        for (int i = 0; i < frameSlots.size(); i++) {
            if (!frameSlots.get(i).isActive() || !contains(mouseX, mouseY, slotX(i), slotY(i), SLOT_SIZE, SLOT_SIZE)) {
                continue;
            }

            // Only the empty slots get a hint here; a slot with a frame in it already has
            // the item's own tooltip, which AbstractContainerScreen draws.
            return frameSlots.get(i).hasItem() ? null : Component.translatable("gui.mellifera.frames.empty");
        }

        return null;
    }

    /// Frames are installed and removed by clicking the real Slot, which the container
    /// handles -- so those clicks have to reach it rather than stop at this panel.
    @Override
    protected boolean transparentAt(double mouseX, double mouseY) {
        for (int i = 0; i < frameSlots.size(); i++) {
            if (frameSlots.get(i).isActive() && contains(mouseX, mouseY, slotX(i), slotY(i), SLOT_SIZE, SLOT_SIZE)) {
                return true;
            }
        }

        return false;
    }

    /// Where this panel draws its slot boxes.
    ///
    /// Must agree with ApiaryMenu's FRAME_SLOT_X/Y, which are absolute because Slot
    /// coordinates are final and cannot follow a panel around. They do agree because the
    /// panel's own origin is fixed: it is always one closed tab down the right edge, the tab
    /// strip never having two panels open on a side at once.
    private int slotX(int index) {
        return contentLeft() + PADDING + (index % COLUMNS) * PITCH;
    }

    private int slotY(int index) {
        return bodyY() + (index / COLUMNS) * PITCH;
    }

    /// Kept so a future panel can label each slot with what its frame does without this
    /// class having to know the frame set.
    public static Component describe(FrameItem frame) {
        FrameType type = frame.type();
        return Component.translatable("tooltip.mellifera.frame." + type.frameName());
    }
}
