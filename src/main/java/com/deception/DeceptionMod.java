package com.deception;

import java.util.UUID;

import com.deception.command.ModCommands;
import com.deception.game.GameManager;
import com.deception.game.PresentationManager;
import com.deception.init.ClueHoverOverlay;
import com.deception.init.ModBlockEntities;
import com.deception.init.ModBlocks;
import com.deception.init.ModClientSetup;
import com.deception.init.ModItems;
import com.deception.menu.ModMenus;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.network.chat.Component;
import com.deception.game.Role;

@Mod(DeceptionMod.MOD_ID)
public class DeceptionMod {

    public static final String MOD_ID = "deception";

    public DeceptionMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        com.deception.network.ModNetworking.register();

        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModClientSetup::onClientSetup);
        modEventBus.addListener(ModClientSetup::registerOverlays);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
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

    // server mau berhenti (stop command, restart, crash-shutdown, dll) --
    // kalo game lagi jalan, stopgame dulu (restore arena, balikin
    // pvp/difficulty/gamemode) biar gak ninggalin arena dalam kondisi
    // "kepasang" pas server nyala lagi.
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        GameManager.get().forceCleanupOnStartup(event.getServer());
    }
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (GameManager.get().getState() != GameManager.State.IDLE) {
            GameManager.get().stopGame(event.getServer());
        }
    }

    // player rejoin pas game lagi jalan -- kalo dia punya cluster, respawn
    // kepalanya pake GameProfile asli dia yang sekarang online (ganti yang
    // tadinya kepasang pake profile placeholder/skin default pas dia masih
    // offline waktu arena di-build).
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            GameManager.get().refreshOwnerHeadSkin(serverPlayer.getServer(), serverPlayer.getUUID());
            GameManager.get().onPlayerRejoined(serverPlayer.getServer(), serverPlayer);
        }
    }

    // player left di tengah game -- kalo dia lagi "buka mata" (murderer
    // milih item / witness liat killer), kasih tau role terkait & mulai
    // timer 90 detik buat auto-pick/auto-skip (lihat GameManager#onPlayerLeft).
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            GameManager.get().onPlayerLeft(serverPlayer.getServer(), serverPlayer.getUUID());
        }
    }

    // Murderer klik kanan block means/clue (ClueBlock) di cluster dia
    // sendiri -- pilih item asli, backing-nya berubah jadi hijau.
    // Konfirmasi pilihan (murderer) & konfirmasi "selesai melihat" (witness)
    // sekarang lewat /deception confirm, dipicu tombol [KONFIRMASI] di
    // chat -- lihat GameManager#onConfirmCommand & ModCommands.
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // Flint and steel "penghapus scene tile" (ronde 2/3) -- selalu
        // di-cancel begitu ke-detect (lihat javadoc tryRemoveTile), gak
        // pernah dibiarin lanjut ke behavior vanilla (nyalain api dll).
        if (PresentationManager.get().tryRemoveTile(player, level, event.getPos(), player.getItemInHand(event.getHand()))) {
            event.setCanceled(true);
            return;
        }

        if (GameManager.get().onMurdererClickClue(player, level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    // Cegah police_badge di-drop (Q) -- lihat PresentationManager, item ini
    // dikunci di hotbar slot 5 selama diskusi/presentasi. Bagian "gak bisa
    // dipindah/di-swap" ditangani safety-net tiap tick di PresentationManager#tick.
    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (PresentationManager.get().isLockedItem(event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    // Klik kanan police_badge (totem asli yang di-retexture) di udara --
    // trigger flow "confession" ("X ingin menyelesaikan kasus"), lihat
    // PresentationManager#tryStartConfession. Klik kanan bow "Tebak Witness"
    // (murderer, gak ada arrow-nya jadi gak bisa ditembakin) buka UI chest
    // isi head semua player buat nebak witness -- lihat
    // PresentationManager#tryOpenWitnessGuessMenu.
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = player.getItemInHand(event.getHand());

        if (PresentationManager.get().tryStartConfession(player, player.getServer())) {
            event.setCanceled(true);
            return;
        }

        if (PresentationManager.get().isWitnessGuessBow(stack)
                && PresentationManager.get().tryOpenWitnessGuessMenu(player, player.getServer())) {
            event.setCanceled(true);
        }
    }
}