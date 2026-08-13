package com.deception.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Satu-satunya tempat definisi look & feel GUI mod ini: texture, ukuran
 * texture, tebal bevel, sama palet warna. Ganti tema = edit file ini doang.
 *
 * <p>Di sini juga ada helper gambar yang GAK ada di vanilla:
 * {@link #nineSlice} -- versi 9-slice yang ngehargain ukuran texture asli.
 * {@code GuiGraphics#blitNineSliced} vanilla hardcode sheet 256x256 (lihat
 * GuiGraphics#blit(ResourceLocation,int,int,int,int,int,int)), jadi gak bisa
 * dipake buat texture kita yang 676x212.
 */
public final class DeceptionTheme {

    private DeceptionTheme() {}

    private static final String MODID = "deception";

    // ---------------------------------------------------------------- texture

    /** Papan versi lengkap: ada polaroid, peta, foto TKP. Buat menu utama. */
    public static final ResourceLocation BOARD_DETAILED =
            new ResourceLocation(MODID, "textures/gui/deception_tiles.png");
    public static final int BOARD_DETAILED_W = 473;
    public static final int BOARD_DETAILED_H = 527;

    /** Papan versi polos: cuma frame kayu + dinding. Buat sub-screen. */
    public static final ResourceLocation BOARD_CLEAN =
            new ResourceLocation(MODID, "textures/gui/deception_tiles_clear.png");
    public static final int BOARD_CLEAN_W = 470;
    public static final int BOARD_CLEAN_H = 531;

    /** Plat tombol batu berbingkai logam. */
    public static final ResourceLocation BUTTON =
            new ResourceLocation(MODID, "textures/gui/deception_button.png");
    public static final int BUTTON_W = 676;
    public static final int BUTTON_H = 212;

    /**
     * Tebal bingkai tombol dalam pixel texture asli -- diukur dari PNG-nya
     * (bagian datar interior mulai sekitar x=20/x=655, y=16/y=191). Bagian
     * inilah yang GAK boleh ikut melar pas tombolnya dilebarin.
     */
    public static final int BUTTON_INSET_L = 24;
    public static final int BUTTON_INSET_T = 20;
    public static final int BUTTON_INSET_R = 24;
    public static final int BUTTON_INSET_B = 24;

    // ------------------------------------------------------------------ warna

    /** Teks tombol keadaan normal -- gelap, biar kebaca di atas plat batu. */
    public static final int TEXT_BUTTON = 0xFF2C231D;
    /** Teks tombol pas di-hover -- lebih pekat dikit, platnya yang menyala. */
    public static final int TEXT_BUTTON_HOVER = 0xFF171008;
    /** Teks tombol pas disabled. */
    public static final int TEXT_BUTTON_DISABLED = 0xFF6E6862;

    /** Judul / heading di atas papan. */
    public static final int TEXT_TITLE = 0xFFE8DCC8;
    /** Teks sekunder, keterangan, subtitle. */
    public static final int TEXT_MUTED = 0xFFA19486;
    /** Aksen merah khas Deception (huruf "E" di logo). */
    public static final int ACCENT = 0xFFB03A2E;

    /** Gelapin layar di belakang papan. */
    public static final int SCRIM = 0xC0000000;

    // ---------------------------------------------------------------- helper

    /**
     * Nyalain filter linear buat satu texture. Wajib buat texture kita yang
     * resolusinya jauh lebih gede dari ukuran render (676px -> ~160px): tanpa
     * ini Minecraft pake nearest-neighbour dan bevel-nya keliatan pecah.
     *
     * <p>Dipanggil tiap frame (bukan sekali di init) karena setting filter
     * ke-reset tiap resource reload (F3+T).
     */
    public static void smooth(ResourceLocation texture) {
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
    }

    /**
     * Gambar texture utuh, di-stretch ke kotak tujuan. Beda sama
     * {@code GuiGraphics#blit} yang pendek: yang ini ngasih tau ukuran sheet
     * asli, jadi UV-nya bener buat texture non-256x256.
     */
    public static void blitWhole(GuiGraphics g, ResourceLocation tex,
                                 int x, int y, int w, int h, int texW, int texH) {
        g.blit(tex, x, y, w, h, 0.0F, 0.0F, texW, texH, texW, texH);
    }

    /**
     * 9-slice: 4 sudut digambar utuh, 4 sisi melar satu arah, tengah melar dua
     * arah. Bikin satu texture tombol kepake di lebar/tinggi apa pun tanpa
     * bingkainya ketarik pipih.
     *
     * @param borderScale skala bingkai dari pixel texture ke pixel layar.
     *                    Umumnya {@code tinggiTujuan / (float) texH} biar
     *                    proporsinya sama persis kayak texture aslinya.
     */
    public static void nineSlice(GuiGraphics g, ResourceLocation tex,
                                 int x, int y, int w, int h,
                                 int texW, int texH,
                                 int insL, int insT, int insR, int insB,
                                 float borderScale) {
        if (w <= 0 || h <= 0) return;

        // Bingkai gak boleh lebih tebal dari setengah tombol, kalau enggak
        // sisi kiri & kanan bakal tumpang tindih pas tombolnya kecil banget.
        int l = Math.min(Math.round(insL * borderScale), w / 2);
        int r = Math.min(Math.round(insR * borderScale), w / 2);
        int t = Math.min(Math.round(insT * borderScale), h / 2);
        int b = Math.min(Math.round(insB * borderScale), h / 2);

        int srcMidW = texW - insL - insR;
        int srcMidH = texH - insT - insB;
        int dstMidW = w - l - r;
        int dstMidH = h - t - b;

        int x1 = x + w - r;
        int y1 = y + h - b;
        int u1 = texW - insR;
        int v1 = texH - insB;

        // sudut
        part(g, tex, x,  y,  l, t, 0,  0,  insL, insT, texW, texH);
        part(g, tex, x1, y,  r, t, u1, 0,  insR, insT, texW, texH);
        part(g, tex, x,  y1, l, b, 0,  v1, insL, insB, texW, texH);
        part(g, tex, x1, y1, r, b, u1, v1, insR, insB, texW, texH);

        // sisi atas & bawah
        part(g, tex, x + l, y,  dstMidW, t, insL, 0,  srcMidW, insT, texW, texH);
        part(g, tex, x + l, y1, dstMidW, b, insL, v1, srcMidW, insB, texW, texH);

        // sisi kiri & kanan
        part(g, tex, x,  y + t, l, dstMidH, 0,  insT, insL, srcMidH, texW, texH);
        part(g, tex, x1, y + t, r, dstMidH, u1, insT, insR, srcMidH, texW, texH);

        // tengah
        part(g, tex, x + l, y + t, dstMidW, dstMidH, insL, insT, srcMidW, srcMidH, texW, texH);
    }

    private static void part(GuiGraphics g, ResourceLocation tex,
                             int x, int y, int w, int h,
                             int u, int v, int uw, int vh, int texW, int texH) {
        if (w <= 0 || h <= 0) return;
        g.blit(tex, x, y, w, h, (float) u, (float) v, uw, vh, texW, texH);
    }

    // ------------------------------------------------------ sudut membulat

    /**
     * Gambar texture penuh tapi keempat sudutnya dibulatkan.
     *
     * <p>Caranya: gambar yang sama di-blit berkali-kali, tiap kali dibatasin
     * scissor selebar satu baris, dan lebar baris di area sudut dipotong
     * ngikutin lengkung lingkaran. Dipilih daripada ngitung UV manual karena
     * scissor motongnya eksak -- gak ada pembulatan pixel yang bikin sudutnya
     * kelihatan bergerigi. Sisa sudutnya tembus pandang, jadi tekstur papan di
     * belakangnya tetap kelihatan.
     */
    public static void blitRounded(GuiGraphics g, ResourceLocation tex,
                                   int x, int y, int w, int h,
                                   int texW, int texH, int radius) {
        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (radius == 0) {
            blitWhole(g, tex, x, y, w, h, texW, texH);
            return;
        }

        // Badan tengah: gak ada yang dipotong.
        clipped(g, tex, x, y, w, h, texW, texH, x, y + radius, x + w, y + h - radius);

        // Baris-baris di area sudut, atas dan bawah sekaligus (simetris).
        for (int i = 0; i < radius; i++) {
            int inset = cornerInset(radius, i);
            clipped(g, tex, x, y, w, h, texW, texH,
                    x + inset, y + i, x + w - inset, y + i + 1);
            clipped(g, tex, x, y, w, h, texW, texH,
                    x + inset, y + h - i - 1, x + w - inset, y + h - i);
        }
    }

    /** Kotak warna solid dengan sudut membulat -- buat bingkai di belakang foto. */
    public static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        g.fill(x, y + radius, x + w, y + h - radius, color);

        for (int i = 0; i < radius; i++) {
            int inset = cornerInset(radius, i);
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /**
     * Seberapa dalam baris ke-{@code row} (dihitung dari tepi) harus dipotong
     * biar ngikutin lingkaran berjari-jari {@code radius}. Pakai titik tengah
     * baris (+0.5) supaya lengkungnya gak miring sebelah.
     */
    private static int cornerInset(int radius, int row) {
        double dy = radius - row - 0.5;
        return (int) Math.round(radius - Math.sqrt(radius * radius - dy * dy));
    }

    private static void clipped(GuiGraphics g, ResourceLocation tex,
                                int x, int y, int w, int h, int texW, int texH,
                                int clipX0, int clipY0, int clipX1, int clipY1) {
        if (clipX1 <= clipX0 || clipY1 <= clipY0) return;
        g.enableScissor(clipX0, clipY0, clipX1, clipY1);
        blitWhole(g, tex, x, y, w, h, texW, texH);
        g.disableScissor();
    }
}
