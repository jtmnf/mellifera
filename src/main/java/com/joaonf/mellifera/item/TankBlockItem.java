package com.joaonf.mellifera.item;

import java.util.function.Consumer;

import com.joaonf.mellifera.block.TankBlockEntity;
import com.joaonf.mellifera.registry.MelliferaDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

/// The Tank as an item, which is a Tank with its contents still in it -- see
/// TankBlockEntity.collectImplicitComponents.
///
/// The tooltip is the whole reason this is not a plain BlockItem. A tank carried around full looks
/// exactly like an empty one in an inventory, so without it the one thing that makes the block worth
/// picking up is invisible until it is put back down.
public class TankBlockItem extends BlockItem {
    public TankBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        SimpleFluidContent contents = stack.get(MelliferaDataComponents.TANK_CONTENTS.get());
        if (contents == null || contents.isEmpty()) {
            return;
        }

        builder.accept(Component.translatable("block.mellifera.tank.contents",
                contents.copy().getHoverName(), contents.getAmount(), TankBlockEntity.CAPACITY)
            .withStyle(ChatFormatting.GRAY));
    }
}
