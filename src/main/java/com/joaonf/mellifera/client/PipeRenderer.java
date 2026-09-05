package com.joaonf.mellifera.client;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.block.PipeBlock;
import com.joaonf.mellifera.block.PipeBlockEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

/// Draws the liquid going through a Fluid Pipe.
///
/// WHY A RENDERER AND NOT A MODEL, which is the second time this mod has answered that question and
/// the same answer: a block model is baked once from a fixed set of textures, and which fluid is in
/// a pipe is known only at runtime. The first attempt did it with a model anyway -- a white sleeve
/// with a tint over it -- and it worked, in the sense that something honey-coloured appeared. But a
/// tint is a colour, and a fluid is a texture that moves. Honey in a pipe should be the same honey
/// that is in the tank at the end of it, ripples and all, and a tank of somebody else's fluid should
/// look like that mod drew it rather than like an average of it.
///
/// So the sleeve is drawn here from the fluid's own still sprite, read out of the same fluid model
/// the world uses -- exactly as TankRenderer does, and the geometry is the only part that differs.
///
/// WHAT IS DRAWN. A box through the middle of the block, and an arm out to each side the pipe
/// connects on. Faces that would end up inside the run are skipped rather than drawn and hidden:
/// the junction loses the face on any side that has an arm, and an arm has no cap at its far end at
/// all -- at a pipe joint the next pipe's sleeve carries straight on, and at a machine the collar is
/// already in front of it.
public class PipeRenderer implements BlockEntityRenderer<PipeBlockEntity, PipeRenderState> {
    /// The sleeve, in block coordinates: five pixels through the middle, which is what the six-pixel
    /// casing has room for behind its window.
    private static final float NEAR = 5.5F / 16.0F;
    private static final float FAR = 10.5F / 16.0F;

    public PipeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public PipeRenderState createRenderState() {
        return new PipeRenderState();
    }

    @Override
    public void extractRenderState(
        PipeBlockEntity blockEntity,
        PipeRenderState state,
        float partialTicks,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.sprite = null;
        state.tint = -1;
        state.fluidLightCoords = state.lightCoords;

        BlockState block = blockEntity.getBlockState();
        if (!block.getValue(PipeBlock.FLOWING) || blockEntity.carried().isEmpty()) {
            return;
        }

        FluidStack contents = blockEntity.carried().toStack(1);
        FluidModel model = fluidModel(contents);
        if (model == null) {
            return;
        }

        state.sprite = model.stillMaterial().sprite();

        FluidTintSource tint = model.fluidTintSource();
        state.tint = tint == null ? -1 : tint.colorAsStack(contents);

        for (Direction side : Direction.values()) {
            state.arms[side.ordinal()] = block.getValue(PipeBlock.propertyFor(side)) != PipeBlock.Connection.NONE;
        }

        // A pipe carrying lava lights the room, the same as the Tank holding it would.
        int emission = contents.getFluidType().getLightLevel(contents);
        if (emission > 0) {
            state.fluidLightCoords = LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, emission);
        }
    }

    /// The baked model the world draws this fluid with, or null before the atlas exists. Same guard,
    /// same reason as TankRenderer's.
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
    public void submit(PipeRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null) {
            return;
        }

        submitNodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityTranslucent(sprite.atlasLocation()),
            (pose, buffer) -> renderRun(pose, buffer, state, sprite));
    }

    private static void renderRun(PoseStack.Pose pose, VertexConsumer buffer, PipeRenderState state, TextureAtlasSprite sprite) {
        int light = state.fluidLightCoords;
        int color = state.tint;

        // The junction, minus every face an arm is about to cover.
        box(pose, buffer, sprite, light, color, NEAR, NEAR, NEAR, FAR, FAR, FAR, state.arms, true);

        for (Direction side : Direction.values()) {
            if (!state.arms[side.ordinal()]) {
                continue;
            }

            // Out to the block's own edge, so two pipes meet with no seam between their sleeves.
            float minX = side == Direction.EAST ? FAR : (side == Direction.WEST ? 0.0F : NEAR);
            float maxX = side == Direction.WEST ? NEAR : (side == Direction.EAST ? 1.0F : FAR);
            float minY = side == Direction.UP ? FAR : (side == Direction.DOWN ? 0.0F : NEAR);
            float maxY = side == Direction.DOWN ? NEAR : (side == Direction.UP ? 1.0F : FAR);
            float minZ = side == Direction.SOUTH ? FAR : (side == Direction.NORTH ? 0.0F : NEAR);
            float maxZ = side == Direction.NORTH ? NEAR : (side == Direction.SOUTH ? 1.0F : FAR);

            // An arm is open at both ends: the junction closes one and whatever is next door closes
            // the other.
            boolean[] open = new boolean[Direction.values().length];
            open[side.ordinal()] = true;
            open[side.getOpposite().ordinal()] = true;

            box(pose, buffer, sprite, light, color, minX, minY, minZ, maxX, maxY, maxZ, open, false);
        }
    }

    /// One box, skipping the faces named in `skip`.
    ///
    /// The sprite is mapped at its natural scale -- a sixteenth of the sprite to a sixteenth of a
    /// block -- rather than stretched to each face, so the liquid in a pipe has the same texel size
    /// as the same liquid in a pool, and a long run does not look like a smear.
    private static void box(
        PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light, int color,
        float minX, float minY, float minZ, float maxX, float maxY, float maxZ, boolean[] skip, boolean junction
    ) {
        for (Direction side : Direction.values()) {
            if (skip[side.ordinal()] && (junction || side.getAxis() == axisOf(minX, minY, minZ, maxX, maxY, maxZ))) {
                continue;
            }

            face(pose, buffer, sprite, light, color, side, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /// The axis a box is longest along, which for an arm is the way it points.
    private static Direction.Axis axisOf(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float x = maxX - minX;
        float y = maxY - minY;
        float z = maxZ - minZ;

        if (x >= y && x >= z) {
            return Direction.Axis.X;
        }

        return y >= z ? Direction.Axis.Y : Direction.Axis.Z;
    }

    private static void face(
        PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light, int color,
        Direction side, float minX, float minY, float minZ, float maxX, float maxY, float maxZ
    ) {
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float uSpan = sprite.getU1() - u0;
        float vSpan = sprite.getV1() - v0;

        // Each face takes the slice of the sprite its own size asks for, measured from the corner of
        // the block: a face half a block long takes half the sprite.
        float u1;
        float v1;

        switch (side.getAxis()) {
            case X -> {
                u1 = u0 + uSpan * (maxZ - minZ);
                v1 = v0 + vSpan * (maxY - minY);
            }
            case Y -> {
                u1 = u0 + uSpan * (maxX - minX);
                v1 = v0 + vSpan * (maxZ - minZ);
            }
            default -> {
                u1 = u0 + uSpan * (maxX - minX);
                v1 = v0 + vSpan * (maxY - minY);
            }
        }

        float nx = side.getStepX();
        float ny = side.getStepY();
        float nz = side.getStepZ();

        switch (side) {
            case DOWN -> {
                vertex(pose, buffer, light, color, minX, minY, maxZ, u0, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, maxX, minY, maxZ, u1, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, maxX, minY, minZ, u1, v0, nx, ny, nz);
                vertex(pose, buffer, light, color, minX, minY, minZ, u0, v0, nx, ny, nz);
            }
            case UP -> {
                vertex(pose, buffer, light, color, minX, maxY, minZ, u0, v0, nx, ny, nz);
                vertex(pose, buffer, light, color, maxX, maxY, minZ, u1, v0, nx, ny, nz);
                vertex(pose, buffer, light, color, maxX, maxY, maxZ, u1, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, minX, maxY, maxZ, u0, v1, nx, ny, nz);
            }
            case NORTH -> {
                vertex(pose, buffer, light, color, maxX, minY, minZ, u0, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, minX, minY, minZ, u1, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, minX, maxY, minZ, u1, v0, nx, ny, nz);
                vertex(pose, buffer, light, color, maxX, maxY, minZ, u0, v0, nx, ny, nz);
            }
            case SOUTH -> {
                vertex(pose, buffer, light, color, minX, minY, maxZ, u0, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, maxX, minY, maxZ, u1, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, maxX, maxY, maxZ, u1, v0, nx, ny, nz);
                vertex(pose, buffer, light, color, minX, maxY, maxZ, u0, v0, nx, ny, nz);
            }
            case WEST -> {
                vertex(pose, buffer, light, color, minX, minY, minZ, u0, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, minX, minY, maxZ, u1, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, minX, maxY, maxZ, u1, v0, nx, ny, nz);
                vertex(pose, buffer, light, color, minX, maxY, minZ, u0, v0, nx, ny, nz);
            }
            case EAST -> {
                vertex(pose, buffer, light, color, maxX, minY, maxZ, u0, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, maxX, minY, minZ, u1, v1, nx, ny, nz);
                vertex(pose, buffer, light, color, maxX, maxY, minZ, u1, v0, nx, ny, nz);
                vertex(pose, buffer, light, color, maxX, maxY, maxZ, u0, v0, nx, ny, nz);
            }
        }
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
