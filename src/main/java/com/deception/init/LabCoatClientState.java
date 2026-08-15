package com.deception.init;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Daftar pemain yang lagi pake jas lab, dikirim server lewat LabCoatPacket.
 * Cuma data mentah -- yang gambar client/render/LabCoatLayer.
 *
 * <p>HashSet biasa aman: yang nulis handler packet (jalan di thread client
 * lewat enqueueWork) dan yang baca renderer, dua-duanya thread yang sama.
 */
public class LabCoatClientState {

    private static final Set<UUID> WEARERS = new HashSet<>();

    public static void set(UUID uuid, boolean wearing) {
        if (wearing) {
            WEARERS.add(uuid);
        } else {
            WEARERS.remove(uuid);
        }
    }

    public static boolean isWearing(UUID uuid) {
        return WEARERS.contains(uuid);
    }

    /**
     * State-nya static & client-only, jadi gak ikut kebuang pas pindah
     * server. Tanpa reset di sini, pemain dengan UUID yang sama di world
     * berikutnya bisa kebagian jas nyasar.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WEARERS.clear();
    }
}
