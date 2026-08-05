package com.deception.init;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Nyimpen & gambar title/subtitle/actionbar buat announcement fase night
 * ("Semua orang tutup mata", "Murderer buka mata", dst) -- DIGAMBAR MANUAL
 * (bukan vanilla title system) soalnya perlu urutan gambar yang kepastian:
 * dipanggil dari BlindfoldOverlay PERSIS SETELAH eyelid-nya (+ Z lebih
 * tinggi), biar teksnya selalu keliatan di atas walaupun mata lagi "merem".
 * Statenya disinkron lewat NightTitlePacket/NightActionBarPacket yang
 * di-BROADCAST ke semua client (bukan cuma yang bersangkutan), biar semua
 * pemain tau lagi fase apa/giliran siapa.
 *
 * Title digambar SKALA 2x (niru ukuran title vanilla, defaultnya kegedean
 * kecil kalo pake font biasa 1x). Actionbar "menunggu" itu PERSISTEN (gak
 * ada auto-fade timer) + animasi titik jalan (".", "..", "...") dihitung
 * lokal di client -- nunggu sampe di-clear eksplisit lewat clearActionBar()
 * (dipanggil server pas fase-nya abis), bukan di-refresh berkala dari
 * server.
 */
public class NightTitleClientState {

    private static final float TITLE_SCALE = 2.0f;
    private static final float SUBTITLE_SCALE = 1.2f;
    private static final int ACTIONBAR_FADE_IN = 5; // cuma fade-in, gak ada auto fade-out (persisten sampe di-clear)
    private static final int DOT_CYCLE_TICKS = 10;  // ganti jumlah titik tiap 0.5 detik

    private static long clientTickCounter = 0;

    private static Component title = null;
    private static Component subtitle = null;
    private static long titleStartTick = 0;
    private static int titleFadeIn = 10, titleStay = 40, titleFadeOut = 10;

    private static Component actionBarBase = null; // null = gak ada actionbar aktif
    private static long actionBarStartTick = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            clientTickCounter++;
        }
    }

    public static void showTitle(Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        NightTitleClientState.title = title;
        NightTitleClientState.subtitle = subtitle;
        NightTitleClientState.titleFadeIn = fadeIn;
        NightTitleClientState.titleStay = stay;
        NightTitleClientState.titleFadeOut = fadeOut;
        NightTitleClientState.titleStartTick = clientTickCounter;
    }

    /** Actionbar PERSISTEN (gak fade-out otomatis) -- dot animasi ditambahin sendiri pas render. */
    public static void showPersistentActionBar(Component baseText) {
        NightTitleClientState.actionBarBase = baseText;
        NightTitleClientState.actionBarStartTick = clientTickCounter;
    }

    public static void clearActionBar() {
        NightTitleClientState.actionBarBase = null;
    }

    /** Dipanggil dari BlindfoldOverlay PERSIS SETELAH eyelid digambar. */
    public static void draw(GuiGraphics guiGraphics, int screenWidth, int screenHeight, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        PoseStack pose = guiGraphics.pose();

        if (title != null) {
            float elapsed = (clientTickCounter - titleStartTick) + partialTick;
            float alpha = fadeAlpha(elapsed, titleFadeIn, titleStay, titleFadeOut);
            if (alpha > 0f) {
                int a = Math.round(alpha * 255f) & 0xFF;
                int color = (a << 24) | 0xFFFFFF;
                int titleCenterY = screenHeight / 2 - 40;

                // Title digambar gede (2x) -- scale di sekitar titik tengahnya
                // sendiri (translate ke posisi dulu, baru scale, baru gambar
                // di origin 0,0) biar tetep center walaupun discale.
                pose.pushPose();
                pose.translate(screenWidth / 2f, titleCenterY, 0);
                pose.scale(TITLE_SCALE, TITLE_SCALE, TITLE_SCALE);
                guiGraphics.drawCenteredString(font, title, 0, 0, color);
                pose.popPose();

                if (subtitle != null && !subtitle.getString().isEmpty()) {
                    pose.pushPose();
                    pose.translate(screenWidth / 2f, titleCenterY + 22, 0);
                    pose.scale(SUBTITLE_SCALE, SUBTITLE_SCALE, SUBTITLE_SCALE);
                    guiGraphics.drawCenteredString(font, subtitle, 0, 0, color);
                    pose.popPose();
                }
            } else if (elapsed > titleFadeIn + titleStay + titleFadeOut) {
                title = null;
                subtitle = null;
            }
        }

        if (actionBarBase != null) {
            float elapsed = (clientTickCounter - actionBarStartTick) + partialTick;
            float alpha = Math.min(1f, elapsed / ACTIONBAR_FADE_IN);
            if (alpha > 0f) {
                int a = Math.round(alpha * 255f) & 0xFF;
                int color = (a << 24) | 0xFFFFFF;
                int y = screenHeight - 60;

                // Animasi titik jalan: "item" -> "item." -> "item.." -> "item..." -> ulang
                int dotCount = (int) ((clientTickCounter / DOT_CYCLE_TICKS) % 4);
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotCount; i++) dots.append('.');
                Component animated = actionBarBase.copy().append(Component.literal(dots.toString()));

                guiGraphics.drawCenteredString(font, animated, screenWidth / 2, y, color);
            }
        }
    }

    private static float fadeAlpha(float elapsed, int fadeIn, int stay, int fadeOut) {
        if (elapsed < fadeIn) return elapsed / fadeIn;
        elapsed -= fadeIn;
        if (elapsed < stay) return 1f;
        elapsed -= stay;
        if (elapsed < fadeOut) return 1f - (elapsed / fadeOut);
        return 0f;
    }
}