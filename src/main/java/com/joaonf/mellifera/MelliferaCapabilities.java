package com.joaonf.mellifera;

import com.joaonf.mellifera.block.TankAccess;
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

        event.registerBlockEntity(Capabilities.Energy.BLOCK, MelliferaBlockEntities.CARPENTER.get(),
            (carpenter, side) -> carpenter.energy());

        // The pipe, on all six faces. Like the cable it stores nothing: what is inserted is handed
        // on to the tanks behind it, in the caller's own transaction.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MelliferaBlockEntities.PIPE.get(),
            (pipe, side) -> pipe.conduit());

        // The capacitor, which is the only block here open at both ends: a cable may fill it and a
        // cable may empty it. See MachineBattery for the one thing it will not do.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MelliferaBlockEntities.CAPACITOR.get(),
            (capacitor, side) -> capacitor.energy());

        // The cable, on all six faces. It stores nothing -- what is inserted here is handed
        // straight on to the machines behind it, in the caller's own transaction.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MelliferaBlockEntities.CABLE.get(),
            (cable, side) -> cable.conductor());

        // The Engine's buffer, which is the one in the mod that pays out rather than takes in: its
        // handler refuses insertion and allows extraction, so a cable pulls from it and nothing can
        // charge it. See MachineGenerator.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MelliferaBlockEntities.ENGINE.get(),
            (engine, side) -> engine.energy());

        // And its tank, an input like the Carpenter's: this is where a pipe from a Tank puts the
        // honey the engine burns.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MelliferaBlockEntities.ENGINE.get(),
            (engine, side) -> TankAccess.input(engine.tank(), TankAccess.honey()));

        // The Carpenter's tank, an input: a pipe fills it with the honey the machine spends, and
        // cannot take that honey back out again. See TankAccess for why the wrapper is there at all.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MelliferaBlockEntities.CARPENTER.get(),
            (carpenter, side) -> TankAccess.input(carpenter.tank(), TankAccess.honey()));

        // The Squeezer's tank, which is the whole reason the machine exists: without this a pipe has no
        // way to find the honey and the fluid never leaves the block.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MelliferaBlockEntities.SQUEEZER.get(),
            (squeezer, side) -> TankAccess.output(squeezer.tank()));

        // The Tank, on every face and in both directions -- a pipe fills it from one side and drains
        // it from another, and which face does which is the plumbing's business, not the block's.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MelliferaBlockEntities.TANK.get(),
            (tank, side) -> TankAccess.storage(tank.tank()));
    }
}
