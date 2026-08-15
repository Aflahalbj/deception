package com.deception.init;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * HUD overlay panel "pilihan kamu sekarang", nempel di KANAN-ATAS layar
 * persis di bawah HUD nama role ({@link RoleVisibleOverlay}). Isinya diganti
 * utuh tiap kali server ngirim update (lihat network/SelectionHudPacket),
 * jadi ngeklik berkali-kali gak numpuk.
 *
 * <p>Titik mulainya sama persis sama {@link MurderResultOverlay} dan itu
 * disengaja, bukan kelewatan: panel ini cuma dipegang murderer / lab
 * technician / inside man, sedangkan HUD means-clue itu cuma punya Forensic
 * Scientist -- gak ada satu pemain pun yang bisa kebagian dua-duanya
 * sekaligus, jadi mereka gak akan pernah ketumpuk.
 */
public class SelectionOverlay {

    private static final int MARGIN = RoleVisibleOverlay.MARGIN;
    private static final int PADDING = 4;
    private static final int LINE_GAP = 2;
    private static final int BACKGROUND = 0x90000000;

    public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || SelectionHudState.isEmpty()) return;

        List<Component> lines = SelectionHudState.getLines();
        Component warning = SelectionHudState.getWarning();

        int lineStep = mc.font.lineHeight + LINE_GAP;
        int rowCount = lines.size() + (warning != null ? 1 : 0);
        if (rowCount == 0) return;

        int widest = 0;
        for (Component line : lines) {
            widest = Math.max(widest, mc.font.width(line));
        }
        if (warning != null) {
            widest = Math.max(widest, mc.font.width(warning));
        }

        int boxHeight = rowCount * lineStep - LINE_GAP + PADDING * 2;
        int boxWidth = widest + PADDING * 2;
        int boxX = screenWidth - boxWidth - MARGIN;
        int boxY = RoleVisibleOverlay.contentTop();

        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, BACKGROUND);

        int textX = boxX + PADDING;
        int y = boxY + PADDING;
        for (Component line : lines) {
            guiGraphics.drawString(mc.font, line, textX, y, 0xFFFFFF, true);
            y += lineStep;
        }
        if (warning != null) {
            guiGraphics.drawString(mc.font, warning, textX, y, 0xFFFFFF, true);
        }
    };

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("selection", HUD);
    }
}
