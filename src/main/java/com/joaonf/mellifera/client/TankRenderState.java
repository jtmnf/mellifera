package com.joaonf.mellifera.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/// What TankRenderer's submit pass needs from one tank: how full it is, and what the fluid in it
/// looks like.
///
/// A snapshot rather than a reference to the block entity, as a render state is meant to be -- the
/// tank can be filled by a pipe on any tick, and submit must not see the level change under it.
public class TankRenderState extends BlockEntityRenderState {
    /// 0 to 1. Zero means there is nothing to draw at all.
    public float fill;

    /// The fluid's own still sprite, or null while the atlas is still being built and for an empty
    /// tank.
    public @Nullable TextureAtlasSprite sprite;

    /// The fluid's tint as ARGB, or -1 for the fluids that need none. Water's sprite is a grey that
    /// only becomes water once this is applied; honey's is already honey-coloured and takes -1.
    public int tint = -1;

    /// The tank's own light, raised to the fluid's own glow where it has one -- a tank of lava lights
    /// itself.
    public int fluidLightCoords;
}
