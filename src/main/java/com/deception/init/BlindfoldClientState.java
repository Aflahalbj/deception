package com.deception.init;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BlindfoldClientState {

    private static final float[][] CLOSE_KEYFRAMES = {
            {4,  0.35f},
            {4,  0.10f},
            {5,  0.60f},
            {5,  0.15f},
            {6,  0.90f},
            {5,  0.20f},
            {16, 1.00f},
    };

    private static final float[][] OPEN_KEYFRAMES = {
            {5,  0.75f},
            {4,  0.95f},
            {6,  0.50f},
            {4,  0.85f},
            {6,  0.15f},
            {4,  0.35f},
            {10, 0.00f},
    };

    private static long clientTickCounter = 0;
    private static long transitionStartTick = 0;
    private static boolean closing = false;
    private static boolean hasState = false;
    private static boolean blindfolded = false;
    private static boolean forceClosed = false;
    // dipake pas rejoin -- langsung "nutup penuh" tanpa animasi (beda sama
    // forceClosed di atas, yang malah maksa BUKA tanpa animasi)
    private static boolean snapShut = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            clientTickCounter++;
        }
    }

    public static void startTransition(boolean isClosing) {
        closing = isClosing;
        transitionStartTick = clientTickCounter;
        hasState = true;
        blindfolded = isClosing;
        forceClosed = false;
        snapShut = false;
    }

    public static void forceClose() {
        forceClosed = true;
        snapShut = false;
        blindfolded = false;
        hasState = true;
    }

    // Dipanggil pas player rejoin di tengah night dan harusnya masih
    // tutup mata -- client baru gak punya state animasi apa-apa, jadi
    // langsung "snap" ke kondisi tertutup penuh tanpa ngulang animasi.
    public static void snapShut() {
        snapShut = true;
        forceClosed = false;
        blindfolded = true;
        hasState = true;
    }

    public static boolean isBlindfolded() {
        return blindfolded;
    }

    public static float getProgress(float partialTick) {
        if (snapShut) {
            return 1f;
        }
        if (forceClosed) {
            return 0f;
        }
        if (!hasState) {
            return 0f;
        }
        
        // Cek kalo blindfolded = false, berarti harus buka
        if (!blindfolded) {
            float elapsed = (clientTickCounter - transitionStartTick) + partialTick;
            float startProgress = 1f;
            float[][] keyframes = OPEN_KEYFRAMES;
            return evalKeyframes(keyframes, startProgress, elapsed);
        }
        
        // closing = true berarti nutup
        if (closing) {
            float elapsed = (clientTickCounter - transitionStartTick) + partialTick;
            float startProgress = 0f;
            float[][] keyframes = CLOSE_KEYFRAMES;
            return evalKeyframes(keyframes, startProgress, elapsed);
        }
        
        return 0f;
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
        return prevProgress;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }
}