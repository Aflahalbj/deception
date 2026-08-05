package com.deception.init;

import com.deception.block.ClueBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD overlay: pas player lagi ngeliat (hover) block clue/means, nama
 * block-nya (translation key "item.deception.<id>", reuse dari
 * ClueBlockItem) muncul di pojok kanan bawah layar. Cuma teks doang, gak
 * ada background, biar gak nutupin HUD lain.
 */
public class ClueHoverOverlay {

    private static final int MARGIN = 6;

    public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || mc.hitResult == null) return;
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
        BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
        if (!(state.getBlock() instanceof ClueBlock clueBlock)) return;

        ItemStack stack = new ItemStack(clueBlock.asItem());
        if (stack.isEmpty()) return;

        Component name = stack.getHoverName();
        int textWidth = mc.font.width(name);
        int x = screenWidth - textWidth - MARGIN;
        int y = screenHeight - mc.font.lineHeight - MARGIN;

        guiGraphics.drawString(mc.font, name, x, y, 0xFFFFFF, true);
    };

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("clue_hover_name", HUD);
    }
}