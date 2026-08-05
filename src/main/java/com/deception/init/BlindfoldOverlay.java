package com.deception.init;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Overlay POV "kelopak mata" (bukan cuma layar item hitam) buat animasi
 * "tutup mata" pas night sequence (lihat GameManager#startNightSequence).
 * Digambar sebagai 2 kelopak (atas & bawah) yang nutup ke tengah layar,
 * bukan fill solid full-screen -- biar keliatan kayak mata beneran ketutup.
 * Progress-nya dibaca dari BlindfoldClientState -- state ini disinkron dari
 * SERVER lewat BlindfoldStatePacket (bukan baca GameManager langsung, karena
 * project ini jalan pake dedicated server terpisah dari client, jadi gak ada
 * shared memory antara keduanya).
 *
 * SENGAJA GAK PAKE RegisterGuiOverlaysEvent/registerAboveAll lagi -- overlay
 * biasa cuma dirender SEBELUM Screen (chat/inventory itu Screen, dirender
 * abis semua HUD overlay kelar, jadi otomatis nutupin apapun di bawahnya),
 * dan urutan antar overlay lain (ClueHoverOverlay dkk) juga gak dijamin
 * "paling atas". Makanya di sini dipake 2 hook yang emang dijamin PALING
 * TERAKHIR digambar tiap frame:
 *  - RenderGuiEvent.Post -> sekali per frame, abis SEMUA HUD/overlay lain
 *    kelar digambar (kasus normal, gak ada Screen kebuka).
 *  - ScreenEvent.Render.Post -> abis konten Screen (chat/inventory/dll)
 *    kelar digambar, biar tetep di atas walaupun ada Screen yang lagi
 *    kebuka.
 * partialTick di-cache dari RenderGuiEvent.Post (yang jalan tiap frame
 * walaupun ada Screen kebuka) terus dipake ulang di ScreenEvent.Render.Post,
 * soalnya ScreenEvent gak nyediain partialTick sendiri.
 *
 * Selain urutan RENDER PASS (RenderGuiEvent vs ScreenEvent), Minecraft juga
 * pake DEPTH TEST buat GUI, jadi submit-order aja gak cukup buat ngalahin
 * elemen dari render pass yang beda (Gui.render vs Screen.render) -- makanya
 * eyelid didorong ke Z=500 (menang lawan HUD/screen biasa di Z=0), dan
 * title/actionbar (NightTitleClientState) didorong lebih jauh lagi ke
 * Z=1000 (menang lawan eyelid-nya sendiri).
 */
public class BlindfoldOverlay {

    // Warna kelopak: agak terang di deket alis/pipi, makin gelap ke arah
    // garis pertemuan (biar ada kesan depth, gak keliatan cuma bar polos)
    private static final int LID_OUTER = 0xFF3a2a24;
    private static final int LID_INNER = 0xFF120c09;

    private static float lastPartialTick = 1f;

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        lastPartialTick = event.getPartialTick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        draw(event.getGuiGraphics(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), lastPartialTick);
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        draw(event.getGuiGraphics(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), lastPartialTick);
    }

    private static void draw(GuiGraphics guiGraphics, int screenWidth, int screenHeight, float partialTick) {
        float progress = BlindfoldClientState.getProgress(partialTick);
        PoseStack pose = guiGraphics.pose();

        if (progress > 0f) {
            // Dorong Z biar eyelid ini MENANG di depth test ngelawan HUD
            // biasa (hotbar, chat, inventory, dll -- semua di Z=0 default).
            // Ini WAJIB, bukan "jaga-jaga" -- GUI render Minecraft pake
            // depth test, submit-order doang gak cukup buat elemen yang
            // digambar di render pass beda (Gui.render vs Screen.render).
            pose.pushPose();
            pose.translate(0, 0, 500);

            // +4px extra biar pas progress=1 dua kelopak beneran ketemu/overlap
            // dikit, gak nyisain celah tipis di tengah layar.
            int barHeight = Math.round(progress * (screenHeight / 2f + 4f));

            // Kelopak atas: turun dari y=0
            guiGraphics.fillGradient(0, 0, screenWidth, barHeight, LID_OUTER, LID_INNER);
            // Kelopak bawah: naik dari y=screenHeight (cermin dari kelopak atas)
            guiGraphics.fillGradient(0, screenHeight - barHeight, screenWidth, screenHeight, LID_INNER, LID_OUTER);

            // Garis bulu mata tipis, cuma muncul pas kelopak udah hampir ketemu
            // sepenuhnya di tengah -- detail kecil biar berasa "nutup beneran"
            if (progress > 0.9f) {
                int lineAlpha = Math.round((progress - 0.9f) / 0.1f * 255f) & 0xFF;
                int lineColor = (lineAlpha << 24);
                int mid = screenHeight / 2;
                guiGraphics.fill(0, mid - 1, screenWidth, mid + 1, lineColor);
            }

            pose.popPose();
        }

        // Title/actionbar night-phase digambar PERSIS SETELAH eyelid, SELALU
        // (bukan cuma pas progress>0) -- soalnya title kayak "Semua orang
        // bangun" masih perlu keliatan walaupun mata udah kebuka penuh
        // (progress=0) pas lagi fade-out. Z didorong LEBIH JAUH lagi dari
        // eyelid (1000 > 500) biar tetep menang di depth test ngelawan
        // eyelid-nya sendiri.
        pose.pushPose();
        pose.translate(0, 0, 1000);
        NightTitleClientState.draw(guiGraphics, screenWidth, screenHeight, partialTick);
        pose.popPose();
    }
}