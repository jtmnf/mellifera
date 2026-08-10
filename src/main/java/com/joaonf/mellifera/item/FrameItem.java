package com.joaonf.mellifera.item;

import java.util.function.Consumer;

import com.joaonf.mellifera.bee.FrameType;
import com.joaonf.mellifera.registry.MelliferaDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/// Speeds up the apiary it sits in for as long as its wear lasts -- ApiaryBlockEntity reads
/// and depletes FRAME_WEAR directly during production, this class owns no tick logic itself.
///
/// Wear shows as a Vanilla-style durability bar (green -> red as it depletes) rather than a
/// tooltip line, the same convention every damageable item already uses -- a fresh frame
/// (wear == FRESH_WEAR) shows no bar at all, exactly like an undamaged tool.
public class FrameItem extends Item {
    public static final float FRESH_WEAR = 1.0F;

    private final FrameType type;

    public FrameItem(FrameType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public FrameType type() {
        return type;
    }

    /// A frame reforged with an ender pearl never wears out at all.
    ///
    /// Deliberately not an enchantment: wear here is a component spent once per production
    /// pulse, not durability damage per use, so Unbreaking's probabilistic saving throw had
    /// no sensible meaning and Mending -- which shares Unbreaking's item tag -- would have
    /// been applicable while doing nothing. An anvil combination says exactly what it does.
    public static boolean neverWears(ItemStack stack) {
        return stack.getOrDefault(MelliferaDataComponents.FRAME_UNBREAKABLE.get(), false);
    }

    /// Frames do very different things now, and none of it is guessable from the icon.
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.mellifera.frame." + type.frameName())
            .withStyle(ChatFormatting.GRAY));

        if (neverWears(stack)) {
            builder.accept(Component.translatable("tooltip.mellifera.frame.permanent")
                .withStyle(ChatFormatting.AQUA));
        }
    }

    /// Permanent frames shimmer.
    ///
    /// isFoil rather than the ENCHANTMENT_GLINT_OVERRIDE component that royal jelly uses,
    /// because here it is a property of the individual stack and not of the item: a frame
    /// and the same frame reforged on an anvil are the same item id, told apart only by
    /// FRAME_UNBREAKABLE. A component default would have glinted every frame in the game.
    ///
    /// It also fills a real gap in reading the item. A permanent frame is the one that
    /// stops showing a wear bar, so at a glance it was indistinguishable from a brand new
    /// one -- the tooltip said so, and nothing else did.
    @Override
    public boolean isFoil(ItemStack stack) {
        return neverWears(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !neverWears(stack) && wear(stack) < FRESH_WEAR;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * wear(stack)), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(wear(stack) / 3.0F, 1.0F, 1.0F);
    }

    private static float wear(ItemStack stack) {
        return stack.getOrDefault(MelliferaDataComponents.FRAME_WEAR.get(), FRESH_WEAR);
    }
}
