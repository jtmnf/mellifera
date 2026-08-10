package com.joaonf.mellifera;

import org.slf4j.Logger;

import com.joaonf.mellifera.config.MelliferaOutputConfig;
import com.joaonf.mellifera.network.MelliferaPayloads;
import com.joaonf.mellifera.registry.MelliferaBeeBranches;
import com.joaonf.mellifera.registry.MelliferaBeeSpecies;
import com.joaonf.mellifera.registry.MelliferaBlockEntities;
import com.joaonf.mellifera.registry.MelliferaBlocks;
import com.joaonf.mellifera.registry.MelliferaCombTypes;
import com.joaonf.mellifera.registry.MelliferaCreativeTabs;
import com.joaonf.mellifera.registry.MelliferaDataComponents;
import com.joaonf.mellifera.registry.MelliferaFluids;
import com.joaonf.mellifera.registry.MelliferaItems;
import com.joaonf.mellifera.registry.MelliferaMenus;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Mellifera.MODID)
public class Mellifera {
    public static final String MODID = "mellifera";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mellifera(IEventBus modEventBus, ModContainer modContainer) {
        MelliferaBeeSpecies.register(modEventBus);
        MelliferaBeeBranches.register(modEventBus);
        MelliferaCombTypes.register(modEventBus);
        MelliferaBlocks.register(modEventBus);
        MelliferaFluids.register(modEventBus);
        MelliferaDataComponents.register(modEventBus);
        MelliferaItems.register(modEventBus);
        MelliferaBlockEntities.register(modEventBus);
        MelliferaMenus.register(modEventBus);
        MelliferaCreativeTabs.register(modEventBus);
        MelliferaOutputConfig.register(modEventBus);
        MelliferaPayloads.register(modEventBus);
    }
}
