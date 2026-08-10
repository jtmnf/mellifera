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

    /// Tilts the bee nose-down. Applied outside the yaw so it reads as the camera looking
    /// down on it rather than the bee rolling. Negative because the -1 scale above flips the
    /// X axis: on this stack a larger angle pitches the nose up, not down.
    private static final float PITCH = -25.0F;

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

    public BeeSpecialRenderer(AdultBeeModel model, float scale, boolean stinger, boolean goldAntennae) {
        this.model = model;
        this.scale = scale;
        this.goldAntennae = goldAntennae;

        // setupAnim drives stinger visibility off the state, so the caste's stinger has to
        // be expressed there -- hiding the part directly would be undone every frame.
        this.state.hasStinger = stinger;
        this.state.isOnGround = false;
    }

    /// Applied identically in submit() and getExtents(), so what is measured for the
    /// auto-fit into the slot is the same thing that gets drawn.
    private void transform(PoseStack poseStack) {
        poseStack.translate(0.5F, 0.5F + Y_NUDGE_PIXELS / 16.0F, 0.5F);
        poseStack.scale(-this.scale, -this.scale, this.scale);
        poseStack.translate(0.0F, -BONE_HEIGHT, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(PITCH));
        poseStack.mulPose(Axis.YP.rotationDegrees(YAW));
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

    public record Unbaked(float scale, boolean stinger, boolean goldAntennae) implements SpecialModelRenderer.Unbaked<Integer> {
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
            return new BeeSpecialRenderer(
                new AdultBeeModel(context.entityModelSet().bakeLayer(ModelLayers.BEE)), scale, stinger, goldAntennae);
        }
    }
}
