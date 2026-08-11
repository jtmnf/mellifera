package com.joaonf.mellifera.client;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.block.TankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

/// Draws the fluid inside a Tank.
///
/// WHY A RENDERER AND NOT A MODEL. A block model is baked once from a fixed set of textures, and the
/// two things this has to draw are known only at runtime: which fluid is in the tank, and how much of
/// it. Both change while the player is looking at the block.
///
/// The fluid is its *own* sprite, read out of the same fluid model the world uses, exactly as the
/// Squeezer's gauge does -- so a tank of honey animates with honey, a tank of water ripples like
/// water, and a tank of some other mod's fluid looks like that mod meant it to. Tinting matters here
/// in a way it does not for honey: water's sprite is a grey that only becomes water once the fluid's
/// own tint is applied, which is why the tint is read rather than assumed.
///
/// The box is drawn all but flush with the block's own faces. An earlier version inset it two pixels
/// -- wider than the three-pixel frame, so head-on it looked right -- and it was wrong from every
/// other angle: a slanted line of sight passed the near wall of the fluid and left through the window
/// on the far side, which put bright gaps down the corners of a full tank. A fluid that reaches the
/// walls leaves no such gap to see through, and the frame hides the sliver that then sits behind it.
public class TankRenderer implements BlockEntityRenderer<TankBlockEntity, TankRenderState> {
    /// How far the fluid sits inside the block's faces, and the floor and ceiling it moves between.
    ///
    /// The inset is a quarter of a pixel: not zero, because a wall exactly on the block's own face
    /// z-fights with it and flickers, and not more, because every extra pixel is somewhere a slanted
    /// view can see past the fluid and out the far side of the tank.
    ///
    /// The floor is a pixel off the bottom and the ceiling a pixel off the top, both hidden behind the
    /// frame: it keeps an almost-empty tank showing a sliver of fluid instead of a surface z-fighting
    /// with the block below, and a full one from doing the same against the lid.
    private static final float INSET = 0.015625F;
    private static final float FLOOR = 0.0625F;
    private static final float CEILING = 0.9375F;

    public TankRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public TankRenderState createRenderState() {
        return new TankRenderState();
    }

    @Override
    public void extractRenderState(
        TankBlockEntity blockEntity,
        TankRenderState state,
        float partialTicks,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.sprite = null;
        state.tint = -1;
        state.fill = 0.0F;
        state.fluidLightCoords = state.lightCoords;

        FluidStack contents = blockEntity.contents();
        if (contents.isEmpty()) {
            return;
        }

        FluidModel model = fluidModel(contents);
        if (model == null) {
            return;
        }

        state.fill = Math.min(1.0F, blockEntity.fillFraction());
        state.sprite = model.stillMaterial().sprite();

        FluidTintSource tint = model.fluidTintSource();
        state.tint = tint == null ? -1 : tint.colorAsStack(contents);

        // A tank of lava lights the room, as the same lava in a hole in the floor would. The block's
        // own light is the floor of it: a glowing fluid may only ever brighten what is there.
        int emission = contents.getFluidType().getLightLevel(contents);
        if (emission > 0) {
            state.fluidLightCoords = LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, emission);
        }
    }

    /// The baked model the world draws this fluid with, or null before the atlas exists.
    ///
    /// getFluidStateModelSet throws until models have baked, which is one frame at worst -- the same
    /// guard SqueezerScreen carries for the same call.
    private static @Nullable FluidModel fluidModel(FluidStack contents) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getModelManager() == null) {
            return null;
        }

        try {
            return minecraft.getModelManager()
                .getFluidStateModelSet()
                .get(contents.getFluid().defaultFluidState());
        } catch (RuntimeException notReadyYet) {
            return null;
        }
    }

    @Override
    public void submit(TankRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null || state.fill <= 0.0F) {
            return;
        }

        float top = FLOOR + state.fill * (CEILING - FLOOR);
        int light = state.fluidLightCoords;
        int color = state.tint;

        // The fluid sits on the block atlas, so the sprite's own atlas is the texture to draw with.
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityTranslucent(sprite.atlasLocation()),
            (pose, buffer) -> renderFluid(pose, buffer, sprite, top, light, color));
    }

    /// Four walls and a surface. No floor: the block's bottom face is opaque, so nothing could ever
    /// see it.
    ///
    /// The sprite is mapped at its natural scale rather than stretched to fit -- a sixteenth of the
    /// sprite to a sixteenth of a block -- so the fluid has the same texel size here as it does in a
    /// pool of it outside. The walls keep the *bottom* of the sprite at the tank floor and cut it off
    /// at the surface, which is what makes a rising level look like more fluid rather than like the
    /// same fluid stretched.
    private static void renderFluid(
        PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float top, int light, int color
    ) {
        float near = INSET;
        float far = 1.0F - INSET;
        float span = far - near;

        float u0 = sprite.getU0();
        float u1 = u0 + (sprite.getU1() - u0) * span;
        float wallV1 = sprite.getV1();
        float wallV0 = wallV1 - (wallV1 - sprite.getV0()) * (top - FLOOR);
        float topV0 = sprite.getV0();
        float topV1 = topV0 + (sprite.getV1() - topV0) * span;

        // West (-X)
        vertex(pose, buffer, light, color, near, FLOOR, near, u0, wallV1, -1.0F, 0.0F, 0.0F);
        vertex(pose, buffer, light, color, near, FLOOR, far, u1, wallV1, -1.0F, 0.0F, 0.0F);
        vertex(pose, buffer, light, color, near, top, far, u1, wallV0, -1.0F, 0.0F, 0.0F);
        vertex(pose, buffer, light, color, near, top, near, u0, wallV0, -1.0F, 0.0F, 0.0F);

        // East (+X)
        vertex(pose, buffer, light, color, far, FLOOR, far, u0, wallV1, 1.0F, 0.0F, 0.0F);
        vertex(pose, buffer, light, color, far, FLOOR, near, u1, wallV1, 1.0F, 0.0F, 0.0F);
        vertex(pose, buffer, light, color, far, top, near, u1, wallV0, 1.0F, 0.0F, 0.0F);
        vertex(pose, buffer, light, color, far, top, far, u0, wallV0, 1.0F, 0.0F, 0.0F);

        // North (-Z)
        vertex(pose, buffer, light, color, far, FLOOR, near, u0, wallV1, 0.0F, 0.0F, -1.0F);
        vertex(pose, buffer, light, color, near, FLOOR, near, u1, wallV1, 0.0F, 0.0F, -1.0F);
        vertex(pose, buffer, light, color, near, top, near, u1, wallV0, 0.0F, 0.0F, -1.0F);
        vertex(pose, buffer, light, color, far, top, near, u0, wallV0, 0.0F, 0.0F, -1.0F);

        // South (+Z)
        vertex(pose, buffer, light, color, near, FLOOR, far, u0, wallV1, 0.0F, 0.0F, 1.0F);
        vertex(pose, buffer, light, color, far, FLOOR, far, u1, wallV1, 0.0F, 0.0F, 1.0F);
        vertex(pose, buffer, light, color, far, top, far, u1, wallV0, 0.0F, 0.0F, 1.0F);
        vertex(pose, buffer, light, color, near, top, far, u0, wallV0, 0.0F, 0.0F, 1.0F);

        // The surface (+Y)
        vertex(pose, buffer, light, color, near, top, far, u0, topV1, 0.0F, 1.0F, 0.0F);
        vertex(pose, buffer, light, color, far, top, far, u1, topV1, 0.0F, 1.0F, 0.0F);
        vertex(pose, buffer, light, color, far, top, near, u1, topV0, 0.0F, 1.0F, 0.0F);
        vertex(pose, buffer, light, color, near, top, near, u0, topV0, 0.0F, 1.0F, 0.0F);
    }

    private static void vertex(
        PoseStack.Pose pose, VertexConsumer buffer, int light, int color,
        float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ
    ) {
        buffer.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, normalX, normalY, normalZ);
    }
}
