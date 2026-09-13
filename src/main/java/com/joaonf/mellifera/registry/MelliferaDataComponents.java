package com.joaonf.mellifera.registry;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.QueenGenomeData;
import com.joaonf.mellifera.bee.SerumData;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// BEE_GENOME/QUEEN_GENOME/COMB_TYPE are the apiculture system's genetics: Princess and
// Drone stacks carry one BeeGenome (BEE_GENOME); a mated Queen carries two, her own plus
// her mate's (QUEEN_GENOME); a comb only ever carries which kind of comb it is (COMB_TYPE),
// never a full genome -- it's an inert output, not a breeding individual. COMB_TYPE names
// the comb directly rather than the bee that made it, because a species can produce more
// than one comb (Austere yields both Parched and Powdery) and two species in a branch
// produce the same one.
public final class MelliferaDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Mellifera.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BeeGenome>> BEE_GENOME =
        COMPONENTS.registerComponentType("bee_genome", builder -> builder.persistent(BeeGenome.CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<QueenGenomeData>> QUEEN_GENOME =
        COMPONENTS.registerComponentType("queen_genome", builder -> builder.persistent(QueenGenomeData.CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> COMB_TYPE =
        COMPONENTS.registerComponentType("comb_type", builder -> builder.persistent(Identifier.CODEC));

    /// Whether this bee's genome has been read, and so whether its tooltip prints anything but
    /// its species. See BeeTooltip and the Beealyzer.
    ///
    /// Absent means unanalysed, which makes every bee that already exists in a world -- and every
    /// bee any loot table, recipe or other mod produces without knowing about this -- start off
    /// unread. That is the right default: the alternative is a component every producer of a bee
    /// has to remember to leave off, and the ones that forget hand the player a free answer.
    ///
    /// Synchronised as well as persisted. The tooltip is drawn from the client's copy of the
    /// stack, so a server that kept this to itself would show every bee as unanalysed.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BEE_ANALYSED =
        COMPONENTS.registerComponentType("bee_analysed", builder -> builder
            .persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL));

    /// Set by combining a frame with an ender pearl on an anvil (see MelliferaAnvilRecipes).
    /// Such a frame never loses wear at all -- see FrameItem.neverWears.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FRAME_UNBREAKABLE =
        COMPONENTS.registerComponentType("frame_unbreakable", builder -> builder.persistent(Codec.BOOL));

    // 1.0 = fresh, 0.0 = spent and pulled from its frame slot. See ApiaryBlockEntity.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> FRAME_WEAR =
        COMPONENTS.registerComponentType("frame_wear", builder -> builder.persistent(Codec.FLOAT));

    /// One gene bottled by the Isolator: which chromosome, and what it held. See SerumData --
    /// it is deliberately trait-name + value strings rather than a typed allele, so this one
    /// component covers every chromosome and survives an allele being renamed.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SerumData>> SERUM_DATA =
        COMPONENTS.registerComponentType("serum_data", builder -> builder.persistent(SerumData.CODEC));

    /// Which of its two jobs the debug stick does when clicked on a hive: false runs a
    /// single production cycle, true runs the queen out to her death and her whole brood.
    /// On the stack rather than in a static field so two people testing on a server, or the
    /// same person with two sticks, do not share one setting -- and so it survives a reload
    /// mid-session, which is exactly when a testing tool should not forget what it was set to.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> DEBUG_FULL_PRODUCTION =
        COMPONENTS.registerComponentType("debug_full_production", builder -> builder.persistent(Codec.BOOL));

    /// What a broken Tank was holding, so putting it back down puts the fluid back too. See
    /// TankBlockEntity -- the block entity contributes it, the block's loot table copies it onto the
    /// dropped item, and TankBlockItem is what shows it on the tooltip.
    ///
    /// Synchronised as well as persisted, unlike the components above: the tooltip is drawn from the
    /// client's copy of the stack, so a component the server keeps to itself would leave a full tank
    /// in an inventory looking empty.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> TANK_CONTENTS =
        COMPONENTS.registerComponentType("tank_contents", builder -> builder
            .persistent(SimpleFluidContent.CODEC)
            .networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    private MelliferaDataComponents() {}

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
