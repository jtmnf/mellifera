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

/// Tints one part of a bee or comb icon from whichever genetics component the stack
/// actually carries.
///
/// A bee is two-tone -- the fuzz carries the species' own colour, the abdomen bands carry
/// the colour that reads as "bee" for that climate -- so this is parameterised by which of
/// the two colours to return rather than being two near-identical classes. Entry `i` of
/// the model's `tints` list feeds `tintindex` `i`:
///
/// ```json
/// "tints": [
///   { "type": "mellifera:bee_color", "layer": "primary" },
///   { "type": "mellifera:bee_color", "layer": "secondary" }
/// ]
/// ```
///
/// The bee models are 3D (`models/item/*_bee.json`, cuboids in the shape of the vanilla
/// bee) and set `tintindex` explicitly per face; the dark bands, eyes, wings and the
/// queen's crown carry no tintindex and so stay the texture's own colour. That last part
/// is load-bearing: species like `glacial` have a near-white primary *and* a near-white
/// secondary, and the untinted bands are the only thing keeping the icon readable. Combs
/// are still flat two-layer sprites, where `item/generated` assigns layerN to tintindex N.
///
/// Princess/Drone (BEE_GENOME) and Queen (QUEEN_GENOME, her own genome) resolve to the
/// species' colours; a comb (COMB_TYPE) resolves to its CombType's, since a comb is
/// coloured by what kind of comb it is, not by which bee happened to make it.
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

        var species = MelliferaBeeSpecies.get(resolveBeeSpecies(stack));
        return opaque(layer == Layer.PRIMARY ? species.primaryColor() : species.secondaryColor());
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
