package com.joaonf.mellifera.client.special;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/// What a big bee portrait needs to draw itself, handed to [BeePortraitRenderer].
///
/// A picture-in-picture state is data only: the GUI collects these during the extract pass
/// and the renderer turns them into pixels later, off its own framebuffer. That indirection
/// is the whole reason the portrait can be large and still sharp -- see BeePortraitRenderer.
///
/// @param color ARGB species colour for the abdomen
/// @param goldAntennae gild the antennae, the queen's marking
/// @param stinger whether the caste has one
/// @param tint ARGB multiplied over the whole model, [#NO_TINT] to draw it as it is
/// @param ageInTicks animation phase, sampled by the caller so it advances every frame
/// @param scale model units to GUI pixels -- roughly the height in pixels of one block
public record BeePortraitRenderState(
    int color,
    boolean goldAntennae,
    boolean stinger,
    int tint,
    float ageInTicks,
    int x0,
    int y0,
    int x1,
    int y1,
    float scale,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
    /// White, so the multiply leaves every texel exactly as [BeeTextures] painted it.
    public static final int NO_TINT = -1;

    public BeePortraitRenderState(
        int color,
        boolean goldAntennae,
        boolean stinger,
        float ageInTicks,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        @Nullable ScreenRectangle scissorArea
    ) {
        this(color, goldAntennae, stinger, NO_TINT, ageInTicks, x0, y0, x1, y1, scale, scissorArea);
    }

    public BeePortraitRenderState(
        int color,
        boolean goldAntennae,
        boolean stinger,
        int tint,
        float ageInTicks,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        @Nullable ScreenRectangle scissorArea
    ) {
        this(color, goldAntennae, stinger, tint, ageInTicks, x0, y0, x1, y1, scale, scissorArea,
            PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}
