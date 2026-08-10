package com.joaonf.mellifera.client;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.config.MelliferaOutputSync;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/// Where a client stops believing the server it just left.
///
/// The payload that installed those values arrives on login and there is no matching "and
/// now forget it" packet, because by the time it would be sent the connection is already
/// gone -- a disconnect is frequently the connection dying rather than the player choosing
/// to leave. So the client notices for itself: LoggingOut is the one event that fires for
/// every way a level goes away, including a timeout, a kick and the client shutting the
/// integrated server down to start a different one.
///
/// Restoring unconditionally is deliberate. This has no way of knowing whether anything was
/// ever installed -- singleplayer never receives the packet at all -- and MelliferaOutputSync
/// treats "put the local values back" as valid when they are already the ones in place.
@EventBusSubscriber(modid = Mellifera.MODID, value = Dist.CLIENT)
public final class MelliferaClientSync {
    private MelliferaClientSync() {}

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MelliferaOutputSync.restoreLocal();
    }
}
