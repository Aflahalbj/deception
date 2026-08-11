package com.deception.menu;

import com.deception.DeceptionMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, DeceptionMod.MOD_ID);

    public static final RegistryObject<MenuType<WitnessGuessMenu>> WITNESS_GUESS = MENUS.register("witness_guess",
            () -> IForgeMenuType.create((windowId, inv, data) -> new WitnessGuessMenu(windowId, inv)));
}
