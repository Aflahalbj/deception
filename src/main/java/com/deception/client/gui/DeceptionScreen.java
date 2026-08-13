package com.deception.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Base semua GUI mod ini. Yang dia urus:
 *
 * <ul>
 *   <li>Nempatin papan latar dengan aspect-fit -- rasionya gak pernah gepeng,
 *       ukurannya ngikutin layar (jadi bener di 1080p, 1440p, atau window kecil,
 *       dan di semua GUI Scale).</li>
 *   <li>Nyediain "content box": area kosong di tengah papan yang aman dipake
 *       taruh widget, udah ngehindarin frame kayu, logo, polaroid, sama lentera.</li>
 *   <li>Helper posisi berbasis persen ({@link #cx}, {@link #cy}, {@link #column})
 *       -- ini pengganti flexbox/persentase CSS. Jangan hardcode koordinat
 *       pixel di subclass, pake helper ini biar ikut skala.</li>
 * </ul>
 *
 * <p>Catatan kenapa gak pake {@code LinearLayout} vanilla: di 1.20.1
 * LinearLayout itu "space-between" (dia bagi rata sisa ruang ke seluruh
 * panjang layout), belum ada {@code spacing()} -- itu baru ada di 1.20.2+.
 * {@link #column} di sini ngasih gap tetap yang emang kita mau.
 */
public abstract class DeceptionScreen extends Screen {

    /** Papan latar yang tersedia, lengkap sama area amannya. */
    public enum Board {
        /**
         * Papan lengkap (ada polaroid kiri-kanan). Area amannya cuma kolom
         * tengah yang sempit, soalnya kiri-kanan udah keisi foto.
         */
        DETAILED(DeceptionTheme.BOARD_DETAILED,
                DeceptionTheme.BOARD_DETAILED_W, DeceptionTheme.BOARD_DETAILED_H,
                0.280F, 0.280F, 0.720F, 0.790F),

        /** Papan polos. Hampir seluruh lebar dalam frame bisa dipake. */
        CLEAN(DeceptionTheme.BOARD_CLEAN,
                DeceptionTheme.BOARD_CLEAN_W, DeceptionTheme.BOARD_CLEAN_H,
                0.115F, 0.280F, 0.885F, 0.795F);

        final ResourceLocation texture;
        final int texWidth;
        final int texHeight;
        /** Batas area aman, sebagai fraksi ukuran papan. */
        final float left, top, right, bottom;

        Board(ResourceLocation texture, int texWidth, int texHeight,
              float left, float top, float right, float bottom) {
            this.texture = texture;
            this.texWidth = texWidth;
            this.texHeight = texHeight;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /** Papan ngisi segini dari layar; sisanya nafas di pinggir. */
    private static final float SCREEN_FILL_X = 0.92F;
    private static final float SCREEN_FILL_Y = 0.94F;

    /** Posisi subtitle (nama screen) di bawah logo, fraksi tinggi papan. */
    private static final float SUBTITLE_Y = 0.222F;

    private final Board board;

    /** Kotak papan di koordinat layar. Valid setelah {@link #init()}. */
    protected int panelX, panelY, panelW, panelH;

    /** Kotak area aman di dalam papan. Valid setelah {@link #init()}. */
    protected int contentX, contentY, contentW, contentH;

    protected DeceptionScreen(Component title, Board board) {
        super(title);
        this.board = board;
    }

    @Override
    protected final void init() {
        // Aspect-fit: ambil skala terkecil biar papan muat utuh tanpa gepeng.
        float scale = Math.min(
                this.width * SCREEN_FILL_X / board.texWidth,
                this.height * SCREEN_FILL_Y / board.texHeight);

        this.panelW = Math.round(board.texWidth * scale);
        this.panelH = Math.round(board.texHeight * scale);
        this.panelX = (this.width - panelW) / 2;
        this.panelY = (this.height - panelH) / 2;

        this.contentX = panelX + Math.round(board.left * panelW);
        this.contentY = panelY + Math.round(board.top * panelH);
        this.contentW = Math.round((board.right - board.left) * panelW);
        this.contentH = Math.round((board.bottom - board.top) * panelH);

        buildContent();
    }

    /**
     * Tempat subclass bikin widget-nya. Dipanggil tiap kali screen di-resize,
     * dan {@code panelX/contentX/...} dijamin udah keisi pas dipanggil.
     */
    protected abstract void buildContent();

    // ------------------------------------------------------- helper posisi

    /** Titik X di dalam area konten, {@code f} 0..1 (kayak {@code left: 40%}). */
    protected int cx(float f) {
        return contentX + Math.round(f * contentW);
    }

    /** Titik Y di dalam area konten, {@code f} 0..1 (kayak {@code top: 40%}). */
    protected int cy(float f) {
        return contentY + Math.round(f * contentH);
    }

    /** Lebar sebagai fraksi area konten (kayak {@code width: 60%}). */
    protected int cw(float f) {
        return Math.round(f * contentW);
    }

    /** Tinggi sebagai fraksi area konten (kayak {@code height: 20%}). */
    protected int ch(float f) {
        return Math.round(f * contentH);
    }

    /** Sumbu tengah area konten. */
    protected int centerX() {
        return contentX + contentW / 2;
    }

    /** Tinggi tombol standar. Minimal 16px biar teksnya tetap muat. */
    protected int buttonHeight() {
        return Math.max(16, Math.round(panelH * 0.072F));
    }

    /** Jarak antar item dalam satu kolom. */
    protected int gap() {
        return Math.max(3, Math.round(panelH * 0.026F));
    }

    /** Tinggi tombol paling mepet yang masih kebaca. */
    private static final int MIN_ITEM_HEIGHT = 14;
    private static final int MIN_GAP = 2;

    /**
     * Grid item yang ditaruh di tengah area konten -- ekuivalen
     * {@code display:grid; place-content:center}. Satu kolom = tumpukan
     * vertikal biasa (lihat {@link #column}).
     *
     * <p>Kalau barisnya kebanyakan sampai gak muat, gap sama tinggi item
     * dikecilin otomatis (mirip {@code flex-shrink}), jadi tombolnya gak
     * pernah nembus keluar papan.
     */
    protected final class Grid {
        public final int itemWidth;
        public final int itemHeight;
        public final int columns;
        private final int hGap;
        private final int vGap;
        private final int startY;

        private Grid(int itemWidth, int itemHeight, int columns, int hGap, int vGap, int startY) {
            this.itemWidth = itemWidth;
            this.itemHeight = itemHeight;
            this.columns = columns;
            this.hGap = hGap;
            this.vGap = vGap;
            this.startY = startY;
        }

        /** X item ke-{@code index}. */
        public int x(int index) {
            return contentX + (index % columns) * (itemWidth + hGap);
        }

        /** Y item ke-{@code index}. */
        public int y(int index) {
            return startY + (index / columns) * (itemHeight + vGap);
        }
    }

    /** Tumpukan vertikal (grid 1 kolom) pakai ukuran tombol standar. */
    protected Grid column(int count) {
        return grid(count, 1);
    }

    /** Grid {@code columns} kolom pakai ukuran & gap standar. */
    protected Grid grid(int count, int columns) {
        return grid(count, columns, buttonHeight(), gap(), gap());
    }

    /** Grid dengan ukuran awal sendiri (tetap dikecilin kalau barisnya gak muat). */
    protected Grid grid(int count, int columns, int itemHeight, int hGap, int vGap) {
        columns = Math.max(1, columns);
        int itemWidth = (contentW - (columns - 1) * hGap) / columns;

        if (count <= 0) {
            return new Grid(itemWidth, itemHeight, columns, hGap, vGap, contentY);
        }

        int rows = (count + columns - 1) / columns;
        int needed = rows * itemHeight + (rows - 1) * vGap;
        if (needed > contentH) {
            // Rapetin gap dulu, sisanya baru dipotong dari tinggi item.
            float shrink = contentH / (float) needed;
            vGap = Math.max(MIN_GAP, Math.round(vGap * shrink));
            itemHeight = Math.max(MIN_ITEM_HEIGHT, (contentH - (rows - 1) * vGap) / rows);
            needed = rows * itemHeight + (rows - 1) * vGap;
        }

        int startY = contentY + Math.max(0, (contentH - needed) / 2);
        return new Grid(itemWidth, itemHeight, columns, hGap, vGap, startY);
    }

    /** X kiri buat item selebar {@code itemWidth} yang ditaruh di tengah. */
    protected int centered(int itemWidth) {
        return centerX() - itemWidth / 2;
    }

    /**
     * Potong area konten dari bawah sebanyak {@code px}, buat nyisain tempat
     * elemen yang posisinya tetap (misal tombol Kembali) supaya dia gak ikut
     * kehitung pas {@link #grid} nengahin isinya.
     *
     * <p>Aman dipanggil tiap {@link #buildContent()}: {@code contentH} diitung
     * ulang dari nol di {@link #init()}, jadi potongannya gak numpuk.
     */
    protected void reserveBottom(int px) {
        contentH = Math.max(0, contentH - px);
    }

    /**
     * Bangun ulang semua widget pakai data terbaru. Dipanggil dari handler
     * packet sync abis server nerapin perubahan -- server yang jadi sumber
     * kebenaran, GUI cuma nampilin ulang apa yang dia balikin.
     */
    public void refresh() {
        this.rebuildWidgets();
    }

    // ------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, DeceptionTheme.SCRIM);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        DeceptionTheme.smooth(board.texture);
        DeceptionTheme.blitWhole(g, board.texture, panelX, panelY, panelW, panelH,
                board.texWidth, board.texHeight);

        renderSubtitle(g);
        renderContent(g, mouseX, mouseY, partialTick);

        // Widget + tooltip digambar terakhir biar selalu di atas papan.
        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Nama screen, ditaruh persis di bawah logo DECEPTION dengan gaya
     * "- Setting -" kayak di mockup. Subclass bisa override kalau gak mau.
     */
    protected void renderSubtitle(GuiGraphics g) {
        Component text = Component.literal("- " + this.title.getString() + " -");
        int y = panelY + Math.round(SUBTITLE_Y * panelH);
        g.drawCenteredString(this.font, text, panelX + panelW / 2, y, DeceptionTheme.TEXT_TITLE);
    }

    /** Hook buat subclass yang mau gambar sendiri di atas papan. */
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /**
     * Ini GUI multiplayer -- game GAK boleh ke-pause pas screen kebuka.
     * (Di singleplayer, isPauseScreen() true bakal ngefreeze dunia.)
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
