package com.joaonf.mellifera.item;

import com.joaonf.mellifera.registry.MelliferaBlockTags;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/// Takes a wild hive apart. Sixteen of them and it has had it.
///
/// A class of its own for one reason: `Item.mineBlock` spends a Tool's `damagePerBlock` on *every*
/// block broken with it that is not instabreak, not only on the blocks its rules match. That is
/// the right default for a pickaxe with two hundred and fifty points in it and a trap for one with
/// sixteen -- a player who happened to be holding the Scoop when they cleared a bit of dirt would
/// have spent a quarter of it on nothing, and never know why.
///
/// So the damage is charged where the tool is actually doing its job: on a hive, and nowhere else.
/// The Scoop still breaks anything else at ordinary speed, and still does it for free.
public class ScoopItem extends Item {
    public ScoopItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (!level.isClientSide() && state.is(MelliferaBlockTags.HIVES)) {
            stack.hurtAndBreak(1, owner, EquipmentSlot.MAINHAND);
        }

        // True the way the supertype means it: this item handled the break. Returning the
        // supertype's answer instead would hand the durability back to the rule this exists to
        // narrow, which is the whole point of being here.
        return true;
    }
}
