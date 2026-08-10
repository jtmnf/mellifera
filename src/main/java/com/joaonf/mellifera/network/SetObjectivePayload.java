package com.joaonf.mellifera.network;

import java.util.Optional;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.MutationObjective;
import com.joaonf.mellifera.block.ApiaryBlockEntity;
import com.joaonf.mellifera.menu.ApiaryMenu;
import com.joaonf.mellifera.registry.MelliferaBeeMutations;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// "Point this apiary at this species", or at nothing.
///
/// It used to be smuggled through Vanilla's inventory-button packet, with the species encoded
/// as its index in the registry plus one. That worked only because every species is declared
/// in Java and both sides therefore build the registry in the same order -- the moment a
/// species can come from a datapack, an index means one bee on the server and a different one
/// on the client. So the id travels as an id.
///
/// @param apiary which hive, checked against the menu the sender actually has open
/// @param species the target, or empty to clear the objective
public record SetObjectivePayload(BlockPos apiary, Optional<Identifier> species) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetObjectivePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Mellifera.MODID, "set_objective"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetObjectivePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetObjectivePayload::apiary,
        ByteBufCodecs.optional(Identifier.STREAM_CODEC), SetObjectivePayload::species,
        SetObjectivePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /// Everything a client sends is a claim, so all of it is re-checked here.
    ///
    /// The position is not trusted on its own -- it is only honoured when the sender has that
    /// exact apiary open, which is the same permission Vanilla's button click carried for free
    /// through the menu it was addressed to. Without that check this payload would be a way to
    /// retarget any hive in the world from anywhere in it.
    public static void handle(SetObjectivePayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player.containerMenu instanceof ApiaryMenu menu) || !menu.apiaryPos().equals(payload.apiary())) {
            return;
        }

        if (!(player.level().getBlockEntity(payload.apiary()) instanceof ApiaryBlockEntity housing)) {
            return;
        }

        if (payload.species().isEmpty()) {
            housing.setObjective(null);
            return;
        }

        Identifier species = payload.species().get();
        if (!MelliferaBeeSpecies.REGISTRY.containsKey(species)) {
            // A species this side does not have. Ignoring rather than clearing keeps a client
            // with a different mod list from wiping an objective the player set.
            return;
        }

        // Re-checked here and not only in the GUI: a species nothing breeds into would set an
        // objective that silently does nothing while still halving every off-path mutation.
        if (!MutationObjective.canTarget(species, MelliferaBeeMutations.all())) {
            return;
        }

        housing.setObjective(species);
    }
}
