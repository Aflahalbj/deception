package com.deception.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Tombol bertexture papan Deception. Satu texture ({@code deception_button.png})
 * dipake buat semua ukuran lewat 9-slice, jadi bingkai logamnya gak pernah
 * ketarik pipih walau tombolnya dilebarin.
 *
 * <p>State yang dirender beda-beda: normal, hover (plat menyala, transisi
 * halus), ditekan (turun 1px + gelap), sama disabled (redup + teks abu).
 * Ekuivalen {@code :hover} / {@code :active} / {@code :disabled} di CSS, cuma
 * ditulis di kode karena GUI Minecraft itu immediate mode.
 */
public class DeceptionButton extends AbstractButton {

    /** Lama transisi hover, detik. Kekecilan = kedip, kegedean = berat. */
    private static final float HOVER_FADE_SECONDS = 0.12F;

    /** Jarak teks dari tepi kiri/kanan tombol. */
    private static final int TEXT_PADDING = 6;

    @FunctionalInterface
    public interface OnPress {
        void onPress(DeceptionButton button);
    }

    private final OnPress onPress;

    /** 0 = normal, 1 = hover penuh. Di-lerp pake waktu asli, bukan tick. */
    private float hoverAnim;
    private long lastFrameMs = Util.getMillis();

    /** true selama tombol kiri ditahan di atas tombol ini. */
    private boolean mouseDown;

    public DeceptionButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    /** Bikin tombol sekalian sama tooltip-nya. */
    public static DeceptionButton withTooltip(int x, int y, int width, int height,
                                              Component message, Component tooltip, OnPress onPress) {
        DeceptionButton button = new DeceptionButton(x, y, width, height, message, onPress);
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    @Override
    public void onPress() {
        this.onPress.onPress(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // partialTick gak bisa dipake buat animasi (nilainya partial tick, bukan
        // delta frame), jadi hitung delta dari jam beneran.
        long now = Util.getMillis();
        float delta = (now - this.lastFrameMs) / 1000.0F;
        this.lastFrameMs = now;

        boolean hot = this.active && this.isHoveredOrFocused();
        float step = delta / HOVER_FADE_SECONDS;
        this.hoverAnim = Mth.clamp(this.hoverAnim + (hot ? step : -step), 0.0F, 1.0F);

        boolean held = this.active && this.mouseDown;
        int drawY = this.getY() + (held ? 1 : 0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        DeceptionTheme.smooth(DeceptionTheme.BUTTON);

        // Plat: hover bikin lebih terang, ditekan bikin lebih gelap, disabled
        // diredupin. Semua lewat shader color, jadi cukup satu texture.
        float tint;
        if (!this.active) {
            tint = 0.60F;
        } else if (held) {
            tint = 0.88F;
        } else {
            tint = 1.0F + 0.20F * this.hoverAnim;
        }
        g.setColor(tint, tint, tint, this.alpha);

        // Bingkai diskalakan sesuai tinggi tombol biar proporsinya persis
        // kayak texture aslinya, berapa pun lebar tombolnya.
        float borderScale = this.height / (float) DeceptionTheme.BUTTON_H;
        DeceptionTheme.nineSlice(g, DeceptionTheme.BUTTON,
                this.getX(), drawY, this.width, this.height,
                DeceptionTheme.BUTTON_W, DeceptionTheme.BUTTON_H,
                DeceptionTheme.BUTTON_INSET_L, DeceptionTheme.BUTTON_INSET_T,
                DeceptionTheme.BUTTON_INSET_R, DeceptionTheme.BUTTON_INSET_B,
                borderScale);

        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        renderLabel(g, drawY);
    }

    private void renderLabel(GuiGraphics g, int drawY) {
        Font font = Minecraft.getInstance().font;
        Component label = this.getMessage();

        int color;
        if (!this.active) {
            color = DeceptionTheme.TEXT_BUTTON_DISABLED;
        } else if (this.hoverAnim > 0.5F) {
            color = DeceptionTheme.TEXT_BUTTON_HOVER;
        } else {
            color = DeceptionTheme.TEXT_BUTTON;
        }
        color = (color & 0x00FFFFFF) | (Mth.ceil(this.alpha * 255.0F) << 24);

        int textY = drawY + (this.height - font.lineHeight) / 2 + 1;
        int available = this.width - TEXT_PADDING * 2;
        int textWidth = font.width(label);

        // Teks gelap di atas plat terang -- shadow-nya dimatiin (drawString
        // param terakhir), kalau enggak hurufnya keliatan kotor.
        if (textWidth <= available) {
            g.drawString(font, label, this.getX() + (this.width - textWidth) / 2, textY, color, false);
        } else {
            // Kepanjangan: potong pake scissor, jangan sampai nabrak bingkai.
            g.enableScissor(this.getX() + TEXT_PADDING, drawY,
                    this.getX() + this.width - TEXT_PADDING, drawY + this.height);
            g.drawString(font, label, this.getX() + TEXT_PADDING, textY, color, false);
            g.disableScissor();
        }
    }

    // Efek "ketekan" dilacak sendiri lewat onClick/onRelease -- jangan
    // andelin isFocused(), itu juga nyala kalau tombolnya kepilih via Tab.
    @Override
    public void onClick(double mouseX, double mouseY) {
        this.mouseDown = true;
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        this.mouseDown = false;
        super.onRelease(mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
