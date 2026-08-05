package com.deception.init;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Cegah player buka inventory/container screen (E, chest, dll) selama lagi
 * "harus tutup mata" (BlindfoldClientState.isBlindfolded()) -- biar gak bisa
 * ngelepas noteblock lewat GUI. Dicek di CLIENT (ScreenEvent.Opening
 * cancellable), jadi ini murni pencegahan biasa buat pemain jujur; safety
 * net beneran (re-equip paksa tiap tick) tetep ada di GameManager
 * server-side buat jaga-jaga kalo somehow kelolos.
 */
public class BlindfoldInputLock {

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen newScreen = event.getNewScreen();
        if (newScreen instanceof AbstractContainerScreen<?> && BlindfoldClientState.isBlindfolded()) {
            event.setCanceled(true);
        }
    }
}