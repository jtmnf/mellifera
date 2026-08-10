package com.joaonf.mellifera.client.color;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeGenome;
import com.joaonf.mellifera.bee.CombType;
import com.joaonf.mellifera.bee.QueenGenomeData;
import com.joaonf.mellifera.item.HoneyCombItem;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/// Tints one layer of a comb icon from the CombType the stack carries.
///
/// A comb is two flat sprites, and `item/generated` assigns layerN to tintindex N, so entry
/// `i` of the model's `tints` list feeds layer `i` -- which is why the comb's own JSON asks
/// for `secondary` first, the cell fill being layer 0:
///
/// ```json
/// "tints": [
///   { "type": "mellifera:bee_color", "layer": "secondary" },
///   { "type": "mellifera:bee_color", "layer": "primary" }
/// ]
/// ```
///
/// The bees no longer come through here at all: they render as Vanilla's own model with a
/// repainted texture, and the repaint is the species colour (see BeeSpecialRenderer and
/// BeeTextures). A stack that is not a comb still resolves, to the species colour whichever
/// layer is asked for, because a species has exactly one colour to give.
public record BeeTintSource(Layer layer) implements ItemTintSource {
    public static final MapCodec<BeeTintSource> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Layer.CODEC.optionalFieldOf("layer", Layer.PRIMARY).forGetter(BeeTintSource::layer)
    ).apply(instance, BeeTintSource::new));

    public enum Layer implements StringRepresentable {
        PRIMARY("primary"),
        SECONDARY("secondary");

        public static final com.mojang.serialization.Codec<Layer> CODEC = StringRepresentable.fromEnum(Layer::values);

        private final String name;

        Layer(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        if (stack.getItem() instanceof HoneyCombItem) {
            CombType comb = HoneyCombItem.combType(stack);
            return opaque(layer == Layer.PRIMARY ? comb.primaryColor() : comb.secondaryColor());
        }

        // The layer is ignored: a species has one colour, and there is nothing sensible for a
        // second request to return that is not just this again.
        return opaque(MelliferaBeeSpecies.get(resolveBeeSpecies(stack)).primaryColor());
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static Identifier resolveBeeSpecies(ItemStack stack) {
        BeeGenome genome = stack.get(MelliferaDataComponents.BEE_GENOME.get());
        if (genome != null) {
            return genome.species().active();
        }

        QueenGenomeData queenData = stack.get(MelliferaDataComponents.QUEEN_GENOME.get());
        if (queenData != null) {
            return queenData.own().species().active();
        }

        return MelliferaBeeSpecies.FOREST.getId();
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
