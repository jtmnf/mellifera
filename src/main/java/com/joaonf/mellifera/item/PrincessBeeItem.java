package com.joaonf.mellifera.item;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.BeeSpecies;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaDataComponents;

import java.util.function.Consumer;

import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/// One item id for every species -- the BEE_GENOME component is what actually varies, and
/// the display name is resolved from it dynamically, the same way a written book shows a
/// title that lives in its own data rather than in the item id.
public class PrincessBeeItem extends Item {
    public PrincessBeeItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        BeeGenome genome = stack.getOrDefault(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.defaultGenome());
        BeeSpecies species = MelliferaBeeSpecies.get(genome.species().active());
        return Component.translatable("item.mellifera.princess_bee.named", Component.translatable(species.translationKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        BeeGenome genome = stack.getOrDefault(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.defaultGenome());
        BeeTooltip.appendGenome(genome, builder);
    }

    /// Forestry marks a handful of species with `setHasEffect()` -- Imperial, Industrious,
    /// Austere, Agrarian and the other branch tops -- and their items glint permanently as
    /// a "this is a prize bee" marker. It is a property of the species, not of how this
    /// particular individual was obtained.
    @Override
    public boolean isFoil(ItemStack stack) {
        BeeGenome genome = stack.getOrDefault(MelliferaDataComponents.BEE_GENOME.get(), BeeGenome.defaultGenome());
        return MelliferaBeeSpecies.get(genome.species().active()).hasEffect();
    }
}
