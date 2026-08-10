package com.joaonf.mellifera.client;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.registry.MelliferaItems;
import com.joaonf.mellifera.temperature.EnvironmentTemperature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Readout for the ambient temperature the player is standing in, plus the breakdown that
// produced it. Nothing here models the player yet -- this is the tuning instrument for
// EnvironmentTemperature, and the shape the body temperature HUD will grow out of.
//
// Only drawn while a Climate Chart is in hand. A permanent corner readout is the wrong default:
// it is the mod editorialising over the player's screen for information they need in one place
// (siting an apiary) and never anywhere else. Holding an item for it also makes the reading a
// thing you *acquire*, which is worth a recipe.
@EventBusSubscriber(modid = Mellifera.MODID, value = Dist.CLIENT)
public final class MelliferaTemperatureHud {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Mellifera.MODID, "temperature");

    // Endpoints of the colour ramp, in Celsius. Values outside the range clamp to the ends.
    private static final float COLD_CELSIUS = 0.0F;
    private static final float MILD_CELSIUS = 20.0F;
    private static final float HOT_CELSIUS = 40.0F;

    private static final int COLD_COLOR = 0xFF6ECBFF;
    private static final int MILD_COLOR = 0xFFF4F4F4;
    private static final int HOT_COLOR = 0xFFFF7A3D;
    private static final int DETAIL_COLOR = 0xFFA0A0A0;

    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;

    // Sky exposure jumps a long way in a single step at a cave mouth, and walking past a
    // campfire moves the heat term just as sharply. Both are real, but reading a number that
    // snaps is unpleasant, so the headline value chases the sample instead of tracking it.
    // The breakdown below it stays raw -- that is the one you tune against.
    private static final float SMOOTHING = 0.2F;

    private static EnvironmentTemperature.@Nullable Sample sample;
    private static float displayedCelsius;

    private MelliferaTemperatureHud() {}

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EFFECTS, LAYER_ID, (guiGraphics, deltaTracker) -> render(guiGraphics));
    }

    // Sampling scans a box of blocks around the player, so it belongs on the tick, not in the
    // render layer, which runs at whatever the frame rate happens to be.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;

        if (level == null || player == null || !holdingChart(player)) {
            // Dropped rather than kept, so putting the chart away and taking it out again reads
            // the place you are now instead of sliding down from wherever you last held it.
            sample = null;
            return;
        }

        if (minecraft.isPaused()) {
            return;
        }

        BlockPos pos = player.blockPosition();
        EnvironmentTemperature.Sample current = EnvironmentTemperature.sample(level, pos);

        // Snap on the first sample after joining or changing dimension, so the readout does not
        // spend a second sliding in from a stale value.
        displayedCelsius = sample == null ? current.celsius() : Mth.lerp(SMOOTHING, displayedCelsius, current.celsius());
        sample = current;
    }

    private static void render(GuiGraphicsExtractor guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        EnvironmentTemperature.Sample current = sample;

        // Vanilla passes a shouldRender gate per layer, but layers registered through
        // RegisterGuiLayersEvent get none, so hiding the HUD (F1) is on us. F3 is skipped too
        // because the debug overlay draws over this same corner.
        // `current` being null already covers the chart being put away -- onClientTick clears it
        // -- but the hand is checked here too, so the readout vanishes on the frame the item
        // leaves the hand rather than on the next tick.
        if (level == null
            || player == null
            || current == null
            || !holdingChart(player)
            || minecraft.gui.hud.isHidden()
            || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        Font font = minecraft.font;
        int y = MARGIN;

        guiGraphics.text(
            font,
            Component.translatable("hud.mellifera.temperature", format(displayedCelsius, 1)),
            MARGIN,
            y,
            colorFor(displayedCelsius));
        y += LINE_HEIGHT;

        guiGraphics.text(
            font,
            Component.translatable(
                "hud.mellifera.temperature.place",
                level.getBiome(player.blockPosition()).getRegisteredName(),
                format(current.biomeCelsius(), 1),
                Math.round(current.skyExposure() * 100.0F)),
            MARGIN,
            y,
            DETAIL_COLOR);
        y += LINE_HEIGHT;

        guiGraphics.text(
            font,
            Component.translatable(
                "hud.mellifera.temperature.breakdown",
                signed(current.sun()),
                signed(current.altitude()),
                signed(current.weather()),
                signed(current.geothermal()),
                signed(current.heatSources())),
            MARGIN,
            y,
            DETAIL_COLOR);
    }

    // Either hand: an offhand chart is the useful way to carry it, since it leaves the main hand
    // free to place the hives you are reading the climate for.
    private static boolean holdingChart(Player player) {
        return player.getMainHandItem().is(MelliferaItems.CLIMATE_CHART.get())
            || player.getOffhandItem().is(MelliferaItems.CLIMATE_CHART.get());
    }

    // Two ramps meeting at mild, so the colour tracks the number instead of only reacting at
    // the extremes.
    private static int colorFor(float celsius) {
        if (celsius < MILD_CELSIUS) {
            return lerpColor(progress(celsius, COLD_CELSIUS, MILD_CELSIUS), COLD_COLOR, MILD_COLOR);
        }

        return lerpColor(progress(celsius, MILD_CELSIUS, HOT_CELSIUS), MILD_COLOR, HOT_COLOR);
    }

    private static float progress(float value, float min, float max) {
        return Mth.clamp(Mth.inverseLerp(value, min, max), 0.0F, 1.0F);
    }

    private static int lerpColor(float progress, int from, int to) {
        return ARGB.color(
            Mth.lerpInt(progress, ARGB.red(from), ARGB.red(to)),
            Mth.lerpInt(progress, ARGB.green(from), ARGB.green(to)),
            Mth.lerpInt(progress, ARGB.blue(from), ARGB.blue(to)));
    }

    // Locale.ROOT so the decimal separator does not follow the system language: these numbers
    // are read against the constants in EnvironmentTemperature, not prose.
    private static String format(float value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String signed(float value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }
}
