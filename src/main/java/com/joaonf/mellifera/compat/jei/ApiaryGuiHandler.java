package com.joaonf.mellifera.compat.jei;

import java.util.List;

import com.joaonf.mellifera.client.ApiaryScreen;

import mezz.jei.api.gui.handlers.IGuiContainerHandler;

import net.minecraft.client.renderer.Rect2i;

/// Tells JEI that the apiary's window is wider than AbstractContainerScreen says it is,
/// because the objective tab hangs off its right edge.
///
/// This is not cosmetic. JEI lays its ingredient list against the right side of the screen
/// and takes the mouse inside its own area, so an undeclared tab ends up *underneath* the
/// list: it is drawn over, and -- the part that actually breaks -- a bee dragged onto it
/// never reaches the ghost target, because JEI consumed the drop first. Declaring the area
/// moves the list aside and hands the space back.
public class ApiaryGuiHandler implements IGuiContainerHandler<ApiaryScreen> {
    @Override
    public List<Rect2i> getGuiExtraAreas(ApiaryScreen screen) {
        return screen.tabBounds();
    }
}
