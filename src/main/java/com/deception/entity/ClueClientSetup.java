package com.deception.entity;

import com.deception.DeceptionMod;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registrasi renderer entity clue. Dipisah di FMLClientSetupEvent
 * supaya aman dijalankan di server (dedicated server ga load class renderer).
 */
@Mod.EventBusSubscriber(modid = DeceptionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClueClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> EntityRenderers.register(ModEntities.CLUE_ENTITY.get(), ClueEntityRenderer::new));
    }
}
