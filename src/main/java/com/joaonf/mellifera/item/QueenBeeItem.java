package com.joaonf.mellifera.item;

import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.bee.QueenGenomeData;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaDataComponents;

import java.util.function.Consumer;

import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/// A Queen only ever exists inside an Apiary (see ApiaryBlockEntity's mating step) -- this
/// item id is what occupies slot 0 once a Princess has mated, named after her own species
/// (QUEEN_GENOME.own()), not the drone she mated with.
public class QueenBeeItem extends Item {
    public QueenBeeItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        QueenGenomeData data = stack.getOrDefault(MelliferaDataComponents.QUEEN_GENOME.get(), QueenGenomeData.defaultGenome());
        BeeSpecies species = MelliferaBeeSpecies.get(data.own().species().active());
        return Component.translatable("item.mellifera.queen_bee.named", Component.translatable(species.translationKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        QueenGenomeData data = stack.getOrDefault(MelliferaDataComponents.QUEEN_GENOME.get(), QueenGenomeData.defaultGenome());
        BeeTooltip.appendGenome(data.own(), builder);
        BeeTooltip.appendMate(data.mate(), builder);
    }

    /// See PrincessBeeItem.isFoil -- a queen glints for her own species, not her mate's.
    @Override
    public boolean isFoil(ItemStack stack) {
        QueenGenomeData data = stack.getOrDefault(MelliferaDataComponents.QUEEN_GENOME.get(), QueenGenomeData.defaultGenome());
        return MelliferaBeeSpecies.get(data.own().species().active()).hasEffect();
    }
}
