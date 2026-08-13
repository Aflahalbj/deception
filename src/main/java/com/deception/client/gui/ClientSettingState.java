package com.deception.client.gui;

import com.deception.game.SettingSnapshot;
import com.deception.network.ModNetworking;
import com.deception.network.SettingActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Cache client-side dari snapshot setting terakhir yang dikirim server, plus
 * jalur ngirim aksi balik ke server. Semua screen setting baca dari sini --
 * gak ada satu pun yang nyimpen atau ngitung nilainya sendiri.
 */
public final class ClientSettingState {

    private ClientSettingState() {}

    private static SettingSnapshot current;

    /**
     * Dipanggil handler {@code SettingSyncPacket}. Kalau {@code open}, buka
     * GUI-nya dari nol; kalau enggak, gambar ulang screen setting yang lagi
     * kebuka (kalau emang ada).
     */
    public static void accept(SettingSnapshot snapshot, boolean open) {
        current = snapshot;

        Minecraft minecraft = Minecraft.getInstance();
        if (open) {
            minecraft.setScreen(new SettingScreen());
            return;
        }
        Screen screen = minecraft.screen;
        if (screen instanceof DeceptionScreen deceptionScreen) {
            deceptionScreen.refresh();
        }
    }

    /**
     * Snapshot terakhir. Bisa null cuma kalau screen dibuka tanpa lewat
     * packet -- screen setting selalu cek ini sebelum pakai.
     */
    public static SettingSnapshot get() {
        return current;
    }

    public static void send(SettingActionPacket.Action action) {
        ModNetworking.CHANNEL.sendToServer(new SettingActionPacket(action));
    }

    public static void send(SettingActionPacket.Action action, int amount) {
        ModNetworking.CHANNEL.sendToServer(new SettingActionPacket(action, amount));
    }

    public static void send(SettingActionPacket.Action action, int amount, String playerName) {
        ModNetworking.CHANNEL.sendToServer(new SettingActionPacket(action, amount, playerName));
    }
}
