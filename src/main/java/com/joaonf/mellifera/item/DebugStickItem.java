package com.joaonf.mellifera.item;

import java.util.function.Consumer;

import com.joaonf.mellifera.block.BeeHousingBlockEntity;
import com.joaonf.mellifera.block.CentrifugeBlockEntity;
import com.joaonf.mellifera.block.PipeBlockEntity;
import com.joaonf.mellifera.registry.MelliferaDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/// A testing tool, not a gameplay item: waiting out 20-70 production cycles to see whether
/// a mutation fires makes the genetics effectively untestable.
///
/// It never creates bees or products itself -- it only flips a flag the machine's own next
/// tick honours, so whatever comes out is exactly what the real simulation would have
/// produced, mutation rolls and all. That matters: a tool that fabricated its own offspring
/// could quietly disagree with the thing it is meant to be testing.
///
/// On an Apiary: right-click does whichever job the stick is currently set to.
/// On a Centrifuge: completes the current spin, whatever the mode.
/// Sneak+right-click in the air toggles the mode.
///
/// The mode used to be the sneak modifier on the block itself, which meant the two jobs
/// were a keypress apart with nothing on screen saying which one a plain click would do.
/// A mode you set once and can read off the tooltip is harder to trigger by accident, and
/// it frees sneak+click on a block to keep meaning what it does everywhere else.
public class DebugStickItem extends Item {
    public DebugStickItem(Properties properties) {
        super(properties);
    }

    /// True runs the queen out entirely; false runs a single production cycle.
    public static boolean fullProduction(ItemStack stack) {
        return stack.getOrDefault(MelliferaDataComponents.DEBUG_FULL_PRODUCTION.get(), false);
    }

    /// Sneak+right-click with nothing in reach flips the mode.
    ///
    /// Also reached by sneak-clicking a block this item has no use for, because Vanilla
    /// falls through to use() whenever useOn() passes. That is harmless and arguably right:
    /// the gesture means the same thing either way, and the only blocks useOn claims are
    /// the ones the stick actually operates.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        boolean full = !fullProduction(stack);
        stack.set(MelliferaDataComponents.DEBUG_FULL_PRODUCTION.get(), full);
        say(player, full ? "item.mellifera.debug_stick.mode.full" : "item.mellifera.debug_stick.mode.one");
        return InteractionResult.SUCCESS;
    }

    /// A one-line reminder of what a click will do, so the mode is never invisible.
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable(
                fullProduction(stack) ? "item.mellifera.debug_stick.mode.full" : "item.mellifera.debug_stick.mode.one")
            .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.mellifera.debug_stick.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());
        Player player = context.getPlayer();

        if (blockEntity instanceof BeeHousingBlockEntity housing) {
            if (!housing.hasQueen()) {
                say(player, "item.mellifera.debug_stick.no_queen");
                return InteractionResult.CONSUME;
            }

            if (fullProduction(context.getItemInHand())) {
                housing.debugKillQueen();
                say(player, "item.mellifera.debug_stick.queen_ended");
            } else {
                housing.debugCycle();
                say(player, "item.mellifera.debug_stick.cycle");
            }

            return InteractionResult.CONSUME;
        }

        if (blockEntity instanceof PipeBlockEntity pipe) {
            // Not an action: a reading. A pipe showing no liquid is either moving nothing or moving
            // something without saying so, and those look the same from outside.
            if (player != null) {
                player.sendOverlayMessage(Component.literal(pipe.debugProbe(level, context.getClickedPos())));
            }

            return InteractionResult.CONSUME;
        }

        if (blockEntity instanceof CentrifugeBlockEntity centrifuge) {
            centrifuge.debugSpin();
            say(player, "item.mellifera.debug_stick.spin");
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    /// Action bar rather than chat -- this fires repeatedly while testing and would bury
    /// anything else in the log.
    private static void say(Player player, String key) {
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(key));
        }
    }
}
