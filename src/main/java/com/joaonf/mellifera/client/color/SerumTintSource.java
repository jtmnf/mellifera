package com.joaonf.mellifera.client.color;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.bee.BeeTrait;
import com.joaonf.mellifera.bee.SerumData;
import com.joaonf.mellifera.item.SerumItem;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/// Colours the liquid inside a serum bottle from the trait it holds -- the sibling of
/// BeeTintSource, and registered the same way.
///
/// The model is vanilla's potion pair rather than textures of this mod's own:
/// `minecraft:item/potion_overlay` (the liquid, shaped to the inside of the bottle and
/// drawn light precisely so it can be tinted) under `minecraft:item/potion` (the glass and
/// cork). That is exactly how a water bottle is drawn, which is the point -- a serum should
/// read as a real bottle with something in it, and hand-drawn glass at 16x16 never quite
/// does. One tint over shared vanilla art renders every serum in the table.
///
/// ```json
/// "tints": [
///   { "type": "mellifera:serum_color" }
/// ]
/// ```
///
/// One entry, not two: `tints` is indexed by layer and layers past the end of the array are
/// simply left untinted, so the glass keeps its authored colours without a pass-through
/// white. Vanilla's own potion model does the same.
public record SerumTintSource() implements ItemTintSource {
    public static final MapCodec<SerumTintSource> MAP_CODEC = MapCodec.unit(SerumTintSource::new);

    /// A vial with no readable trait renders as plain water rather than black.
    private static final int UNKNOWN = 0x6A9AD0;

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        SerumData data = SerumItem.data(stack);
        BeeTrait trait = data == null ? null : data.resolvedTrait();
        return 0xFF000000 | (trait == null ? UNKNOWN : trait.liquidColor());
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
