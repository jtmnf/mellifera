package com.joaonf.mellifera.registry;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.menu.ApiaryMenu;
import com.joaonf.mellifera.menu.CarpenterMenu;
import com.joaonf.mellifera.menu.CentrifugeMenu;
import com.joaonf.mellifera.menu.SqueezerMenu;
import com.joaonf.mellifera.menu.InfuserMenu;
import com.joaonf.mellifera.menu.IsolatorMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MelliferaMenus {
    public static final DeferredRegister<MenuType<?>> TYPES =
        DeferredRegister.create(Registries.MENU, Mellifera.MODID);

    // IMenuTypeExtension.create rather than plain MenuType: the screen needs the apiary's
    // position to read ContainerData and to look up the block entity for anything not
    // exposed through data slots.
    public static final DeferredHolder<MenuType<?>, MenuType<ApiaryMenu>> APIARY =
        TYPES.register("apiary", () -> IMenuTypeExtension.create(ApiaryMenu::new));

    // Same reasoning again: the centrifuge screen reads its progress out of ContainerData.
    public static final DeferredHolder<MenuType<?>, MenuType<CentrifugeMenu>> CENTRIFUGE =
        TYPES.register("centrifuge", () -> IMenuTypeExtension.create(CentrifugeMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SqueezerMenu>> SQUEEZER =
        TYPES.register("squeezer", () -> IMenuTypeExtension.create(SqueezerMenu::new));

    // Same reasoning again: the isolator screen reads its progress and trait cursor out of
    // ContainerData.
    public static final DeferredHolder<MenuType<?>, MenuType<IsolatorMenu>> ISOLATOR =
        TYPES.register("isolator", () -> IMenuTypeExtension.create(IsolatorMenu::new));

    // Same reasoning again: the infuser screen reads its progress and pollen charges out of
    // ContainerData.
    public static final DeferredHolder<MenuType<?>, MenuType<InfuserMenu>> INFUSER =
        TYPES.register("infuser", () -> IMenuTypeExtension.create(InfuserMenu::new));

    // And again: the carpenter screen reads its progress, its buffer and its tank out of
    // ContainerData.
    public static final DeferredHolder<MenuType<?>, MenuType<CarpenterMenu>> CARPENTER =
        TYPES.register("carpenter", () -> IMenuTypeExtension.create(CarpenterMenu::new));

    private MelliferaMenus() {}

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
