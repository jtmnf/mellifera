package com.joaonf.mellifera.client.swarm;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

/// What HiveBeeRenderer's submit pass needs from one hive.
///
/// The swarm is handed over by reference rather than copied into the state. A render state is
/// normally a snapshot precisely so that submit cannot see the world change under it, and this
/// one bends that rule knowingly: the bees are owned by the renderer, live only on the render
/// thread, and each has already had its pose for this frame frozen into plain fields during
/// extract (see HiveSwarm.Forager.extract). Copying five poses into a parallel list every frame
/// would buy nothing but the allocation.
public class HiveBeeRenderState extends BlockEntityRenderState {
    /// Null when this hive has no bees out -- an idle hive, or one whose block entity is not the
    /// controller of its column.
    public @Nullable HiveSwarm swarm;

    /// The species-recoloured bee sheet, from BeeTextures. Per hive, not per bee: every forager
    /// out of one hive is the same species.
    public @Nullable Identifier texture;
}
