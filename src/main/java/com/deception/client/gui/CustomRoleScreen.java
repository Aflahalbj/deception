package com.deception.client.gui;

import com.deception.game.SettingSnapshot;
import com.deception.network.SettingActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Custom Role: nyalain/matiin Accomplice sama Witness.
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

    private Grid rows;

    public CustomRoleScreen(Screen parent) {
        super(Component.literal("Custom Role"), parent);
    }

    @Override
    protected void buildSettings(SettingSnapshot snapshot) {
        rows = column(2);

        addToggle(0, snapshot.accompliceMode(), snapshot.accompliceAutoValue(),
                SettingActionPacket.Action.CYCLE_ACCOMPLICE);
        addToggle(1, snapshot.witnessMode(), snapshot.witnessAutoValue(),
                SettingActionPacket.Action.CYCLE_WITNESS);
    }

    private void addToggle(int row, SettingSnapshot.Mode mode, boolean autoValue,
                           SettingActionPacket.Action action) {
        int width = cw(TOGGLE_WIDTH);
        int x = contentX + contentW - width;

        addRenderableWidget(DeceptionButton.withTooltip(
                x, rows.y(row), width, rows.itemHeight,
                Component.literal(label(mode, autoValue)), HINT,
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

        drawRowLabel(g, "Accomplice", rows.y(0), rows.itemHeight);
        drawRowLabel(g, "Witness", rows.y(1), rows.itemHeight);

        // Konteks yang bikin mode Auto masuk akal: tabel komposisi itu
        // ditentuin sama jumlah player teregistrasi.
        drawNote(g, " ");
        drawNote(g, "Jumlah player: " + snapshot.players().size());
    }
}
