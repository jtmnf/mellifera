package com.joaonf.mellifera;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/// Where the machines advertise their Forge Energy buffer to the rest of the world.
///
/// A block entity holding an EnergyHandler is invisible until it is registered here: cables
/// and generators find power by asking the world for `Capabilities.Energy.BLOCK` at a
/// position and a face, not by looking at the block entity's fields. Without this the
/// machines would have a full buffer and nothing would ever fill it.
///
/// The face is ignored on purpose -- every side accepts power. A machine that only took
/// energy from behind would be a puzzle with no payoff, and the mod has no cabling of its
/// own to make a preferred face mean anything.
@EventBusSubscriber(modid = Mellifera.MODID)
public final class MelliferaCapabilities {
    private MelliferaCapabilities() {}

    @SubscribeEvent
    static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MelliferaBlockEntities.CENTRIFUGE.get(),
            (centrifuge, side) -> centrifuge.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MelliferaBlockEntities.ISOLATOR.get(),
            (isolator, side) -> isolator.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MelliferaBlockEntities.INFUSER.get(),
            (infuser, side) -> infuser.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MelliferaBlockEntities.SQUEEZER.get(),
            (squeezer, side) -> squeezer.energy());

        // The Squeezer's tank, which is the whole reason the machine exists: without this a pipe has no
        // way to find the honey and the fluid never leaves the block.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MelliferaBlockEntities.SQUEEZER.get(),
            (squeezer, side) -> squeezer.tank());
    }
}
