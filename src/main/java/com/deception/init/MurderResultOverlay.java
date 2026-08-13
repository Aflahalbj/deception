package com.deception.init;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD overlay khusus layar Forensic Scientist: item means (baris atas) &
 * clue (baris bawah) yang dipilih murderer, nempel di KANAN-TENGAH layar.
 * Persisten -- baru ilang pas server ngirim state kosong (game selesai,
 * lihat GameManager#abortGame).
 */
public class MurderResultOverlay {

    private static final int MARGIN = 6;
    private static final int ICON_SIZE = 16;
    private static final int ROW_GAP = 4;
    private static final int NAME_GAP = 4;

    public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || MurderResultHudState.isEmpty()) return;

        int rowHeight = ICON_SIZE + ROW_GAP;
        int iconX = screenWidth - ICON_SIZE - MARGIN;
        // Dua baris dibikin mengapit garis tengah layar: means pas di atasnya,
        // clue pas di bawahnya.
        int meansY = screenHeight / 2 - rowHeight;

        drawRow(mc, guiGraphics, MurderResultHudState.getMeans(), iconX, meansY);
        drawRow(mc, guiGraphics, MurderResultHudState.getClue(), iconX, meansY + rowHeight);
    };

    private static void drawRow(Minecraft mc, GuiGraphics guiGraphics, ItemStack stack, int iconX, int y) {
        if (stack.isEmpty()) return;

        // Nama ditaro di KIRI icon, biar icon-nya yang rata kanan layar dan
        // nama sepanjang apapun tumbuh ke dalem (gak kepotong tepi layar).
        Component name = stack.getHoverName();
        int textX = iconX - mc.font.width(name) - NAME_GAP;
        int textY = y + (ICON_SIZE - mc.font.lineHeight) / 2;
        guiGraphics.drawString(mc.font, name, textX, textY, 0xFFFFFF, true);

        guiGraphics.renderItem(stack, iconX, y);
    }

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("murder_result", HUD);
    }
}
