package com.deception.client.gui;

import com.deception.game.SettingSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base sub-screen setting. Yang diurus di sini cuma hal yang sama buat
 * semuanya: tombol Kembali di bawah, ESC balik ke parent (bukan nutup total),
 * dan jaminan snapshot-nya udah ada sebelum widget dibangun.
 */
public abstract class SettingSubScreen extends DeceptionScreen {

    private final Screen parent;

    /** Lebar tombol Kembali, fraksi area konten. */
    private static final float BACK_WIDTH = 0.45F;

    protected SettingSubScreen(Component title, Screen parent) {
        super(title, Board.CLEAN);
        this.parent = parent;
    }

    @Override
    protected final void buildContent() {
        int backHeight = buttonHeight();
        int backY = contentY + contentH - backHeight;
        int backWidth = cw(BACK_WIDTH);

        // Sisain jalur buat tombol Kembali sebelum isinya ditata, biar isinya
        // ketengah di ruang sisa dan gak numpuk sama tombol.
        reserveBottom(backHeight + gap() * 2);

        // Tombol Kembali dipasang duluan supaya tetap ada walau isinya gagal
        // dibangun -- jangan sampai player kejebak di screen tanpa jalan keluar.
        addRenderableWidget(new DeceptionButton(
                centered(backWidth), backY, backWidth, backHeight,
                Component.literal("Kembali"), b -> onClose()));

        // Sengaja gak manggil onClose() di sini: buildContent() jalan di dalam
        // init(), dan setScreen() dari dalam init() itu reentran. Biarin
        // screen-nya kosong, player tinggal klik Kembali / ESC.
        SettingSnapshot snapshot = ClientSettingState.get();
        if (snapshot != null) {
            buildSettings(snapshot);
        }
    }

    /** Isi khusus sub-screen. Snapshot dijamin gak null. */
    protected abstract void buildSettings(SettingSnapshot snapshot);

    /** Teks kiri pada satu baris, ketengah vertikal terhadap tinggi baris. */
    protected void drawRowLabel(GuiGraphics g, String text, int rowY, int rowHeight) {
        int y = rowY + (rowHeight - this.font.lineHeight) / 2;
        g.drawString(this.font, text, contentX, y, DeceptionTheme.TEXT_TITLE, true);
    }

    /**
     * Teks keterangan kecil, ditaruh persis di atas area konten. Sengaja
     * dipatok ke {@code contentY} (yang gak ikut berubah pas
     * {@link #reserveBottom}) supaya gak pernah nabrak tombol Kembali.
     */
    protected void drawNote(GuiGraphics g, String text) {
        int y = contentY - this.font.lineHeight - 3;
        g.drawCenteredString(this.font, text, centerX(), y, DeceptionTheme.TEXT_MUTED);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
