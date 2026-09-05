package com.joaonf.mellifera.client;

import java.util.List;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.client.color.ApiaryItemTintSource;
import com.joaonf.mellifera.client.color.ApiaryTintSource;
import com.joaonf.mellifera.client.color.BeeTintSource;
import com.joaonf.mellifera.client.color.SerumTintSource;
import com.joaonf.mellifera.client.special.AnimatedSpecialItemModel;
import com.joaonf.mellifera.client.special.BeePortraitRenderState;
import com.joaonf.mellifera.client.special.BeePortraitRenderer;
import com.joaonf.mellifera.client.special.BeeSpecialRenderer;
import com.joaonf.mellifera.client.swarm.HiveBeeRenderer;
import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaBlocks;
import com.joaonf.mellifera.registry.MelliferaMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import com.joaonf.mellifera.registry.MelliferaFluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@EventBusSubscriber(modid = Mellifera.MODID, value = Dist.CLIENT)
public class MelliferaClient {
    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(MelliferaMenus.APIARY.get(), ApiaryScreen::new);
        event.register(MelliferaMenus.CENTRIFUGE.get(), CentrifugeScreen::new);
        event.register(MelliferaMenus.SQUEEZER.get(), SqueezerScreen::new);
        event.register(MelliferaMenus.ISOLATOR.get(), IsolatorScreen::new);
        event.register(MelliferaMenus.INFUSER.get(), InfuserScreen::new);
        event.register(MelliferaMenus.CARPENTER.get(), CarpenterScreen::new);
        event.register(MelliferaMenus.ENGINE.get(), EngineScreen::new);
    }

    // The bees flying around a working apiary. A block entity renderer rather than the particle
    // this used to be, because a particle can only ever be a textured quad and these are the
    // vanilla bee model -- see HiveBeeRenderer.
    // The fluid behind a Tank's glass. Same reason as the bees: what has to be drawn is decided at
    // runtime -- which fluid, and how much of it -- and a baked model is decided at load. See
    // TankRenderer.
    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MelliferaBlockEntities.APIARY.get(), HiveBeeRenderer::new);
        event.registerBlockEntityRenderer(MelliferaBlockEntities.TANK.get(), TankRenderer::new);
        event.registerBlockEntityRenderer(MelliferaBlockEntities.PIPE.get(), PipeRenderer::new);
    }

    // Lets the comb and serum item JSONs reference "mellifera:bee_color" in their "tints"
    // array, once per tintable layer -- see BeeTintSource. The bees themselves no longer
    // go through this: they are rendered as the vanilla bee model, which resolves its own
    // colour -- see BeeSpecialRenderer.
    @SubscribeEvent
    static void onRegisterItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath(Mellifera.MODID, "bee_color"), BeeTintSource.MAP_CODEC);
        event.register(Identifier.fromNamespaceAndPath(Mellifera.MODID, "serum_color"), SerumTintSource.MAP_CODEC);
        event.register(Identifier.fromNamespaceAndPath(Mellifera.MODID, "apiary_color"), ApiaryItemTintSource.MAP_CODEC);
    }

    // The Apiary's paint. One entry, because the models ask for tintindex 0 and nothing else:
    // the boards take it, and the second cube -- brass, doorway, bees -- deliberately does not.
    // The block textures are stored neutral, so this runs even on an undyed hive, which is what
    // gives it its wood colour at all -- see ApiaryBlock.Tint.UNDYED_TAN.
    @SubscribeEvent
    static void onRegisterBlockTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(new ApiaryTintSource()), MelliferaBlocks.APIARY.get());
    }

    // Lets drone/princess/queen item JSONs ask for "mellifera:bee" as their special model.
    @SubscribeEvent
    static void onRegisterSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(Identifier.fromNamespaceAndPath(Mellifera.MODID, "bee"), BeeSpecialRenderer.Unbaked.MAP_CODEC);
    }

    // "minecraft:special" renders an item once into the GUI's cached atlas and never again,
    // so a model that animates itself has to go through a wrapper that marks the item
    // animated -- see AnimatedSpecialItemModel.
    @SubscribeEvent
    static void onRegisterItemModels(RegisterItemModelsEvent event) {
        event.register(Identifier.fromNamespaceAndPath(Mellifera.MODID, "animated_special"),
            AnimatedSpecialItemModel.Unbaked.MAP_CODEC);
    }

    // The big bee in the Apiarist Database. Item rendering cannot do it -- the GUI item atlas
    // fixes every icon at 16x16 -- so the portrait gets a framebuffer of its own. See
    // BeePortraitRenderer.
    /// Liquid honey's look, which in this version is a baked model rather than the texture pair the
    /// old IClientFluidTypeExtensions carried -- that interface no longer has getStillTexture at all.
    ///
    /// Honey's own sprites, and no tint at all.
    ///
    /// The first attempt borrowed water's sprites and tinted them amber, which came out brown for a
    /// reason that is arithmetic rather than taste: water's texture is a dark blue-grey, and a blue-grey
    /// multiplied by amber is mud. No tint can fix that -- the blue has to go. So the two sheets are
    /// drawn from a honey ramp with no blue in it, and the model carries no tint, which also means the
    /// bucket and the GUI tank get the same colour for free: both read the still sprite.
    @SubscribeEvent
    static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        Material still = new Material(Identifier.fromNamespaceAndPath(Mellifera.MODID, "block/honey_still"));
        Material flowing = new Material(Identifier.fromNamespaceAndPath(Mellifera.MODID, "block/honey_flow"));

        event.register(
            new FluidModel.Unbaked(still, flowing, null, null),
            MelliferaFluids.HONEY, MelliferaFluids.HONEY_FLOWING);
    }

    @SubscribeEvent
    static void onRegisterPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(BeePortraitRenderState.class,
            () -> new BeePortraitRenderer(Minecraft.getInstance().getEntityModels()));
    }
}
