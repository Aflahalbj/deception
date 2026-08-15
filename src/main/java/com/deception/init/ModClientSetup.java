package com.deception.init;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import com.deception.init.ClueHoverOverlay;
import com.deception.init.ClusterHoverOverlay;

/**
 * Setup client-only. Cuma dipanggil di physical client (FMLClientSetupEvent
 * emang gak pernah fire di dedicated server), jadi aman referensiin class
 * client-only (RenderType dll) di sini.
 */
public class ModClientSetup {

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {

            for (var block : ModBlocks.CLUE_BLOCKS.values()) {
                ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.cutout());
            }
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.INVESTIGATION_PAPER.get(), RenderType.cutout());
            // Sama kayak lantern vanilla -- modelnya punya bagian tembus
            // pandang (rantai/bingkai), bakal item hitam kalo di layer solid.
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.DEAD_LANTERN.get(), RenderType.cutout());

            MinecraftForge.EVENT_BUS.register(ClusterHoverOverlay.class);
            MinecraftForge.EVENT_BUS.register(BlindfoldClientState.class);
            MinecraftForge.EVENT_BUS.register(NightTitleClientState.class);
            // BlindfoldOverlay & BlindfoldInputLock GAK pake RegisterGuiOverlaysEvent
            // (lihat javadoc BlindfoldOverlay) -- daftar ke Forge event bus biasa.
            MinecraftForge.EVENT_BUS.register(BlindfoldOverlay.class);
            MinecraftForge.EVENT_BUS.register(BlindfoldInputLock.class);
            MinecraftForge.EVENT_BUS.register(PoliceBadgeNameTagRenderer.class);
            MinecraftForge.EVENT_BUS.register(MovementLockClientState.class);
            // Butuh tick client sendiri buat ngitung umur baris penolakan.
            MinecraftForge.EVENT_BUS.register(SelectionHudState.class);
            // Kunci kamera pas cutscene intro -- perlu hook tick + frame
            // sendiri, lihat javadoc CutsceneClientState.
            MinecraftForge.EVENT_BUS.register(CutsceneClientState.class);

        });
    }
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        ClueHoverOverlay.register(event);
        ClusterHoverOverlay.register(event);
        InvestigationPaperHoverOverlay.register(event);
        MurderResultOverlay.register(event);
        RoleVisibleOverlay.register(event);
        SelectionOverlay.register(event);
        CutsceneOverlay.register(event);
    }
}