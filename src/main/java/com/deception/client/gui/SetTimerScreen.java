package com.deception.client.gui;

import com.deception.game.SettingSnapshot;
import com.deception.network.SettingActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Set Timer: durasi diskusi (menit) sama durasi giliran presentasi (detik).
 *
 * <p>Tiap baris bentuknya {@code label ......... [-] nilai [+]}. Nilainya
 * digambar sebagai teks, bukan tombol -- yang bisa diklik cuma yang emang
 * ngubah sesuatu.
 */
public class SetTimerScreen extends SettingSubScreen {

    /** Langkah normal per klik. Tahan Shift buat 5x lipat. */
    private static final int DISCUSS_STEP_MINUTES = 1;
    private static final int PRESENTATION_STEP_SECONDS = 5;
    private static final int SHIFT_MULTIPLIER = 5;

    /** Bagian kanan baris yang dipake kontrol [-] nilai [+]. */
    private static final float CONTROL_WIDTH = 0.46F;

    private static final Component SHIFT_HINT = Component.literal("Tahan SHIFT untuk lompat 5x");

    /** Disimpen dari buildSettings biar renderContent naruh teksnya pas. */
    private Grid rows;

    public SetTimerScreen(Screen parent) {
        super(Component.literal("Set Timer"), parent);
    }

    @Override
    protected void buildSettings(SettingSnapshot snapshot) {
        // 3 baris: diskusi, presentasi, reset.
        rows = column(3);

        addStepper(0, SettingActionPacket.Action.DISCUSS_ADD, DISCUSS_STEP_MINUTES);
        addStepper(1, SettingActionPacket.Action.PRESENTATION_ADD, PRESENTATION_STEP_SECONDS);

        addRenderableWidget(new DeceptionButton(
                rows.x(2), rows.y(2), rows.itemWidth, rows.itemHeight,
                Component.literal("Reset ke Default"),
                b -> ClientSettingState.send(SettingActionPacket.Action.TIMER_RESET)));
    }

    /** Pasang tombol [-] dan [+] di kanan baris ke-{@code row}. */
    private void addStepper(int row, SettingActionPacket.Action action, int step) {
        int y = rows.y(row);
        int size = rows.itemHeight;
        int controlWidth = cw(CONTROL_WIDTH);
        int controlX = contentX + contentW - controlWidth;

        addRenderableWidget(DeceptionButton.withTooltip(
                controlX, y, size, size,
                Component.literal("-"), SHIFT_HINT,
                b -> ClientSettingState.send(action, -step * multiplier())));

        addRenderableWidget(DeceptionButton.withTooltip(
                controlX + controlWidth - size, y, size, size,
                Component.literal("+"), SHIFT_HINT,
                b -> ClientSettingState.send(action, step * multiplier())));
    }

    private int multiplier() {
        return hasShiftDown() ? SHIFT_MULTIPLIER : 1;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        SettingSnapshot snapshot = ClientSettingState.get();
        if (snapshot == null || rows == null) return;

        drawRowLabel(g, "Diskusi", rows.y(0), rows.itemHeight);
        drawRowLabel(g, "Presentasi", rows.y(1), rows.itemHeight);

        drawValue(g, 0, snapshot.discussSeconds() / 60 + " menit");
        drawValue(g, 1, snapshot.presentationSeconds() + " detik");
    }

    /** Nilai sekarang, ketengah di antara tombol [-] dan [+]. */
    private void drawValue(GuiGraphics g, int row, String text) {
        int controlWidth = cw(CONTROL_WIDTH);
        int controlX = contentX + contentW - controlWidth;
        int x = controlX + controlWidth / 2;
        int y = rows.y(row) + (rows.itemHeight - this.font.lineHeight) / 2;
        g.drawCenteredString(this.font, text, x, y, DeceptionTheme.TEXT_TITLE);
    }
}
