package com.joaonf.mellifera.client.special;

import net.minecraft.client.renderer.special.SpecialModelRenderer;

/// A special model that wants to be drawn differently when the item is lying on the ground.
///
/// WHY IT EXISTS. A SpecialModelRenderer is never told which display context it is drawing for:
/// `submit` gets a pose and nothing else. Everywhere else that difference is expressed in the
/// item model's `display` block -- but a `rotation` there is applied out in item space, where
/// the pivot is the corner of the block the item is drawn inside rather than the thing being
/// drawn, so it swings the model sideways instead of turning it on the spot. On the ground that
/// shows: ItemEntityRenderer corrects a dropped item's height and not its X or Z, then spins it
/// about the item-space Y axis, so any sideways offset becomes an orbit.
///
/// The fix is to bake a second renderer that already has the difference built into its own
/// transform, and pick between the two where the context *is* known -- which is
/// AnimatedSpecialItemModel.update. This interface is how an unbaked model offers that second
/// renderer without AnimatedSpecialItemModel having to know what kind of model it is wrapping.
public interface GroundVariantUnbaked {
    /// The renderer to use for ItemDisplayContext.GROUND. Baked once, alongside the normal one.
    SpecialModelRenderer<?> bakeGround(SpecialModelRenderer.BakingContext context);
}
