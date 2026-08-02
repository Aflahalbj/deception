package com.deception.init;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Setup client-only. Cuma dipanggil di physical client (FMLClientSetupEvent
 * emang gak pernah fire di dedicated server), jadi aman referensiin class
 * client-only (RenderType dll) di sini.
 */
public class ModClientSetup {

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // cutout biar bagian transparan di texture beneran transparan
            // (bukan solid item, cuma bagian yang non-transparan yang keliatan)
            for (var block : ModBlocks.CLUE_BLOCKS.values()) {
                ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.cutout());
            }
        });
    }
}