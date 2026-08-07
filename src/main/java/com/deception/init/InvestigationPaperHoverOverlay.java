package com.deception.init;

import com.deception.block.InvestigationPaperBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD overlay: pas player ngeliat (hover) investigation paper yang UDAH ada
 * jawabannya, "Kategori: Jawaban" muncul di pojok KANAN bawah layar (sama
 * kayak ClueHoverOverlay -- gak akan numpuk soalnya cuma satu block yang
 * bisa di-hover dalam satu waktu).
 */
public class InvestigationPaperHoverOverlay {

    private static final int MARGIN = 6;

    public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || mc.hitResult == null) return;
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
        BlockEntity blockEntity = mc.level.getBlockEntity(blockHit.getBlockPos());
        if (!(blockEntity instanceof InvestigationPaperBlockEntity paperEntity)) return;
        if (paperEntity.getChoice().isEmpty()) return;

        Component text = Component.literal(paperEntity.getCategory() + ": " + paperEntity.getChoice());
        int x = screenWidth - mc.font.width(text) - MARGIN;
        int y = screenHeight - mc.font.lineHeight - MARGIN;

        guiGraphics.drawString(mc.font, text, x, y, 0xFFFFFF, true);
    };

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("investigation_paper_hover_name", HUD);
    }
}
