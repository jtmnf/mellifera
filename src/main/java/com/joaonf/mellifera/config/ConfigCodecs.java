package com.joaonf.mellifera.config;

import com.mojang.serialization.Codec;

import net.minecraft.util.ExtraCodecs;

/// Codecs shared by everything a player writes by hand in `config/mellifera/`.
final class ConfigCodecs {
    /// Hex in, plain RGB out. `ExtraCodecs.STRING_RGB_COLOR` accepts both `"#3B2F5E"` and a
    /// raw integer, but hands back 0xFFRRGGBB -- and every colour in this mod is 24-bit, fed
    /// to renderers that add their own alpha (see BeeTintSource, BeeTextures). Masking here
    /// keeps a JSON-defined bee or comb's colour identical in kind to a Java-defined one's.
    static final Codec<Integer> COLOR = ExtraCodecs.STRING_RGB_COLOR.xmap(c -> c & 0xFFFFFF, c -> c);

    private ConfigCodecs() {}
}
