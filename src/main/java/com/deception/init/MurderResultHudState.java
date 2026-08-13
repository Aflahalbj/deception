package com.deception.init;

import net.minecraft.world.item.ItemStack;

/**
 * Nampung item means & clue pilihan murderer yang dikirim server ke layar FS
 * (lihat network/MurderResultHudPacket). Cuma state mentah -- gambarnya di
 * MurderResultOverlay.
 */
public class MurderResultHudState {

    private static ItemStack means = ItemStack.EMPTY;
    private static ItemStack clue = ItemStack.EMPTY;

    public static void set(ItemStack newMeans, ItemStack newClue) {
        means = newMeans;
        clue = newClue;
    }

    public static ItemStack getMeans() {
        return means;
    }

    public static ItemStack getClue() {
        return clue;
    }

    public static boolean isEmpty() {
        return means.isEmpty() && clue.isEmpty();
    }
}
