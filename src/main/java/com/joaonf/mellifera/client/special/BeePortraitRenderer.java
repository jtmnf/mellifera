package com.joaonf.mellifera.client.special;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.animal.bee.AdultBeeModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/// Draws the vanilla bee large and sharp, for the Apiarist Database.
///
/// The obvious way to show a big bee in a GUI -- draw the item and scale the matrix up --
/// does not work. GUI items are not drawn where you see them: `GuiItemAtlas` renders each one
/// into an atlas slot of exactly `16 * guiScale` pixels and the GUI blits that slot as a
/// quad, so scaling the matrix magnifies a 16x16 render rather than rendering at 16x the
/// detail. The bee would come out as a blur of fat pixels.
///
/// Picture-in-picture is the way out. A [PictureInPictureRenderer] gets its own framebuffer
/// sized to the box it will occupy, so a 72x72 portrait really is rendered at 72x72 (times
/// the GUI scale) and every texel of the bee's 64x64 skin lands where it belongs.
///
/// It re-renders every frame, so the wings beat here without any of the cache trouble the
/// item icons needed [AnimatedSpecialItemModel] for: nothing is cached to go stale.
///
/// The texture is the same per-species recolour the icons use -- see [BeeTextures] -- so the
/// portrait and the item in the slot are the same bee, not two drawings of one.
///
/// The state's tint multiplies the whole model, which is how the objective slot draws its
/// placeholder bee. Only the colour of it is usable: this is a cutout render type, so it has
/// no blending and an alpha below the cutout threshold discards the texel rather than fading
/// it. A ghost is therefore a dark bee, not a translucent one.
public class BeePortraitRenderer extends PictureInPictureRenderer<BeePortraitRenderState> {
    /// The root sits 19 units above the feet in a model whose Y grows downward. Undoing that
    /// is what puts the bee in the middle of the frame instead of hanging below it.
    private static final float BONE_HEIGHT = 19.0F / 16.0F;

    /// The same three-quarter view the item icons use, expressed for this stack. The icons
    /// reach it as pitch -25 / yaw 200 because the item pose is mirrored twice on the way in
    /// (once by `ItemModel`'s -1 scale, once by the atlas's flipped Y); picture-in-picture
    /// mirrors Z instead, and working that difference through leaves these two angles.
    private static final float PITCH = 25.0F;
    private static final float YAW = 20.0F;

    private final AdultBeeModel model;
    private final BeeRenderState state = new BeeRenderState();

    public BeePortraitRenderer(EntityModelSet models) {
        this.model = new AdultBeeModel(models.bakeLayer(ModelLayers.BEE));
    }

    @Override
    public Class<BeePortraitRenderState> getRenderStateClass() {
        return BeePortraitRenderState.class;
    }

    /// Centre of the frame rather than the bottom of it: a bee hovers, so there is no floor
    /// for it to stand on the way the vanilla portraits assume.
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected void renderToTexture(BeePortraitRenderState portrait, PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);

        this.state.hasStinger = portrait.stinger();
        this.state.isOnGround = false; // the flight animation, not the standing one
        this.state.ageInTicks = portrait.ageInTicks();
        this.model.setupAnim(this.state);

        poseStack.translate(0.0F, -BONE_HEIGHT, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(PITCH));
        poseStack.mulPose(Axis.YP.rotationDegrees(YAW));

        collector.submitModel(
            this.model,
            this.state,
            poseStack,
            RenderTypes.entityCutout(BeeTextures.get(portrait.color(), portrait.goldAntennae())),
            15728880, // full bright: a GUI has no light level to sample
            OverlayTexture.NO_OVERLAY,
            portrait.tint(),
            null,
            0, // no outline
            null
        );
    }

    @Override
    protected String getTextureLabel() {
        return "mellifera bee portrait";
    }
}
