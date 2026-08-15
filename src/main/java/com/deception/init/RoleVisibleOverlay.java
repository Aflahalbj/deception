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
 * <p>Ini HUD paling atas di sisi kanan; yang lain (means/clue-nya Forensic
 * Scientist di {@link MurderResultOverlay}, panel pilihan di
 * {@link SelectionOverlay}) numpuk di bawahnya lewat {@link #contentTop()}.
 */
public class RoleVisibleOverlay {

    public static final int MARGIN = 6;

    /** Jarak antara nama role sama HUD yang nempel di bawahnya. */
    private static final int GAP = 4;

    private static final int GOOD_COLOR = ChatFormatting.AQUA.getColor();
    private static final int EVIL_COLOR = ChatFormatting.RED.getColor();

    /**
     * Y teratas yang aman dipake HUD lain di sisi kanan: persis di bawah nama
     * role, atau nempel di tepi atas kalau HUD role-nya lagi mati
     * (/deception rolevisible off) -- biar gak nyisain lubang kosong.
     */
    public static int contentTop() {
        if (RoleVisibleHudState.isEmpty()) return MARGIN;
        return MARGIN + Minecraft.getInstance().font.lineHeight + GAP;
    }

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
