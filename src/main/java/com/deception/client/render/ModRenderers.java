package com.deception.client.render;

import com.deception.DeceptionMod;
import com.deception.init.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Daftarin BlockEntityRenderer. Sengaja lewat @EventBusSubscriber
 * value=Dist.CLIENT (bukan ditambahin manual dari constructor DeceptionMod)
 * biar class renderer-nya gak pernah ke-load sama sekali di dedicated
 * server -- di sana class render-nya emang gak ada.
 */
@Mod.EventBusSubscriber(modid = DeceptionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModRenderers {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.HONGKONG_FLAG.get(), HongkongFlagRenderer::new);
    }
}
