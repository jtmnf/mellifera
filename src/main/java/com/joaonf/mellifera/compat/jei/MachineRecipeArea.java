package com.joaonf.mellifera.compat.jei;

import java.util.Collection;
import java.util.List;

import com.joaonf.mellifera.client.MachineGeometry;

import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.types.IRecipeType;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/// Makes the drive track in the middle of a machine window open that machine's recipes.
///
/// It is the convention every tech mod follows -- click the arrow, get the page -- and the
/// alternative in this mod was worse than usual: several of these machines have recipes that
/// exist nowhere a player can stumble on them. The Carpenter's frames stopped being bench
/// crafts when the machine took them over, repair is a branch inside a block entity rather
/// than a recipe at all, and the Isolator and Infuser work off a genome and have no recipe to
/// discover. A player standing in front of the machine had no way in but knowing to press U
/// somewhere else first.
///
/// One handler for all six machines, built from the track rectangle the window is painted
/// with, so a machine whose track moves in tools/gen_machine_guis.py takes its clickable area
/// with it. Nothing is added to the screens themselves: JEI is the only thing that needs to
/// know this rectangle means something, and the mod has to run without JEI.
public class MachineRecipeArea<T extends AbstractContainerScreen<?>> implements IGuiContainerHandler<T> {
    /// The painted housing is a pixel wider than the track on each side and two rows taller, and
    /// even that is a five-pixel-high thing to hit with a mouse. Padded to roughly the height of
    /// a slot, which every machine has room for: no slot in any of the six windows comes within
    /// the track's column, so there is nothing above or below one for this to steal a click from.
    private static final int PAD_X = 1;
    private static final int PAD_Y = 5;

    private final List<IGuiClickableArea> areas;

    private MachineRecipeArea(MachineGeometry.Rect track, IRecipeType<?>... recipeTypes) {
        this.areas = List.of(IGuiClickableArea.createBasic(
            track.x() - PAD_X,
            track.y() - PAD_Y,
            track.width() + PAD_X * 2,
            track.height() + PAD_Y * 2,
            recipeTypes));
    }

    public static <T extends AbstractContainerScreen<?>> MachineRecipeArea<T> of(
        MachineGeometry.Rect track, IRecipeType<?>... recipeTypes
    ) {
        return new MachineRecipeArea<>(track, recipeTypes);
    }

    /// The whole list every time. JEI checks the mouse against each area's own rectangle; the
    /// filtering this method offers is for windows whose clickable areas move, and these do not.
    @Override
    public Collection<IGuiClickableArea> getGuiClickableAreas(T screen, double guiMouseX, double guiMouseY) {
        return this.areas;
    }
}
