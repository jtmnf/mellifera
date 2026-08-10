package com.joaonf.mellifera.item;

import com.joaonf.mellifera.client.MelliferaClientHooks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/// Opens the mutation browser -- the in-game answer to "what do I cross to get this bee",
/// which otherwise only existed in the source code.
public class BeeGuideItem extends Item {
    public BeeGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            MelliferaClientHooks.openBeeGuide();
        }

        return InteractionResult.SUCCESS;
    }
}
