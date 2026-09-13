package com.joaonf.mellifera.item;

import java.util.function.Consumer;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.Chromosome;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/// Renders a bee's genome onto its tooltip, the way Forestry's own bee items show their
/// traits rather than making you guess what you are holding.
///
/// Every chromosome prints its *active* allele, and its recessive one in brackets when the
/// two differ -- which is the whole point of a diploid system: a bee that looks Forest can
/// still be carrying Meadows, and that hidden allele is what a mutation fires off. Without
/// this, breeding is invisible bookkeeping.
public final class BeeTooltip {
    private BeeTooltip() {}

    /// The whole genome, for a bee that has been read.
    ///
    /// `analysed` false prints the expressed species and one line saying the rest is unknown. Not
    /// the recessive half of the species chromosome either: that is the single most valuable thing
    /// on the tooltip, and a bee whose hidden species was legible would make the Beealyzer a
    /// formality. See BeeStacks.isAnalysed.
    public static void appendGenome(BeeGenome genome, boolean analysed, Consumer<Component> lines) {
        if (!analysed) {
            lines.accept(label("species", Component.translatable(
                MelliferaBeeSpecies.get(genome.species().active()).translationKey())
                .copy().withStyle(ChatFormatting.WHITE)));
            lines.accept(Component.translatable("tooltip.mellifera.bee.unanalysed")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        lines.accept(species(genome.species()));
        lines.accept(trait("speed", genome.speed()));
        lines.accept(trait("lifespan", genome.lifespan()));
        lines.accept(trait("fertility", genome.fertility()));
        lines.accept(trait("territory", genome.territory()));
        lines.accept(trait("tolerance", genome.tolerance()));
        lines.accept(trait("effect", genome.effect()));
        lines.accept(trait("flowering", genome.flowering()));
        lines.accept(trait("nocturnal", genome.nocturnal()));
        lines.accept(trait("tolerant_flyer", genome.tolerantFlyer()));
        lines.accept(trait("cave_dwelling", genome.caveDwelling()));
    }

    /// The drone a queen mated with -- only its species, since that is what decides which
    /// mutations are even on the table for her offspring.
    public static void appendMate(BeeGenome mate, Consumer<Component> lines) {
        Component name = speciesName(mate.species().active());
        Component inactive = speciesName(mate.species().inactive());

        Component value = mate.species().active().equals(mate.species().inactive())
            ? name
            : Component.literal("").append(name).append(Component.literal(" (").append(inactive).append(")")
                .withStyle(ChatFormatting.DARK_GRAY));

        lines.accept(Component.translatable("tooltip.mellifera.bee.mate", value).withStyle(ChatFormatting.GRAY));
    }

    private static Component species(Chromosome<Identifier> chromosome) {
        Component active = speciesName(chromosome.active());
        if (chromosome.active().equals(chromosome.inactive())) {
            return label("species", active.copy().withStyle(ChatFormatting.WHITE));
        }

        Component both = active.copy().withStyle(ChatFormatting.WHITE)
            .append(Component.literal(" (").append(speciesName(chromosome.inactive())).append(")")
                .withStyle(ChatFormatting.DARK_GRAY));

        return label("species", both);
    }

    private static <T extends StringRepresentable> Component trait(String trait, Chromosome<T> chromosome) {
        Component active = alleleName(trait, chromosome.active());
        if (chromosome.active() == chromosome.inactive()) {
            return label(trait, active.copy().withStyle(ChatFormatting.WHITE));
        }

        Component both = active.copy().withStyle(ChatFormatting.WHITE)
            .append(Component.literal(" (").append(alleleName(trait, chromosome.inactive())).append(")")
                .withStyle(ChatFormatting.DARK_GRAY));

        return label(trait, both);
    }

    private static Component label(String trait, Component value) {
        return Component.translatable("tooltip.mellifera.bee." + trait, value).withStyle(ChatFormatting.GRAY);
    }

    private static Component speciesName(Identifier id) {
        return Component.translatable(MelliferaBeeSpecies.get(id).translationKey());
    }

    private static Component alleleName(String trait, StringRepresentable allele) {
        return Component.translatable("allele.mellifera." + trait + "." + allele.getSerializedName());
    }
}
