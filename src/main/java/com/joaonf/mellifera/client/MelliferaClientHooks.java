package com.joaonf.mellifera.client;

import net.minecraft.client.Minecraft;

/// Client-only entry points called from common code behind a `level.isClientSide()` guard.
///
/// Kept in its own class so the JVM only ever loads it on a client: a dedicated server never
/// takes that branch, so it never resolves this class and never touches Minecraft.getInstance().
public final class MelliferaClientHooks {
    private MelliferaClientHooks() {}

    public static void openBeeGuide() {
        Minecraft.getInstance().gui.setScreen(new BeeGuideScreen());
    }
}
