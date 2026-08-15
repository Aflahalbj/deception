package com.deception.init;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Sisi CLIENT dari CutscenePacket: ngunci arah pandang selama cutscene intro
 * dan nyimpen flag "gambar intro lagi tampil" buat {@link CutsceneOverlay}.
 *
 * <p>Kunci kameranya dua lapis, dan dua-duanya WAJIB:
 * <ul>
 *   <li>{@link #onClientTick} -- nimpa yaw/pitch player TIAP TICK, persis
 *       setelah vanilla selesai nerapin gerakan mouse (MouseHandler#turnPlayer
 *       jalan lebih awal di Minecraft#tick). Ini yang bikin rotasi ASLI
 *       playernya gak pernah berubah, jadi server juga gak keganggu.</li>
 *   <li>{@link #onComputeCameraAngles} -- nimpa sudut kamera TIAP FRAME.
 *       Perlu karena frame di antara dua tick masih di-interpolasi pake
 *       gerakan mouse yang baru masuk; tanpa ini kameranya keliatan
 *       "bergetar" ngikutin mouse walaupun tiap tick ditarik balik.</li>
 * </ul>
 *
 * Gerak badan sendiri dikunci MovementLockClientState (server ngirim
 * MovementLockPacket bareng packet ini) -- di gamemode spectator input yang
 * sama juga yang dipake buat terbang, jadi kunci itu cukup.
 */
public class CutsceneClientState {

    private static boolean active = false;
    private static float yaw = 0F;
    private static float pitch = 0F;
    private static boolean showImage = false;
    /** Waktu (ms) gambar mulai tampil -- buat fade-in, lihat CutsceneOverlay. */
    private static long imageShownAt = 0L;

    public static void set(boolean active, float yaw, float pitch, boolean showImage) {
        if (showImage && !CutsceneClientState.showImage) {
            imageShownAt = System.currentTimeMillis();
        }
        CutsceneClientState.active = active;
        CutsceneClientState.yaw = yaw;
        CutsceneClientState.pitch = pitch;
        CutsceneClientState.showImage = active && showImage;
        if (!active) {
            imageShownAt = 0L;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isShowingImage() {
        return showImage;
    }

    public static long imageShownAt() {
        return imageShownAt;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // yRotO/xRotO ikut di-set: dua field itu nilai tick SEBELUMNYA yang
        // dipake interpolasi render. Kalo cuma yRot/xRot yang dipaku,
        // kameranya bakal bolak-balik antara sudut lama & sudut kunci.
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active) return;
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(0F);
    }

    /**
     * Di spectator, klik kiri ke player lain itu "pindah jadi dia" -- kamera
     * langsung lompat ke tempat orang lain dan cutscene-nya kacau. Semua klik
     * dimatiin selama cutscene jalan.
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (active) event.setCanceled(true);
    }

    /**
     * State ini static & client-only, jadi dia GAK ikut kebuang pas keluar
     * server. Tanpa reset di sini, orang yang disconnect pas cutscene
     * (crash, kick, tombol Disconnect) bakal masuk ke world berikutnya --
     * singleplayer sekalipun -- dengan kamera masih kepaku. Kunci gerak
     * ikut dilepas karena dipasangnya emang sepaket sama cutscene.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        set(false, 0F, 0F, false);
        MovementLockClientState.setLocked(false);
    }
}
