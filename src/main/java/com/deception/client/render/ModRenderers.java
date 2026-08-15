package com.deception.client.render;

import com.deception.DeceptionMod;
import com.deception.init.ModBlockEntities;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
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

    // Dua versi jas: lengan normal (Steve, 4 px) & lengan ramping (Alex, 3 px).
    public static final ModelLayerLocation LAB_COAT =
            new ModelLayerLocation(new ResourceLocation(DeceptionMod.MOD_ID, "lab_coat"), "main");
    public static final ModelLayerLocation LAB_COAT_SLIM =
            new ModelLayerLocation(new ResourceLocation(DeceptionMod.MOD_ID, "lab_coat_slim"), "main");

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.HONGKONG_FLAG.get(), HongkongFlagRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAB_COAT, () -> LabCoatModel.createLayer(false));
        event.registerLayerDefinition(LAB_COAT_SLIM, () -> LabCoatModel.createLayer(true));
    }

    /**
     * Renderer pemain ada DUA (skin normal & ramping) dan dua-duanya harus
     * dipasangin layer jas -- kalo cuma satu, jasnya ilang buat separuh
     * pemain tergantung skin mereka.
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new LabCoatLayer(renderer, event.getEntityModels()));
            }
        }
    }
}
