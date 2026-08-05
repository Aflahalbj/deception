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

            MinecraftForge.EVENT_BUS.register(ClusterHoverOverlay.class);
            MinecraftForge.EVENT_BUS.register(BlindfoldClientState.class);
            MinecraftForge.EVENT_BUS.register(NightTitleClientState.class);
            // BlindfoldOverlay & BlindfoldInputLock GAK pake RegisterGuiOverlaysEvent
            // (lihat javadoc BlindfoldOverlay) -- daftar ke Forge event bus biasa.
            MinecraftForge.EVENT_BUS.register(BlindfoldOverlay.class);
            MinecraftForge.EVENT_BUS.register(BlindfoldInputLock.class);

        });
    }
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        ClueHoverOverlay.register(event);
        ClusterHoverOverlay.register(event);
    }
}