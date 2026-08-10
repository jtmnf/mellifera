package com.joaonf.mellifera.client.swarm;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.block.ApiaryBlockEntity;
import com.joaonf.mellifera.client.special.BeeTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.animal.bee.AdultBeeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/// Draws a working hive's foragers as the actual vanilla bee, recoloured to the species.
///
/// These bees used to be particles, which is why they were a flat sprite: the particle pipeline
/// takes camera-facing textured quads and nothing else, so no particle could ever be the same
/// bee the item icons already are (see BeeSpecialRenderer). A block entity renderer can submit a
/// model, so this is one -- the flight simulation itself did not change, it only moved (see
/// HiveSwarm).
///
/// Nothing here ships any art. `ModelLayers.BEE` and `textures/entity/bee/bee.png` are already on
/// every client; BeeTextures repaints the abdomen of the player's own copy of Mojang's sheet.
///
/// One model instance serves the whole swarm. That is safe -- and only safe -- because the
/// deferred renderer re-poses the model at draw time from whatever state was submitted with it
/// (see ModelFeatureRenderer.Submit), and every bee submits a BeeRenderState of its own. Sharing
/// one state object would draw the whole swarm in one identical pose.
public class HiveBeeRenderer implements BlockEntityRenderer<ApiaryBlockEntity, HiveBeeRenderState> {
    /// Vanilla's bee is 0.7 blocks wide, which is a bee the size of a cat and reads as an entity
    /// standing next to the apiary rather than as one of its foragers. A third of that is still
    /// unmistakably Mojang's bee -- you can see the stripes, the wings and the legs -- while
    /// staying small enough that five of them are ambience rather than a mob.
    private static final float SCALE = 0.35F;

    /// The model is authored in entity space: Y grows downward and the root sits 19 units up from
    /// the feet. Undoing that is what puts the bee's body, rather than the space under it, at the
    /// position the simulation computed.
    private static final float BONE_HEIGHT = 19.0F / 16.0F;

    /// Bees are only worth drawing close up: the whole insect is a third of a block, so beyond
    /// here it is a few pixels. The old particle used the same figure for the same reason.
    private static final int VIEW_DISTANCE = 32;

    /// How long a swarm survives without being extracted before it is dropped, and how often that
    /// is checked. A block entity renderer gets no callback when its block goes away -- broken,
    /// unloaded, or simply behind you -- so abandoned swarms have to be swept rather than freed.
    private static final int SWEEP_INTERVAL_TICKS = 200;

    private final AdultBeeModel model;

    /// One swarm per hive, keyed by position. Lives here rather than on the block entity because
    /// the block entity is common code: a dedicated server must never load a class that mentions
    /// a render state.
    private final Map<BlockPos, HiveSwarm> swarms = new HashMap<>();

    private long nextSweep;

    public HiveBeeRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new AdultBeeModel(context.bakeLayer(ModelLayers.BEE));
    }

    @Override
    public HiveBeeRenderState createRenderState() {
        return new HiveBeeRenderState();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /// A hive's bees are drawn as part of the hive, but they are not where the hive is -- a
    /// forager may be most of its territory away. Without this the whole swarm would blink out
    /// the moment the apiary itself left the frustum, which for a hive behind you and a bee in
    /// front of you is exactly the wrong way round. The view-distance cap above is what keeps
    /// that from meaning "every apiary in the world, every frame".
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void extractRenderState(
        ApiaryBlockEntity blockEntity,
        HiveBeeRenderState state,
        float partialTicks,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.swarm = null;
        state.texture = null;

        if (!(blockEntity.getLevel() instanceof ClientLevel level)) {
            return;
        }

        long gameTime = level.getGameTime();
        this.sweep(gameTime);

        BlockPos pos = blockEntity.getBlockPos();
        boolean departing = blockEntity.showsBees();
        HiveSwarm swarm = this.swarms.get(pos);
        if (swarm == null) {
            if (!departing) {
                return;
            }

            swarm = new HiveSwarm(pos, gameTime);
            this.swarms.put(pos, swarm);
        }

        swarm.advance(level, gameTime, blockEntity.territory(), departing);

        // An idle hive keeps its swarm only until the last forager is home; then the entry goes,
        // so a world full of empty apiaries costs nothing.
        if (swarm.isEmpty()) {
            if (!departing) {
                this.swarms.remove(pos);
            }
            return;
        }

        for (HiveSwarm.Forager bee : swarm.bees()) {
            bee.extract(level, pos, partialTicks, state.lightCoords);
        }

        state.swarm = swarm;
        state.texture = BeeTextures.get(blockEntity.speciesColor(), false);
    }

    @Override
    public void submit(
        HiveBeeRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera
    ) {
        if (state.swarm == null || state.texture == null) {
            return;
        }

        for (HiveSwarm.Forager bee : state.swarm.bees()) {
            poseStack.pushPose();

            // Bee positions are already relative to this block's corner, which is where the pose
            // stack stands.
            poseStack.translate(bee.renderX(), bee.renderY(), bee.renderZ());

            // The mirror every entity model is drawn under, and the offset that brings the body
            // to the origin. Both rotations come after it, so the bee turns about its own body
            // rather than about a point somewhere below it -- and both are read in the mirrored
            // frame, which is what HiveSwarm.Forager.headingOf accounts for.
            poseStack.scale(-SCALE, -SCALE, SCALE);
            poseStack.translate(0.0F, -BONE_HEIGHT, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(bee.renderYaw()));
            poseStack.mulPose(Axis.XP.rotationDegrees(bee.renderPitch()));

            // The texture overload, so the model picks its own render type: a bee's wings are
            // single-sided quads, and anything but the model's own no-cull type loses one of them
            // depending on which side you stand.
            submitNodeCollector.submitModel(
                this.model,
                bee.renderState(),
                poseStack,
                state.texture,
                bee.lightCoords(),
                OverlayTexture.NO_OVERLAY,
                0,
                null);

            poseStack.popPose();
        }
    }

    /// Drops swarms whose hive has stopped being extracted -- broken, unloaded, or out of view.
    private void sweep(long gameTime) {
        if (gameTime < this.nextSweep) {
            return;
        }

        this.nextSweep = gameTime + SWEEP_INTERVAL_TICKS;
        this.swarms.values().removeIf(swarm -> gameTime - swarm.lastSeen() > SWEEP_INTERVAL_TICKS);
    }
}
