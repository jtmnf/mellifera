package com.joaonf.mellifera.network;

import com.joaonf.mellifera.Mellifera;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/// The mod's own packets, such as they are.
///
/// Two, and both exist reluctantly: everything else this mod syncs already had a carrier --
/// item components for a bee's genome, the block entity's update tag for what a hive is
/// doing, the menu's data slots for its progress. The objective is the one piece of state
/// that has to travel client to server, and the only Vanilla channel for that is the
/// inventory-button click, which carries an int. The configured drop chances are the one
/// piece that has to travel the other way, and they are attached to no block, item or menu
/// that could have carried them.
///
/// Versioned so a client and a server that disagree about these packets say so at login
/// rather than at the first click.
public final class MelliferaPayloads {
    private static final String VERSION = "1";

    private MelliferaPayloads() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(MelliferaPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Mellifera.MODID).versioned(VERSION);
        registrar.playToServer(SetObjectivePayload.TYPE, SetObjectivePayload.STREAM_CODEC, SetObjectivePayload::handle);
        registrar.playToClient(SyncOutputChancesPayload.TYPE, SyncOutputChancesPayload.STREAM_CODEC, SyncOutputChancesPayload::handle);
    }
}
