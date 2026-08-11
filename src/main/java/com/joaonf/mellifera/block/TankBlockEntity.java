package com.joaonf.mellifera.block;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.StacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

/// Sixteen buckets of any one fluid, behind glass.
///
/// The Squeezer is a machine with a tank in it; this is the tank on its own, and the two exist for
/// different reasons. A Squeezer's four buckets are a buffer -- enough that a hopper feeding it can
/// run unattended -- and it fills them with honey and nothing else. This holds whatever a pipe or a
/// bucket puts in it, which is what makes it worth building next to somebody else's machines rather
/// than only next to ours.
///
/// It has no menu. Every interaction is a bucket in hand or a pipe on a face, so a screen would be a
/// window onto a single number that the block already shows by being transparent.
///
/// WHY THIS SYNCS ITSELF. A machine's contents reach the client through its menu's ContainerData,
/// which only exists while somebody has the screen open. A tank has to be legible from across the
/// room with nothing open at all, so the level of the fluid is sent as a block entity update
/// whenever it moves -- see sendToClients, which is what TankRenderer ultimately draws from.
public class TankBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    /// Sixteen buckets. Four times the Squeezer's own tank, which is the point of the block: it is
    /// somewhere to put the output of a machine that would otherwise stall the moment it filled its
    /// own buffer. Well under a short, because the fill level is a number the client is told rather
    /// than one it computes -- the same limit the machines' gauges live under.
    public static final int CAPACITY = 16_000;

    /// The one fluid it holds. A tank of one fluid rather than several on purpose, and the same
    /// choice MachineTank documents: a multi-slot handler advertises room a pipe will fill with
    /// something the tank is then stuck with beside the fluid the player wanted in it.
    private final MachineTank tank = new MachineTank(this, CAPACITY) {
        @Override
        protected void onContentsChanged(int index, ResourceStack<FluidResource> previousContents) {
            super.onContentsChanged(index, previousContents);
            TankBlockEntity.this.sendToClients();
        }
    };

    public TankBlockEntity(BlockPos pos, BlockState state) {
        super(MelliferaBlockEntities.TANK.get(), pos, state);
    }

    public MachineTank tank() {
        return tank;
    }

    /// What is in it, as a fluid-plus-amount. Empty rather than a zero-sized stack of something,
    /// because a FluidStack of amount 0 is a stack of nothing that still names a fluid, and callers
    /// that ask "what is in the tank" would then have to check the amount as well.
    public FluidStack contents() {
        return tank.stored() <= 0 ? FluidStack.EMPTY : tank.resource().toStack(tank.stored());
    }

    /// How full, from 0 to 1. What the renderer draws and the only thing it needs.
    public float fillFraction() {
        return (float) tank.stored() / CAPACITY;
    }

    /// Comparator strength: 0 when empty, then 1 to 15 across the rest of the range.
    ///
    /// The step off zero is deliberate and is what every vanilla container does -- a tank holding a
    /// single millibucket must not read the same as an empty one, or a comparator cannot be used to
    /// tell a machine that its output has somewhere to go.
    public int comparatorSignal() {
        if (tank.stored() <= 0) {
            return 0;
        }

        return 1 + tank.stored() * 14 / CAPACITY;
    }

    /// Pushes the new level to every client watching this block.
    ///
    /// setChanged alone is not enough: it marks the chunk to be saved and pokes the comparators, and
    /// neither of those puts a single byte on the wire. Without this the fluid would only appear
    /// after a reload.
    private void sendToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /// The state a client gets when the block comes into view, as opposed to when it changes. Both
    /// paths carry the same thing -- the tank -- and the client reads it back through loadAdditional.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            tank.serialize(output);
            return output.buildResult();
        }
    }

    // -- persistence -------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output);
    }

    // -- the fluid survives being carried ----------------------------------------------

    /// A full tank picked up and put down again is still full.
    ///
    /// The Squeezer loses the honey in it when broken, and that is the right bargain for a machine:
    /// its tank is a buffer that happens to have something in it. For a tank it is the wrong one --
    /// the block exists to hold a fluid, and a container that empties itself when you move it is not
    /// a container. This is the same mechanism a shulker box uses: the block entity contributes a
    /// component when it is broken, and the loot table copies it onto the dropped item.
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(MelliferaDataComponents.TANK_CONTENTS.get(), SimpleFluidContent.copyOf(contents()));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        FluidStack stored = components.getOrDefault(MelliferaDataComponents.TANK_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        // Clamped, so a stack whose component was hand-edited to a bigger number cannot create a
        // tank that holds more than a tank holds.
        tank.set(0, FluidResource.of(stored), Math.min(CAPACITY, stored.getAmount()));
    }

    /// Keeps the placed block from carrying both copies of its contents: the component has already
    /// been applied by the time this runs, and the raw handler data would otherwise be saved beside
    /// it and win on the next load.
    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard(StacksResourceHandler.VALUE_IO_KEY);
    }
}
