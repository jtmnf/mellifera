package com.joaonf.mellifera.block;

import com.mojang.serialization.Codec;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

/// The one-fluid tank a Mellifera machine fills, and the sibling of MachineEnergy.
///
/// Fluid moved with the energy API in this Minecraft version and for the same reasons: it is no
/// longer `IFluidHandler` with a `FluidStack` in hand, it is a `ResourceHandler<FluidResource>` whose
/// every insert and extract happens inside a transaction that can be rolled back. A resource is the
/// *kind* of fluid and an amount is carried beside it, which is why a tank is a handler of one slot
/// rather than a mutable stack. Most guidance online still describes the old API.
///
/// A single slot on purpose. Every machine that has a tank here produces exactly one fluid, and a
/// multi-slot handler would advertise room a pipe could fill with something the machine cannot use.
public class MachineTank extends ResourceStacksResourceHandler<FluidResource> {
    /// The tank's contents on disk. FluidStack is fluid-plus-amount, which is precisely what one slot
    /// holds, so the codec is that with the empty case allowed -- an empty tank has to be storable.
    private static final Codec<ResourceStack<FluidResource>> STACK_CODEC = FluidStack.OPTIONAL_CODEC.xmap(
        stack -> new ResourceStack<>(FluidResource.of(stack), stack.getAmount()),
        stack -> stack.resource().toStack(stack.amount()));

    private final BlockEntity owner;
    private final int capacity;

    public MachineTank(BlockEntity owner, int capacity) {
        super(1, FluidResource.EMPTY, STACK_CODEC);
        this.owner = owner;
        this.capacity = capacity;
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return capacity;
    }

    /// Marks the block entity dirty whenever the level moves, so the tank survives a reload and the
    /// client's gauge is told to update.
    @Override
    protected void onContentsChanged(int index, ResourceStack<FluidResource> previousContents) {
        owner.setChanged();
    }

    public int capacity() {
        return capacity;
    }

    public FluidResource resource() {
        return getResource(0);
    }

    public int stored() {
        return getAmountAsInt(0);
    }

    public int space() {
        return capacity - stored();
    }

    /// Puts `amount` of `resource` in if it all fits, and reports whether it did.
    ///
    /// All or nothing, like MachineEnergy.consume, and a direct write for the same reason: a machine
    /// filling its own tank is not a transfer between two handlers, it is production, and there is
    /// nothing to roll back. Routing it through insert() and a transaction would also be refused the
    /// moment this tank is given an insert rule to keep pipes from pushing the wrong fluid in.
    public boolean fill(FluidResource resource, int amount) {
        if (amount <= 0) {
            return true;
        }

        if (stored() > 0 && !resource().equals(resource)) {
            return false;
        }

        if (space() < amount) {
            return false;
        }

        set(0, resource, stored() + amount);
        return true;
    }

    /// Takes `amount` out if it is all there, and reports whether it was. The machine's own side of a
    /// bucket being filled -- a direct write for the same reason fill is.
    public boolean drain(int amount) {
        if (amount <= 0) {
            return true;
        }

        if (stored() < amount) {
            return false;
        }

        set(0, resource(), stored() - amount);
        return true;
    }
}
