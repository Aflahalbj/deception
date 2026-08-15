package com.deception.game;

import com.deception.network.ModNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Player dipaku di tempat" -- dipake fase shootout biar target diem semua
 * dan murderer punya sasaran yang jelas (lihat PresentationManager#
 * startWitnessShootout).
 *
 * Dua lapis, sengaja:
 *  1. CLIENT (MovementLockPacket -> init/MovementLockClientState): input WASD/
 *     lompat/sneak dinolin di MovementInputUpdateEvent. Ini yang bikin rasanya
 *     mulus -- playernya emang gak jalan, jadi gak ada rubber-band.
 *  2. SERVER (tick() di sini): kalo posisinya somehow tetep geser (client
 *     hacked/mod lain/kena knockback), ditarik balik ke anchor. Ini safety
 *     net, pola sama kayak force-reequip blindfold di GameManager.
 *
 * Rotasi SENGAJA dibiarin bebas -- yang dibekuin cuma posisi, biar mereka
 * tetep bisa nengok liat murderer ngapain.
 */
public class PlayerFreeze {

    /**
     * Toleransi geser sebelum ditarik balik. SENGAJA longgar (setengah blok):
     * yang beneran nahan playernya itu kunci input di client, ini cuma jaring
     * pengaman buat yang curang. Tiap tarikan balik itu teleport = packet ke
     * SEMUA orang yang nge-track playernya, jadi ambang ketat malah bikin
     * banjir packet gara-gara geseran floating point doang.
     */
    private static final double SNAP_BACK_DISTANCE_SQR = 0.5 * 0.5;

    /** Cek posisi gak perlu tiap tick -- 5 tick sekali udah lebih dari cukup buat jaring pengaman. */
    private static final int CHECK_INTERVAL_TICKS = 5;

    private record Anchor(double x, double y, double z) {}

    private static final Map<UUID, Anchor> anchors = new HashMap<>();
    private static int tickCounter = 0;

    /** Teleport ke titik yang dimau (sekalian ngadep arah yaw), terus paku di situ. */
    public static void freezeAt(ServerPlayer player, double x, double y, double z, float yaw) {
        freezeAt(player, x, y, z, yaw, 0.0F);
    }

    /**
     * Sama kayak di atas tapi sekalian nentuin pitch -- dipake cutscene
     * intro, yang arah pandangnya emang dipatok (lihat GameManager#startCutscene).
     */
    public static void freezeAt(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
        player.setDeltaMovement(0, 0, 0);
        player.hurtMarked = true; // paksa client sinkron delta movement 0
        // Matiin gravitasi selama dibekuin: tanpa ini, kalo titik berdirinya
        // meleset dikit di atas lantai, playernya bakal jatuh terus ditarik
        // balik TIAP TICK -- teleport beruntun yang percuma dan bikin banjir
        // packet ke semua orang.
        player.setNoGravity(true);
        anchors.put(player.getUUID(), new Anchor(x, y, z));
        ModNetworking.sendMovementLock(player, true);
    }

    public static void unfreeze(ServerPlayer player) {
        anchors.remove(player.getUUID());
        player.setNoGravity(false);
        ModNetworking.sendMovementLock(player, false);
    }

    /** Lepas semua -- dipanggil pas shootout kelar / game di-reset. */
    public static void unfreezeAll(MinecraftServer server) {
        if (anchors.isEmpty()) return;
        for (UUID uuid : Map.copyOf(anchors).keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.setNoGravity(false);
                ModNetworking.sendMovementLock(player, false);
            }
        }
        anchors.clear();
    }

    public static boolean isFrozen(UUID uuid) {
        return anchors.containsKey(uuid);
    }

    /** Dipanggil tiap tick dari PresentationManager#tick. */
    public static void tick(MinecraftServer server) {
        if (anchors.isEmpty()) return;
        if (++tickCounter % CHECK_INTERVAL_TICKS != 0) return;

        for (Map.Entry<UUID, Anchor> entry : anchors.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;

            Anchor anchor = entry.getValue();
            player.setDeltaMovement(0, 0, 0);
            player.fallDistance = 0;

            if (player.distanceToSqr(anchor.x(), anchor.y(), anchor.z()) > SNAP_BACK_DISTANCE_SQR) {
                // teleportTo(x,y,z) di ServerPlayer nahan rotasi yang sekarang
                // -- sengaja, biar tarikan balik ini gak ngerebut arah pandang
                // playernya di tengah-tengah dia lagi nengok.
                player.teleportTo(anchor.x(), anchor.y(), anchor.z());
            }
        }
    }
}
