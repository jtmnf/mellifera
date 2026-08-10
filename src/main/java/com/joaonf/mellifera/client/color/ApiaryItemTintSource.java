package com.joaonf.mellifera.client.color;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.block.ApiaryBlock;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;

/// The same paint as ApiaryTintSource, for the icon of an Apiary that is in a stack rather than
/// in the world.
///
/// The colour rides in the stack's `block_state` component, which is vanilla plumbing from both
/// ends and not something this mod has to keep in step: the loot table's `copy_state` puts the
/// property there when the hive is broken, and BlockItem.updateBlockStateFromTag reads it back
/// out when the hive is placed. All this has to do is look at it.
///
/// Registered as `mellifera:apiary_color` and asked for by items/apiary.json.
public record ApiaryItemTintSource() implements ItemTintSource {
    public static final MapCodec<ApiaryItemTintSource> MAP_CODEC = MapCodec.unit(new ApiaryItemTintSource());

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        BlockItemStateProperties properties = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        ApiaryBlock.Tint tint = properties.get(ApiaryBlock.TINT);
        return (tint == null ? ApiaryBlock.Tint.NONE : tint).paintColor();
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
