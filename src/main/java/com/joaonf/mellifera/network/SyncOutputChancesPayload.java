package com.joaonf.mellifera.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.config.MelliferaOutputSync;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// The server's `config/mellifera/` numbers, so a client's Apiarist Database and JEI pages
/// quote the odds the server is actually rolling rather than whatever is in the client's own
/// folder.
///
/// Chances and nothing else. It is tempting to send whole output tables -- item, count,
/// chance -- and that is what a "sync the config" packet usually looks like, but the only
/// thing `bees.toml` and `centrifuge.toml` can change is the chance. Which comb a species
/// makes, which items a comb spins down into, how many, and how long it takes are all
/// declared in Java, so both sides already agree on them by virtue of running the same jar.
/// Sending them would be sending the client its own tables back, and would additionally make
/// this packet the thing that decides whether an unknown item id is fatal.
///
/// The lists are positional against those Java tables, which is safe for the same reason:
/// a species' comb list and a recipe's output list are `List.of(...)` literals in source, in
/// source order. The *keys* are ids rather than indices, because those come out of registries
/// whose iteration order (`Map.of` for the recipes, registration order for species) is not
/// something to bet a drop chance on -- see SetObjectivePayload for the same argument.
///
/// @param combs species id -> chance per entry of that species' comb table
/// @param products species id -> chance per entry of its non-comb drops, absent when it has none
/// @param centrifugeCombs comb type id -> chance per output of that comb's centrifuge recipe
/// @param centrifugeItems item id -> chance per output of that item's centrifuge recipe
public record SyncOutputChancesPayload(
    Map<Identifier, List<Float>> combs,
    Map<Identifier, List<Float>> products,
    Map<Identifier, List<Float>> centrifugeCombs,
    Map<Identifier, List<Float>> centrifugeItems
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncOutputChancesPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Mellifera.MODID, "sync_output_chances"));

    /// The tables are bounded well above anything this mod produces -- 58 species and 25
    /// recipes today, at most a handful of outputs each -- and the bound is the point rather
    /// than the numbers. Both `map` and `list` default to Integer.MAX_VALUE, so an unbounded
    /// codec lets whatever is at the other end of the socket name a size and have a client
    /// allocate for it before a single entry is read. A server is trusted with a great deal,
    /// but it should not be trusted with the client's heap on the strength of one varint.
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_OUTPUTS = 64;

    /// All four tables have the same shape, so they share one codec rather than four copies
    /// of the same expression.
    private static final StreamCodec<ByteBuf, Map<Identifier, List<Float>>> TABLE = ByteBufCodecs.map(
        HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list(MAX_OUTPUTS)), MAX_ENTRIES);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncOutputChancesPayload> STREAM_CODEC = StreamCodec.composite(
        TABLE, SyncOutputChancesPayload::combs,
        TABLE, SyncOutputChancesPayload::products,
        TABLE, SyncOutputChancesPayload::centrifugeCombs,
        TABLE, SyncOutputChancesPayload::centrifugeItems,
        SyncOutputChancesPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /// Nothing is validated here beyond what MelliferaOutputSync checks when it rebuilds the
    /// tables, and deliberately so: this is the server telling a client what its own config
    /// says, which is the one direction where the sender is the authority. The reverse packet
    /// re-checks everything (see SetObjectivePayload) because there the sender is not.
    public static void handle(SyncOutputChancesPayload payload, IPayloadContext context) {
        MelliferaOutputSync.apply(payload);
    }
}
