package com.deception.client.gui;

import com.deception.game.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Detail satu role: foto di atas, penjelasan lengkap di bawahnya, sama tombol
 * {@code <} / {@code >} buat pindah role tanpa balik ke daftar.
 *
 * <p>Tinggi teks beda-beda tiap role dan tiap GUI Scale, jadi area teksnya
 * bisa di-scroll kalau kepanjangan -- bukan dipaksa muat dengan ngecilin
 * fotonya sampai gak kelihatan.
 */
public class RoleDetailScreen extends DeceptionScreen {

    /** Jatah tinggi maksimum buat foto, fraksi area konten. */
    private static final float PHOTO_MAX_HEIGHT = 0.46F;

    /** Bulatnya sudut foto, dalam pixel layar. */
    private static final int PHOTO_CORNER_RADIUS = 6;

    /** Tebal bingkai gelap di belakang foto. */
    private static final int PHOTO_BORDER = 2;

    private static final int SCROLLBAR_WIDTH = 2;

    private final Screen parent;
    private final Role role;
    private final RoleInfo.Entry entry;

    private int photoX, photoY, photoW, photoH;
    private int textTop, textHeight, textTotalHeight;
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;

    public RoleDetailScreen(Screen parent, Role role) {
        super(Component.literal(role.getDisplayName()), Board.CLEAN);
        this.parent = parent;
        this.role = role;
        this.entry = RoleInfo.get(role);
    }

    @Override
    protected void buildContent() {
        int navHeight = buttonHeight();
        int navY = contentY + contentH - navHeight;

        // Baris navigasi dipatok di bawah, sisanya baru dibagi foto & teks.
        reserveBottom(navHeight + gap() * 2);

        layoutPhoto();
        layoutText();
        addNavigation(navY, navHeight);
    }

    /** Aspect-fit foto ke dalam jatahnya, jadi rasio aslinya kejaga. */
    private void layoutPhoto() {
        // Role yang fotonya belum ada (lihat RoleInfo) dirender tanpa kotak
        // foto sama sekali -- teksnya yang dapet seluruh ruang.
        if (entry == null || entry.image() == null) {
            photoW = photoH = 0;
            // Bikin layoutText() mulai persis di contentY: dia ngitung
            // textTop dari photoY + photoH + PHOTO_BORDER, jadi tanpa ini
            // photoY-nya nol dan teksnya nempel di ujung atas layar.
            photoX = contentX;
            photoY = contentY - PHOTO_BORDER;
            return;
        }
        int maxHeight = Math.round(contentH * PHOTO_MAX_HEIGHT);
        int maxWidth = contentW - PHOTO_BORDER * 2;

        photoH = Math.min(maxHeight, Math.round(maxWidth / entry.aspect()));
        photoW = Math.round(photoH * entry.aspect());
        photoX = centered(photoW);
        photoY = contentY + PHOTO_BORDER;
    }

    private void layoutText() {
        textTop = photoY + photoH + PHOTO_BORDER + gap();
        textHeight = Math.max(0, contentY + contentH - textTop);

        lines = wrap(entry == null ? "" : entry.description(), contentW - SCROLLBAR_WIDTH - 2);
        textTotalHeight = lines.size() * lineStep();
        scroll = Mth.clamp(scroll, 0, Math.max(0, textTotalHeight - textHeight));
    }

    /**
     * Bungkus teks per paragraf. Paragraf dipisah sendiri (bukan diserahin ke
     * {@code Font#split}) supaya baris kosong antar paragraf dijamin kejaga --
     * tanpa itu penjelasannya jadi satu blok padat yang berat dibaca.
     */
    private List<FormattedCharSequence> wrap(String text, int width) {
        List<FormattedCharSequence> result = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        for (int i = 0; i < paragraphs.length; i++) {
            if (i > 0) {
                result.add(FormattedCharSequence.EMPTY);
            }
            result.addAll(this.font.split(Component.literal(paragraphs[i].trim()), width));
        }
        return result;
    }

    private int lineStep() {
        return this.font.lineHeight + 1;
    }

    private void addNavigation(int navY, int navHeight) {
        Role[] roles = Role.values();
        int index = role.ordinal();
        Role previous = roles[(index - 1 + roles.length) % roles.length];
        Role next = roles[(index + 1) % roles.length];

        addRenderableWidget(DeceptionButton.withTooltip(
                contentX, navY, navHeight, navHeight,
                Component.literal("<"), Component.literal(previous.getDisplayName()),
                b -> go(previous)));

        int backWidth = cw(0.45F);
        addRenderableWidget(new DeceptionButton(
                centered(backWidth), navY, backWidth, navHeight,
                Component.literal("Kembali"), b -> onClose()));

        addRenderableWidget(DeceptionButton.withTooltip(
                contentX + contentW - navHeight, navY, navHeight, navHeight,
                Component.literal(">"), Component.literal(next.getDisplayName()),
                b -> go(next)));
    }

    private void go(Role target) {
        Minecraft.getInstance().setScreen(new RoleDetailScreen(parent, target));
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPhoto(g);
        renderDescription(g);
    }

    private void renderPhoto(GuiGraphics g) {
        if (entry == null || entry.image() == null || photoW <= 0) return;

        // Bingkai gelap tipis, ikut membulat, biar fotonya kebaca sebagai
        // foto yang ditempel di papan -- bukan gambar yang ngambang.
        DeceptionTheme.fillRounded(g,
                photoX - PHOTO_BORDER, photoY - PHOTO_BORDER,
                photoW + PHOTO_BORDER * 2, photoH + PHOTO_BORDER * 2,
                PHOTO_CORNER_RADIUS + PHOTO_BORDER, 0xE0140F0C);

        DeceptionTheme.smooth(entry.image());
        DeceptionTheme.blitRounded(g, entry.image(),
                photoX, photoY, photoW, photoH,
                entry.imageWidth(), entry.imageHeight(), PHOTO_CORNER_RADIUS);
    }

    private void renderDescription(GuiGraphics g) {
        if (textHeight <= 0 || lines.isEmpty()) return;

        g.enableScissor(contentX, textTop, contentX + contentW, textTop + textHeight);
        int y = textTop - scroll;
        for (FormattedCharSequence line : lines) {
            // Lewatin baris yang udah lewat di atas / belum kelihatan di bawah.
            if (y + lineStep() >= textTop && y <= textTop + textHeight) {
                g.drawString(this.font, line, contentX, y, DeceptionTheme.TEXT_TITLE, true);
            }
            y += lineStep();
        }
        g.disableScissor();

        renderScrollbar(g);
    }

    /** Cuma muncul kalau emang ada yang kepotong -- petunjuk kalau teksnya masih lanjut. */
    private void renderScrollbar(GuiGraphics g) {
        int overflow = textTotalHeight - textHeight;
        if (overflow <= 0) return;

        int trackX = contentX + contentW - SCROLLBAR_WIDTH;
        g.fill(trackX, textTop, trackX + SCROLLBAR_WIDTH, textTop + textHeight, 0x40000000);

        int thumbHeight = Math.max(8, textHeight * textHeight / textTotalHeight);
        int thumbY = textTop + (textHeight - thumbHeight) * scroll / overflow;
        g.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, DeceptionTheme.TEXT_MUTED);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int overflow = textTotalHeight - textHeight;
        if (overflow > 0) {
            scroll = Mth.clamp(scroll - (int) (delta * lineStep() * 2), 0, overflow);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
