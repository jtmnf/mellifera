package com.joaonf.mellifera.item;

import com.joaonf.mellifera.bee.BeeStacks;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/// Reads the genome of every bee the player is carrying, at the price of a honey drop each.
///
/// WHY IT EXISTS. Without it a bee's tooltip prints its whole genome the moment it is picked up,
/// recessive alleles included, and the breeding game is played on an open board: the one thing a
/// player would otherwise have to work out -- what a bee is quietly carrying -- is simply told to
/// them. An unread bee names its species and nothing else (see BeeStacks.isAnalysed), and this is
/// what turns the rest over.
///
/// WHY IT HAS NO WINDOW, which is the part that departs from Forestry. Its Beealyzer is a GUI you
/// feed one bee at a time, and one bee at a time is the right pace for a machine that charges by
/// the bee -- but by the time a hive is running a player has a stack of drones and no interest in
/// clicking through forty of them. So it works on the whole inventory in one press, charges the
/// same price per bee, and stops when it has nothing left to spend. The cost is per bee either
/// way; what is dropped is the clicking.
///
/// WHAT A READING COSTS is one point of the book's own life, and there are READINGS of them. One
/// charge rather than two: an earlier version also took a honey drop per bee, which made every
/// press a sum of two dwindling things for no gain. The honey moved into the recipe, where it
/// still does the job it was there for -- a honey drop comes out of a Centrifuge, so nothing can
/// be read until a player has a machine spinning combs, and the first few hives are worked blind.
/// That stretch of the game is what this whole mechanic exists to put back.
///
/// Spent rather than broken: see the note on hurt() below.
public class BeealyzerItem extends Item {
    /// Bees one book can read. Its durability, and the only place the number lives.
    public static final int READINGS = 32;

    public BeealyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Both sides run use(); only the server may edit the stacks, and the overlay message and
        // the sound are sent from there.
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        int read = analyseInventory(player, player.getItemInHand(hand));

        // The action bar rather than chat, the way the Tank and the Capacitor report: it is a
        // reading, not a conversation.
        if (read == 0) {
            serverPlayer.sendSystemMessage(Component.translatable(nothingToDo(player)), true);
            return InteractionResult.CONSUME;
        }

        serverPlayer.sendSystemMessage(Component.translatable("message.mellifera.beealyzer.read", read), true);
        level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.7F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    /// Reads as many carried bees as the book has life left for, and gives back how many.
    ///
    /// One pass over the inventory, wearing the book as it goes rather than counting first and
    /// charging after: a partial job is a perfectly good outcome here, and it means the points
    /// spent always match the bees read even if the count and the spend disagreed about a slot.
    private int analyseInventory(Player player, ItemStack book) {
        Inventory inventory = player.getInventory();
        int read = 0;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (BeeStacks.genomeOf(stack) == null || BeeStacks.isAnalysed(stack)) {
                continue;
            }

            if (!hurt(player, book)) {
                break;
            }

            // The whole stack at once. Bees only stack when their components match, so a stack of
            // drones is one bee's genome however many of it there are, and charging per item would
            // be charging for the same reading over and over.
            BeeStacks.analyse(stack);
            read++;
        }

        return read;
    }

    /// Spends one reading, and reports whether there was one to spend.
    ///
    /// The book is left spent rather than destroyed: it stops one point short of breaking and says
    /// so. A tool that vanishes out of the hand mid-press is startling, and this one is a ledger --
    /// a full ledger is a thing you put down and copy out, not a thing that crumbles. It also
    /// leaves something in the hand for the "worn out" message to be about.
    private boolean hurt(Player player, ItemStack book) {
        if (player.hasInfiniteMaterials()) {
            return true;
        }

        if (book.getDamageValue() >= READINGS - 1) {
            return false;
        }

        book.setDamageValue(book.getDamageValue() + 1);
        return true;
    }

    /// Nothing happened, and the two reasons are worth telling apart: a player with a spent book is
    /// being told to make another, and a player with nothing unread is being told the thing worked
    /// and there was simply nothing left to do.
    private static String nothingToDo(Player player) {
        Inventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (BeeStacks.genomeOf(stack) != null && !BeeStacks.isAnalysed(stack)) {
                return "message.mellifera.beealyzer.spent";
            }
        }

        return "message.mellifera.beealyzer.nothing";
    }
}
