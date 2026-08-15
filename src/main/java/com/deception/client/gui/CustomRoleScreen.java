package com.deception.client.gui;

import com.deception.game.SettingSnapshot;
import com.deception.network.SettingActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Custom Role: nyalain/matiin semua role opsional (Accomplice, Witness, plus
 * tiga role expansion Undercover Allies).
 *
 * <p>Tiap role punya 3 status, bukan 2: AUTO (ikut tabel komposisi berdasar
 * jumlah player), AKTIF, sama NONAKTIF. AUTO itu penting -- kalo cuma
 * on/off, begitu jumlah player berubah komposisinya gak ikut nyesuain lagi.
 * Pas mode AUTO, tombolnya nunjukin nilai yang lagi berlaku sekarang.
 */
public class CustomRoleScreen extends SettingSubScreen {

    /** Bagian kanan baris yang dipake tombol toggle. */
    private static final float TOGGLE_WIDTH = 0.55F;

    private static final Component HINT =
            Component.literal("Klik buat mengubah: Auto -> Aktif -> Nonaktif");

    private static final Component PROTECTIVE_DETAIL_HINT =
            Component.literal("Klik buat mengubah: Auto -> Aktif -> Nonaktif\nCuma jalan kalau Witness aktif.");

    private static final String[] LABELS = {
            "Accomplice", "Witness", "Protective Detail", "Lab Technician", "Inside Man"
    };

    private Grid rows;

    public CustomRoleScreen(Screen parent) {
        super(Component.literal("Custom Role"), parent);
    }

    @Override
    protected void buildSettings(SettingSnapshot snapshot) {
        rows = column(LABELS.length);

        addToggle(0, snapshot.accompliceMode(), snapshot.accompliceAutoValue(),
                SettingActionPacket.Action.CYCLE_ACCOMPLICE, HINT);
        addToggle(1, snapshot.witnessMode(), snapshot.witnessAutoValue(),
                SettingActionPacket.Action.CYCLE_WITNESS, HINT);
        addToggle(2, snapshot.protectiveDetailMode(), snapshot.protectiveDetailAutoValue(),
                SettingActionPacket.Action.CYCLE_PROTECTIVE_DETAIL, PROTECTIVE_DETAIL_HINT);
        addToggle(3, snapshot.labTechnicianMode(), snapshot.labTechnicianAutoValue(),
                SettingActionPacket.Action.CYCLE_LAB_TECHNICIAN, HINT);
        addToggle(4, snapshot.insideManMode(), snapshot.insideManAutoValue(),
                SettingActionPacket.Action.CYCLE_INSIDE_MAN, HINT);
    }

    private void addToggle(int row, SettingSnapshot.Mode mode, boolean autoValue,
                           SettingActionPacket.Action action, Component hint) {
        int width = cw(TOGGLE_WIDTH);
        int x = contentX + contentW - width;

        addRenderableWidget(DeceptionButton.withTooltip(
                x, rows.y(row), width, rows.itemHeight,
                Component.literal(label(mode, autoValue)), hint,
                b -> ClientSettingState.send(action)));
    }

    /** Pas AUTO, sekalian tampilin hasilnya biar gak perlu nebak. */
    private static String label(SettingSnapshot.Mode mode, boolean autoValue) {
        if (mode == SettingSnapshot.Mode.AUTO) {
            return "Auto (" + (autoValue ? "Aktif" : "Nonaktif") + ")";
        }
        return mode == SettingSnapshot.Mode.AKTIF ? "Aktif" : "Nonaktif";
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        SettingSnapshot snapshot = ClientSettingState.get();
        if (snapshot == null || rows == null) return;

        for (int i = 0; i < LABELS.length; i++) {
            drawRowLabel(g, LABELS[i], rows.y(i), rows.itemHeight);
        }

        // Konteks yang bikin mode Auto masuk akal: tabel komposisi itu
        // ditentuin sama jumlah player teregistrasi.
        drawNote(g, "Jumlah player: " + snapshot.players().size());
    }
}
