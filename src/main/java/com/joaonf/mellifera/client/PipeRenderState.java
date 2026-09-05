package com.joaonf.mellifera.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/// What PipeRenderer's submit pass needs from one pipe: what is going through it and which way the
/// run goes.
///
/// A snapshot rather than a reference to the block entity, as a render state is meant to be -- the
/// flow can start and stop on any tick, and submit must not see it change underneath.
public class PipeRenderState extends BlockEntityRenderState {
    /// The fluid's own still sprite, or null when nothing is going through and while the atlas is
    /// still being built. Null means there is nothing to draw at all.
    public @Nullable TextureAtlasSprite sprite;

    /// The fluid's tint as ARGB, or -1 for the fluids that need none -- the same rule the Tank
    /// follows: water's sprite is a grey that only becomes water once its tint is applied, honey's
    /// is already honey.
    public int tint = -1;

    /// The pipe's own light, raised to the fluid's own glow where it has one.
    public int fluidLightCoords;

    /// Which sides the sleeve reaches out along. Indexed by Direction.ordinal, as everything that
    /// walks the six sides in this mod is.
    public final boolean[] arms = new boolean[Direction.values().length];
}
