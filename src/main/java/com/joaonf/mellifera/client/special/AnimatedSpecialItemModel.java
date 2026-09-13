package com.joaonf.mellifera.client.special;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.google.common.base.Suppliers;
import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;

/// `minecraft:special`, but the item is declared animated so it keeps being redrawn.
///
/// The GUI does not render items every frame. `GuiItemAtlas.getOrUpdate` draws each item
/// once into a cached atlas slot keyed on the model's identity, and only redraws a slot
/// when the render state says `isAnimated()`. Vanilla's `SpecialModelWrapper` sets that
/// flag for enchantment glint and nothing else, so a special renderer that animates -- like
/// a bee beating its wings -- is drawn once and then frozen.
///
/// It also explains why only *some* of them looked frozen: a slot is redrawn when it is
/// newly allocated or has gone stale, and the identity includes the special renderer's
/// argument, so bees whose slot had just been (re)allocated animated for that frame while
/// the rest sat in a valid cache entry. Which ones froze looked arbitrary but was not.
///
/// This is vanilla's wrapper with `setAnimated()` added. Registered as a custom item model
/// type so the item JSON can ask for it in place of `minecraft:special`.
public class AnimatedSpecialItemModel<T> implements ItemModel {
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;

    /// The renderer as it is drawn everywhere, and as it is drawn lying on the ground.
    ///
    /// Two of them because a SpecialModelRenderer is never told which display context it is
    /// drawing for, and this method is -- see GroundVariantUnbaked. `ground` is the same object as
    /// `normal` for any model that does not want the distinction, so the lookup below costs a
    /// reference comparison and nothing else.
    private final Variant<T> normal;
    private final Variant<T> ground;

    public AnimatedSpecialItemModel(
        SpecialModelRenderer<T> specialRenderer,
        @Nullable SpecialModelRenderer<T> groundRenderer,
        ModelRenderProperties properties,
        Matrix4fc transformation
    ) {
        this.properties = properties;
        this.transformation = transformation;
        this.normal = Variant.of(specialRenderer);
        this.ground = groundRenderer == null ? this.normal : Variant.of(groundRenderer);
    }

    /// A baked renderer and the extents of that renderer. They travel together because the extents
    /// are measured from the renderer's own transform, and handing the game one renderer's model
    /// with another's bounding box is how a dropped item ends up floating or sunk in the floor.
    private record Variant<T>(SpecialModelRenderer<T> renderer, Supplier<Vector3fc[]> extents) {
        static <T> Variant<T> of(SpecialModelRenderer<T> renderer) {
            return new Variant<>(renderer, Suppliers.memoize(() -> {
                Set<Vector3fc> results = new HashSet<>();
                renderer.getExtents(results::add);
                return results.toArray(new Vector3fc[0]);
            }));
        }
    }

    @Override
    public void update(
        ItemStackRenderState output,
        ItemStack item,
        ItemModelResolver resolver,
        ItemDisplayContext displayContext,
        @Nullable ClientLevel level,
        @Nullable ItemOwner owner,
        int seed
    ) {
        output.appendModelIdentityElement(this);
        // The one line vanilla's wrapper is missing for anything that moves on its own.
        output.setAnimated();

        // The GUI caches a drawn slot against this identity, so the variant has to be part of it --
        // otherwise the ground bee and the icon bee would share one cache entry.
        Variant<T> variant = displayContext == ItemDisplayContext.GROUND ? this.ground : this.normal;
        output.appendModelIdentityElement(variant.renderer());

        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        if (item.hasFoil()) {
            ItemStackRenderState.FoilType foilType = ItemStackRenderState.FoilType.STANDARD;
            layer.setFoilType(foilType);
            output.appendModelIdentityElement(foilType);
        }

        T argument = variant.renderer().extractArgument(item);
        layer.setExtents(variant.extents());
        layer.setLocalTransform(this.transformation);
        layer.setupSpecialModel(variant.renderer(), argument);
        if (argument != null) {
            output.appendModelIdentityElement(argument);
        }

        this.properties.applyToLayer(layer, displayContext);
    }

    public record Unbaked(Identifier base, Optional<Transformation> transformation, SpecialModelRenderer.Unbaked<?> specialModel)
        implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
            Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation),
            SpecialModelRenderers.CODEC.fieldOf("model").forGetter(Unbaked::specialModel)
        ).apply(instance, Unbaked::new));

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(this.base);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            Matrix4fc modelTransform = Transformation.compose(transformation, this.transformation);
            SpecialModelRenderer<?> baked = this.specialModel.bake(context);
            if (baked == null) {
                return context.missingItemModel(modelTransform);
            }

            // A model that wants a different pose on the floor says so by implementing the
            // interface; everything else gets one renderer used for every context, as before.
            SpecialModelRenderer<?> ground = this.specialModel instanceof GroundVariantUnbaked variants
                ? variants.bakeGround(context)
                : null;

            return model(baked, ground, properties(context), modelTransform);
        }

        /// Only here to give the two renderers a name for their shared type parameter.
        ///
        /// The cast is safe for the reason the signature cannot state: both renderers are baked
        /// from this same Unbaked, so whatever argument type one of them extracts from a stack is
        /// the type the other extracts too. SpecialModelRenderers.CODEC hands back
        /// `SpecialModelRenderer.Unbaked<?>` and there is no way to carry that through.
        private static <T> ItemModel model(
            SpecialModelRenderer<T> baked,
            @Nullable SpecialModelRenderer<?> ground,
            ModelRenderProperties properties,
            Matrix4fc transformation
        ) {
            @SuppressWarnings("unchecked")
            SpecialModelRenderer<T> typedGround = (SpecialModelRenderer<T>) ground;
            return new AnimatedSpecialItemModel<>(baked, typedGround, properties, transformation);
        }

        private ModelRenderProperties properties(ItemModel.BakingContext context) {
            ModelBaker baker = context.blockModelBaker();
            ResolvedModel model = baker.getModel(this.base);
            TextureSlots slots = model.getTopTextureSlots();
            return ModelRenderProperties.fromResolvedModel(baker, model, slots);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
