package com.joaonf.mellifera.client.special;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.QueenGenomeData;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.model.animal.bee.AdultBeeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import org.joml.Vector2f;
import org.joml.Vector3fc;

/// Draws a bee item as the actual vanilla bee: vanilla's geometry, vanilla's texture,
/// vanilla's flight animation, with the abdomen recoloured per species.
///
/// Nothing here ships any art. `ModelLayers.BEE` and `textures/entity/bee/bee.png` are
/// already on every client, so this references them rather than copying them -- which is
/// both the only legal way to use Mojang's bee and the reason the icons carry all of its
/// detail instead of an imitation of it.
///
/// The species colour arrives through the texture, not through a tint -- see BeeTextures.
/// A tint multiplies the whole submitted model, so it lands on the yellow as heavily as on
/// the brown and the bee ends up looking painted over; the body is a single 7x7x10 box, so
/// there is no part of it that could be tinted on its own. Repainting texels is what lets
/// the head and thorax stay exactly vanilla while the abdomen changes.
///
/// The wings beat because `submit` runs every frame: it poses `AdultBeeModel` through
/// vanilla's own `setupAnim` before submitting, with `isOnGround` false so the flight
/// animation is the one that plays. Phase comes off the wall clock rather than level time
/// so the icon animates in menus too, where there may be no level at all.
///
/// Caste is carried by the stinger, by gilding the antennae, and by scale, all driven from
/// the item model JSON. Only the drone is scaled down; princess and queen are the same size
/// and are told apart by the gilding, so size reads as "male/female" rather than as a rank:
///
/// ```json
/// { "type": "mellifera:bee", "scale": 1.15, "stinger": true, "gold_antennae": true }
/// ```
///
/// Gilding rather than a crown because the vanilla model has no crown, and adding one would
/// mean hand-modelled geometry sitting on Mojang's -- which is the thing this class exists
/// to avoid. The gilding is a repaint of vanilla's own antenna texels, so they keep their
/// shape and shading instead of turning into gold blocks.
public class BeeSpecialRenderer implements SpecialModelRenderer<Integer> {
    /// The model is authored in entity space: Y grows downward and the root sits 19 units
    /// up from the feet. Undoing that is what puts the bee upright and centred in the slot.
    private static final float BONE_HEIGHT = 19.0F / 16.0F;

    /// The bee faces -Z (antennae at z=-5, stinger at z=+5) and the GUI camera looks at the
    /// model's +Z side, so without a yaw the slot shows the item its own stinger.
    private static final float YAW = 200.0F;

    /// Tilts the bee nose-down. Applied outside the yaw so it reads as the camera looking down on
    /// it rather than the bee rolling. Negative because the -1 scale above flips the X axis: on
    /// this stack a larger angle pitches the nose up, not down.
    ///
    /// This is the icon's angle and it is right for an icon: in a slot the camera looks straight
    /// at the bee, so nose-down reads as looking down on it from above.
    private static final float PITCH = -25.0F;

    /// The same tilt for a bee lying on the ground, where it is seen from the side instead and the
    /// icon's angle reads as a nose-dive. Positive, so the nose comes up.
    ///
    /// A second baked renderer rather than a `rotation` in the item model's `display` block, for
    /// the reason set out in GroundVariantUnbaked: the rotations in this class turn the bee about
    /// its own root bone, and the ones in a display block turn it about the corner of the block the
    /// item is drawn inside, which swings it sideways and makes a dropped bee orbit a point instead
    /// of turning on the spot.
    private static final float GROUND_PITCH = 10.0F;

    /// Nudges the bee down in the slot so it sits centred. In pixels of the 16x16 icon, so
    /// it can be tuned a pixel at a time; negative is down.
    ///
    /// Three rather than two: a bee hovers with its abdomen low, so the model's own centre of mass
    /// sits above the middle of the frame and two pixels still left it riding high. A whole pixel
    /// rather than the half it could take, so ObjectivePanel's ghost can match it exactly -- that
    /// one is positioned in ints.
    private static final float Y_NUDGE_PIXELS = -3.0F;

    private final AdultBeeModel model;
    private final BeeRenderState state = new BeeRenderState();
    private final float scale;
    private final boolean goldAntennae;
    private final float pitch;
    private final boolean centred;

    /// Measured rather than declared, and lazily, because it is the baked model's own bounding
    /// box: how far the bee's middle sits from the axis it is drawn around. Null until asked for.
    private @Nullable Vector2f xzOffset;

    public BeeSpecialRenderer(AdultBeeModel model, float scale, boolean stinger, boolean goldAntennae,
                              float pitch, boolean centred) {
        this.model = model;
        this.scale = scale;
        this.goldAntennae = goldAntennae;
        this.pitch = pitch;
        this.centred = centred;

        // setupAnim drives stinger visibility off the state, so the caste's stinger has to
        // be expressed there -- hiding the part directly would be undone every frame.
        this.state.hasStinger = stinger;
        this.state.isOnGround = false;
    }

    /// Applied identically in submit() and getExtents(), so what is measured for the
    /// auto-fit into the slot is the same thing that gets drawn.
    private void transform(PoseStack poseStack) {
        if (this.centred) {
            Vector2f offset = xzOffset();
            poseStack.translate(-offset.x(), 0.0F, -offset.y());
        }

        place(poseStack);
    }

    /// Where the bee stands before any centring: the transform this class has always applied.
    ///
    /// Split out so xzOffset() has something to measure against. It cannot measure against
    /// transform() itself, which is the thing the measurement is for.
    private void place(PoseStack poseStack) {
        poseStack.translate(0.5F, 0.5F + Y_NUDGE_PIXELS / 16.0F, 0.5F);
        poseStack.scale(-this.scale, -this.scale, this.scale);
        poseStack.translate(0.0F, -BONE_HEIGHT, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(this.pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(YAW));
    }

    /// How far the bee's bounding box sits off the axis the item is spun around, in X and Z.
    ///
    /// The transform above lands the bee's *root bone* on that axis, which is not the same as its
    /// middle: the abdomen hangs behind the root, and the yaw then swings that overhang out to the
    /// side. In a slot nothing shows -- the icon is drawn face-on and fitted to what is there --
    /// but a dropped item spins about that axis, so the leftover offset is a radius and the bee
    /// circles a point rather than turning on the spot.
    ///
    /// Y is deliberately not corrected. ItemEntityRenderer already lifts a dropped item by its own
    /// bounding box, and the slot's framing is Y_NUDGE_PIXELS, which is tuned by eye and matched by
    /// ObjectivePanel's ghost.
    private Vector2f xzOffset() {
        Vector2f offset = this.xzOffset;
        if (offset != null) {
            return offset;
        }

        PoseStack probe = new PoseStack();
        place(probe);

        // minX, maxX, minZ, maxZ. A float[] rather than four locals because the consumer below
        // cannot assign to a local.
        float[] bounds = { Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE };
        this.model.root().getExtentsForGui(probe, corner -> {
            bounds[0] = Math.min(bounds[0], corner.x());
            bounds[1] = Math.max(bounds[1], corner.x());
            bounds[2] = Math.min(bounds[2], corner.z());
            bounds[3] = Math.max(bounds[3], corner.z());
        });

        // A model that reported nothing leaves the bee exactly where it was, which is the old
        // behaviour rather than a bee flung somewhere by two MAX_VALUEs.
        offset = bounds[0] > bounds[1]
            ? new Vector2f()
            : new Vector2f((bounds[0] + bounds[1]) / 2.0F, (bounds[2] + bounds[3]) / 2.0F);

        this.xzOffset = offset;
        return offset;
    }

    /// The clock is wrapped before it reaches a float. Util.getMillis() comes off nanoTime,
    /// so it is large enough that a float holds it to a precision of about two ticks -- the
    /// animation quantises and freezes for stretches, and two instances sampling a few
    /// milliseconds apart land on different steps, which is why some bees beat their wings
    /// and others sat still. Wrapping keeps the magnitude small enough to stay smooth; the
    /// discontinuity once every 1000 seconds is invisible on a wing beating 20 times a second.
    private void animate() {
        this.state.ageInTicks = (Util.getMillis() % 1_000_000L) / 50.0F;
        this.model.setupAnim(this.state);
    }

    @Override
    public void submit(
        @Nullable Integer color,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        int overlayCoords,
        boolean hasFoil,
        int outlineColor
    ) {
        animate();

        int species = color == null ? MelliferaBeeSpecies.get(MelliferaBeeSpecies.FOREST.getId()).primaryColor() : color;
        Identifier texture = BeeTextures.get(species, this.goldAntennae);

        poseStack.pushPose();
        transform(poseStack);
        submitNodeCollector.submitModel(
            this.model,
            this.state,
            poseStack,
            RenderTypes.entityCutout(texture),
            lightCoords,
            overlayCoords,
            -1, // no tint -- the species colour is already painted into the texture
            null,
            outlineColor,
            null
        );

        if (hasFoil) {
            // For a special model the game does not draw the glint itself -- it hands the
            // flag to the renderer and expects a second pass. That is why the branch-top
            // species (see PrincessBeeItem.isFoil) lost their shimmer when the bees moved
            // off flat sprites: the foil was being reported and then ignored. A later order
            // so it lands over the bee rather than under it.
            submitNodeCollector.order(1).submitModel(
                this.model,
                this.state,
                poseStack,
                RenderTypes.entityGlint(),
                lightCoords,
                overlayCoords,
                -1,
                null,
                0,
                null
            );
        }
        poseStack.popPose();
    }

    /// Deliberately does not animate: the extents drive the auto-fit into the slot, so
    /// measuring a moving pose would make the icon breathe in and out. It also runs at an
    /// unspecified point relative to submit(), and re-posing the model after submission
    /// would change geometry that has already been handed over to be drawn.
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        PoseStack poseStack = new PoseStack();
        transform(poseStack);
        this.model.root().getExtentsForGui(poseStack, output);
    }

    /// Resolves the stack's species down to the colour its abdomen is painted with. Drone
    /// and princess carry BEE_GENOME; a queen carries her own genome inside QUEEN_GENOME.
    @Override
    public @Nullable Integer extractArgument(ItemStack stack) {
        BeeGenome genome = stack.get(MelliferaDataComponents.BEE_GENOME.get());
        Identifier species;
        if (genome != null) {
            species = genome.species().active();
        } else {
            QueenGenomeData queen = stack.get(MelliferaDataComponents.QUEEN_GENOME.get());
            species = queen != null ? queen.own().species().active() : MelliferaBeeSpecies.FOREST.getId();
        }
        return MelliferaBeeSpecies.get(species).primaryColor();
    }

    public record Unbaked(float scale, boolean stinger, boolean goldAntennae)
        implements SpecialModelRenderer.Unbaked<Integer>, GroundVariantUnbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Unbaked::scale),
            Codec.BOOL.optionalFieldOf("stinger", true).forGetter(Unbaked::stinger),
            Codec.BOOL.optionalFieldOf("gold_antennae", false).forGetter(Unbaked::goldAntennae)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public BeeSpecialRenderer bake(SpecialModelRenderer.BakingContext context) {
            return bake(context, PITCH, false);
        }

        /// The ground bee, and the two differences are not independent: it is pitched up because it
        /// is seen from the side there, and it is centred because that is the one context that spins
        /// it. Neither is a knob the item model needs -- there is exactly one right answer for a bee
        /// lying on the floor -- so neither is a field.
        @Override
        public BeeSpecialRenderer bakeGround(SpecialModelRenderer.BakingContext context) {
            return bake(context, GROUND_PITCH, true);
        }

        private BeeSpecialRenderer bake(SpecialModelRenderer.BakingContext context, float pitch, boolean centred) {
            return new BeeSpecialRenderer(
                new AdultBeeModel(context.entityModelSet().bakeLayer(ModelLayers.BEE)),
                scale, stinger, goldAntennae, pitch, centred);
        }
    }
}
