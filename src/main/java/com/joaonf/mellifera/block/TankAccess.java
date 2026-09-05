package com.joaonf.mellifera.block;

import java.util.function.Predicate;

import com.joaonf.mellifera.registry.MelliferaFluids;

import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/// What a pipe is allowed to do to a machine's tank from outside it.
///
/// WHY THIS EXISTS. A MachineTank is open at both ends, which is right for the machine that owns it
/// and wrong for everyone else. Without this a Fluid Pipe laid against a Carpenter drains the honey
/// it was about to spend, an Engine can be siphoned dry from the side while it burns, and anything
/// at all can be pushed into the Squeezer's tank -- including a fluid the Squeezer will then never
/// be able to empty, because it only ever adds honey to it.
///
/// So the capability each machine hands out is this wrapper rather than the tank itself, and each
/// machine says which way its own tank faces:
///
/// - The Squeezer's is an output. It may be drained and not filled.
/// - The Carpenter's and the Engine's are inputs. They may be filled with what the machine burns or
///   spends, and not drained.
/// - The Tank block is storage and takes this wrapper's permission for both, which is to say it
///   keeps the behaviour it always had.
///
/// The machine's own code never comes through here: MachineTank.fill and drain are direct writes on
/// the tank, for the same reason a machine spending its own energy buffer is not a transfer.
public final class TankAccess extends DelegatingResourceHandler<FluidResource> {
    private final boolean insertable;
    private final boolean extractable;
    private final Predicate<FluidResource> accepts;

    private TankAccess(ResourceHandler<FluidResource> tank, boolean insertable, boolean extractable,
                       Predicate<FluidResource> accepts) {
        super(tank);
        this.insertable = insertable;
        this.extractable = extractable;
        this.accepts = accepts;
    }

    /// The filter the two input tanks use: this mod's liquid honey and nothing else. A machine that
    /// spends honey has no way to empty a tank somebody filled with water, so it must not accept it.
    public static Predicate<FluidResource> honey() {
        return resource -> resource.getFluid().isSame(MelliferaFluids.HONEY.get());
    }

    /// A tank the outside world may fill with one fluid and never empty.
    public static TankAccess input(ResourceHandler<FluidResource> tank, Predicate<FluidResource> accepts) {
        return new TankAccess(tank, true, false, accepts);
    }

    /// A tank the outside world may empty and never fill.
    public static TankAccess output(ResourceHandler<FluidResource> tank) {
        return new TankAccess(tank, false, true, resource -> false);
    }

    /// Storage: in and out, anything.
    public static TankAccess storage(ResourceHandler<FluidResource> tank) {
        return new TankAccess(tank, true, true, resource -> true);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return insertable && accepts.test(resource) && super.isValid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (!insertable || !accepts.test(resource)) {
            return 0;
        }

        return super.insert(index, resource, amount, transaction);
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        if (!insertable || !accepts.test(resource)) {
            return 0;
        }

        return super.insert(resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return extractable ? super.extract(index, resource, amount, transaction) : 0;
    }

    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        return extractable ? super.extract(resource, amount, transaction) : 0;
    }
}
