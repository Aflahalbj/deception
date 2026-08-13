package com.deception.init;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD overlay: nama role SENDIRI di pojok kanan ATAS layar, nyala kalo OP
 * ngidupin /deception rolevisible on. Warnanya mbedain tim -- biru buat tim
 * baik, merah buat tim jahat (murderer & accomplice).
 *
 * <p>Sengaja di pojok ATAS, bukan tengah: kanan-tengah udah kepake HUD
 * means/clue-nya Forensic Scientist (MurderResultOverlay), dan FS bisa
 * kebagian dua-duanya sekaligus.
 */
public class RoleVisibleOverlay {

    private static final int MARGIN = 6;

    private static final int GOOD_COLOR = ChatFormatting.AQUA.getColor();
    private static final int EVIL_COLOR = ChatFormatting.RED.getColor();

    public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || RoleVisibleHudState.isEmpty()) return;

        Component name = Component.literal(RoleVisibleHudState.getRoleName());
        int color = RoleVisibleHudState.isEvilTeam() ? EVIL_COLOR : GOOD_COLOR;
        int x = screenWidth - mc.font.width(name) - MARGIN;

        guiGraphics.drawString(mc.font, name, x, MARGIN, color, true);
    };

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("role_visible", HUD);
    }
}
