package com.deception;

import com.deception.command.ModCommands;
import com.deception.entity.ClueEntity;
import com.deception.entity.ModEntities;
import com.deception.game.GameManager;
import com.deception.init.ModItems;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DeceptionMod.MOD_ID)
public class DeceptionMod {

    public static final String MOD_ID = "deception";

    public DeceptionMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAttributes);

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    // wajib ada buat entity yang extends LivingEntity (ClueEntity), kalau
    // enggak game bakal crash pas entity di-spawn ("no attribute supplier")
    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CLUE_ENTITY.get(), ClueEntity.createAttributes().build());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            GameManager.get().tick();
        }
    }
}