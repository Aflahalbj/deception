package com.deception.init;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * State blindfold di sisi CLIENT. Disinkron dari server lewat
 * BlindfoldStatePacket -- BUKAN baca GameManager server langsung, karena
 * kalo pake dedicated server, GameManager di client itu instance kosong
 * yang gak pernah ke-update sama server (beda JVM/proses). Tick counter di
 * sini tick CLIENT murni (naik lewat ClientTickEvent), independen dari
 * tick server -- makanya kurva animasinya dihitung ulang di sini, bukan
 * dipanggil dari server.
 */
public class BlindfoldClientState {

    /**
     * Kurva animasi digambarin sebagai deretan "keyframe": {durasi (tick),
     * target progress}. Tiap segmen di-interpolasi (smoothstep) dari
     * progress akhir segmen sebelumnya ke target-nya. Biar keliatan kayak
     * ngantuk beneran (kedip pelan-pelan, makin lama makin dalem, baru
     * bener-bener nutup), bukan cuma 1x kedipan doang.
     *
     * NUTUP: mulai dari mata kebuka (0) -> kedip 1 (dangkal) -> kedip 2
     * (agak dalem) -> kedip 3 (dalem banget) -> nutup beneran pelan-pelan
     * dan nahan di situ (1).
     */
    private static final float[][] CLOSE_KEYFRAMES = {
            // {durasi tick, target progress}
            {4,  0.35f},  // kedip 1: dangkal
            {4,  0.10f},  // buka lagi, gak penuh
            {5,  0.60f},  // kedip 2: lebih dalem
            {5,  0.15f},  // buka lagi dikit
            {6,  0.90f},  // kedip 3: dalem banget
            {5,  0.20f},  // sempet kebuka bentar
            {16, 1.00f},  // nutup beneran, pelan-pelan, nahan di sini
    };

    /**
     * BUKA: mulai dari mata ketutup (1) -> ngerem/kedip beberapa kali
     * (celah dikit demi dikit) -> baru bener-bener kebuka penuh dan nahan
     * di situ (0).
     */
    private static final float[][] OPEN_KEYFRAMES = {
            {5,  0.75f},  // celah dikit
            {4,  0.95f},  // nutup lagi hampir penuh
            {6,  0.50f},  // celah lebih lebar
            {4,  0.85f},  // nutup lagi dikit
            {6,  0.15f},  // celah lebar banget
            {4,  0.35f},  // penyesuaian kecil
            {10, 0.00f},  // bener-bener kebuka, nahan di sini
    };

    private static long clientTickCounter = 0;
    private static long transitionStartTick = 0;
    private static boolean closing = false;
    private static boolean hasState = false;
    private static boolean blindfolded = false; // true selama beneran "harus tutup mata" (dipake buat kunci inventory)

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            clientTickCounter++;
        }
    }

    /** Dipanggil dari BlindfoldStatePacket pas nerima update dari server. */
    public static void startTransition(boolean isClosing) {
        closing = isClosing;
        transitionStartTick = clientTickCounter;
        hasState = true;
        blindfolded = isClosing;
    }

    /** Dipake buat kunci input (misal cegah buka inventory) selama player "harus tutup mata". */
    public static boolean isBlindfolded() {
        return blindfolded;
    }

    /** Dibaca BlindfoldOverlay tiap frame render. 0 = kebuka penuh, 1 = ketutup penuh. */
    public static float getProgress(float partialTick) {
        if (!hasState) return 0f;
        float elapsed = (clientTickCounter - transitionStartTick) + partialTick;
        float startProgress = closing ? 0f : 1f;
        float[][] keyframes = closing ? CLOSE_KEYFRAMES : OPEN_KEYFRAMES;
        return evalKeyframes(keyframes, startProgress, elapsed);
    }

    private static float evalKeyframes(float[][] keyframes, float startProgress, float elapsed) {
        float prevProgress = startProgress;
        float cursor = 0f;
        for (float[] kf : keyframes) {
            float duration = kf[0];
            float target = kf[1];
            if (elapsed < cursor + duration) {
                float t = (elapsed - cursor) / duration;
                return lerp(prevProgress, target, smoothstep(t));
            }
            cursor += duration;
            prevProgress = target;
        }
        return prevProgress; // abis semua keyframe kelar, nahan di nilai terakhir
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }
}