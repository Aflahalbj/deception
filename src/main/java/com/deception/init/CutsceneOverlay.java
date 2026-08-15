package com.deception.init;

import com.deception.DeceptionMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Gambar intro cutscene di TENGAH layar (bukan full-screen -- sengaja cuma
 * separuh lebar layar biar arena di belakangnya tetep keliatan). Muncul pas
 * server ngirim CutscenePacket dengan showImage=true, lihat
 * GameManager#tickCutscene.
 */
public class CutsceneOverlay {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(DeceptionMod.MOD_ID, "textures/gui/intro_text.png");

    // Ukuran asli file-nya -- blit butuh angka ini buat ngitung UV.
    private static final int TEX_WIDTH = 2172;
    private static final int TEX_HEIGHT = 724;

    private static final float WIDTH_RATIO = 0.55F;      // lebar gambar relatif ke lebar layar
    private static final float MAX_HEIGHT_RATIO = 0.35F; // batas tinggi, buat layar yang pendek
    private static final long FADE_IN_MS = 600L;

    public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (!CutsceneClientState.isShowingImage()) return;
        if (Minecraft.getInstance().level == null) return;

        int width = Math.round(screenWidth * WIDTH_RATIO);
        int height = width * TEX_HEIGHT / TEX_WIDTH;

        int maxHeight = Math.round(screenHeight * MAX_HEIGHT_RATIO);
        if (height > maxHeight) {
            height = maxHeight;
            width = height * TEX_WIDTH / TEX_HEIGHT;
        }

        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;

        long shownFor = System.currentTimeMillis() - CutsceneClientState.imageShownAt();
        float alpha = Math.min(1.0F, Math.max(0.0F, shownFor / (float) FADE_IN_MS));

        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        // Versi blit yang dikasih tau ukuran sheet aslinya -- yang pendek
        // hardcode 256x256 dan bakal motong gambar ini (lihat DeceptionTheme).
        guiGraphics.blit(TEXTURE, x, y, width, height, 0.0F, 0.0F, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    };

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("cutscene_image", HUD);
    }
}
