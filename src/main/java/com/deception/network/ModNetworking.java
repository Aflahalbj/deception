package com.deception.network;

import java.util.List;
import java.util.UUID;

import com.deception.DeceptionMod;
import com.deception.game.GameManager;
import com.deception.game.SettingSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetworking {

    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DeceptionMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        CHANNEL.registerMessage(packetId++, BlindfoldStatePacket.class,
                BlindfoldStatePacket::encode,
                BlindfoldStatePacket::decode,
                BlindfoldStatePacket::handle);
        CHANNEL.registerMessage(packetId++, NightTitlePacket.class,
                NightTitlePacket::encode,
                NightTitlePacket::decode,
                NightTitlePacket::handle);
        CHANNEL.registerMessage(packetId++, NightActionBarPacket.class,
                NightActionBarPacket::encode,
                NightActionBarPacket::decode,
                NightActionBarPacket::handle);
        CHANNEL.registerMessage(packetId++, BlindfoldForceClosePacket.class,
                BlindfoldForceClosePacket::encode,
                BlindfoldForceClosePacket::decode,
                BlindfoldForceClosePacket::handle);
        CHANNEL.registerMessage(packetId++, BlindfoldSnapShutPacket.class,
                BlindfoldSnapShutPacket::encode,
                BlindfoldSnapShutPacket::decode,
                BlindfoldSnapShutPacket::handle);
        CHANNEL.registerMessage(packetId++, OpenForensicPickerPacket.class,
                OpenForensicPickerPacket::encode,
                OpenForensicPickerPacket::decode,
                OpenForensicPickerPacket::handle);
        CHANNEL.registerMessage(packetId++, ChooseForensicOptionPacket.class,
                ChooseForensicOptionPacket::encode,
                ChooseForensicOptionPacket::decode,
                ChooseForensicOptionPacket::handle);
        CHANNEL.registerMessage(packetId++, PoliceBadgeHolderPacket.class,
                PoliceBadgeHolderPacket::encode,
                PoliceBadgeHolderPacket::decode,
                PoliceBadgeHolderPacket::handle);
        CHANNEL.registerMessage(packetId++, PoliceBadgeUsePacket.class,
                PoliceBadgeUsePacket::encode,
                PoliceBadgeUsePacket::decode,
                PoliceBadgeUsePacket::handle);
        CHANNEL.registerMessage(packetId++, SettingSyncPacket.class,
                SettingSyncPacket::encode,
                SettingSyncPacket::decode,
                SettingSyncPacket::handle);
        CHANNEL.registerMessage(packetId++, SettingActionPacket.class,
                SettingActionPacket::encode,
                SettingActionPacket::decode,
                SettingActionPacket::handle);
        CHANNEL.registerMessage(packetId++, OpenRoleInfoPacket.class,
                OpenRoleInfoPacket::encode,
                OpenRoleInfoPacket::decode,
                OpenRoleInfoPacket::handle);
        CHANNEL.registerMessage(packetId++, MovementLockPacket.class,
                MovementLockPacket::encode,
                MovementLockPacket::decode,
                MovementLockPacket::handle);
        CHANNEL.registerMessage(packetId++, MurderResultHudPacket.class,
                MurderResultHudPacket::encode,
                MurderResultHudPacket::decode,
                MurderResultHudPacket::handle);
        CHANNEL.registerMessage(packetId++, RoleVisibleHudPacket.class,
                RoleVisibleHudPacket::encode,
                RoleVisibleHudPacket::decode,
                RoleVisibleHudPacket::handle);
        CHANNEL.registerMessage(packetId++, SelectionHudPacket.class,
                SelectionHudPacket::encode,
                SelectionHudPacket::decode,
                SelectionHudPacket::handle);
        CHANNEL.registerMessage(packetId++, CutscenePacket.class,
                CutscenePacket::encode,
                CutscenePacket::decode,
                CutscenePacket::handle);
    }

    public static void sendBlindfoldState(ServerPlayer player, boolean closing) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlindfoldStatePacket(closing));
    }

    public static void sendForceCloseBlindfold(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlindfoldForceClosePacket());
    }

    public static void sendSnapShutBlindfold(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlindfoldSnapShutPacket());
    }

    public static void broadcastNightTitle(Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new NightTitlePacket(title, subtitle, fadeIn, stay, fadeOut));
    }

    // Perbaiki method broadcastNightActionBar agar mengirim ke semua player yang online
    public static void broadcastNightActionBar(Component message) {
        // Gunakan broadcast ke semua player via packet
        CHANNEL.send(PacketDistributor.ALL.noArg(), new NightActionBarPacket(message));
    }

    // Kirim actionbar custom (persisten, non vanilla) ke SATU player aja --
    // dipake pas sinkronisasi rejoin (syncActionBar), karena actionbar yang
    // keliatan di layar itu overlay custom NightTitleClientState, BUKAN
    // ClientboundSetActionBarTextPacket vanilla. Ngirim lewat vanilla gak
    // bakal nge-update overlay custom-nya, makanya sebelumnya keliatan stale.
    public static void sendNightActionBarTo(ServerPlayer player, Component message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NightActionBarPacket(message));
    }

    public static void sendOpenForensicPicker(ServerPlayer player, String category, List<String> options, InteractionHand hand) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenForensicPickerPacket(category, options, hand));
    }

    public static void broadcastPoliceBadgeHolder(UUID uuid, boolean hasBadge) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new PoliceBadgeHolderPacket(uuid, hasBadge));
    }

    /** Sinkron ulang icon badge ke SATU client -- dipake pas rejoin, lihat PresentationManager#onPlayerRejoined. */
    public static void sendPoliceBadgeHolder(ServerPlayer player, UUID uuid, boolean hasBadge) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PoliceBadgeHolderPacket(uuid, hasBadge));
    }

    /** Animasi pop badge di SEMUA layar -- lihat javadoc PoliceBadgeUsePacket. */
    public static void broadcastPoliceBadgeUse(UUID uuid) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new PoliceBadgeUsePacket(uuid));
    }

    /** Buka GUI /deception setting di client, sekalian kirim kondisi awalnya. */
    public static void sendOpenSetting(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SettingSyncPacket(SettingSnapshot.capture(), true));
    }

    /** Kirim kondisi terbaru ke GUI yang lagi kebuka (tanpa buka ulang). */
    public static void sendSettingState(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SettingSyncPacket(SettingSnapshot.capture(), false));
    }

    /** Kunci/lepas input gerak player ini -- lihat game/PlayerFreeze. */
    public static void sendMovementLock(ServerPlayer player, boolean locked) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MovementLockPacket(locked));
    }

    /** Buka GUI /deception inforole di client. */
    public static void sendOpenRoleInfo(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenRoleInfoPacket());
    }

    /** Tempel hasil malam (means & clue pilihan murderer) di HUD-nya FS. */
    public static void sendMurderResultHud(ServerPlayer player, ItemStack means, ItemStack clue) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MurderResultHudPacket(means, clue));
    }

    /** Copot HUD hasil malam dari layar player ini (dipanggil pas game selesai). */
    public static void clearMurderResultHud(ServerPlayer player) {
        sendMurderResultHud(player, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    /** HUD role sendiri di pojok kanan atas -- lihat GameManager#sendRoleVisibleHud. */
    public static void sendRoleVisibleHud(ServerPlayer player, RoleVisibleHudPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Panel pilihan di kiri-tengah layar -- lihat init/SelectionOverlay. */
    public static void sendSelectionHud(ServerPlayer player, SelectionHudPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Copot panel pilihan dari layar player ini. */
    public static void clearSelectionHud(ServerPlayer player) {
        sendSelectionHud(player, SelectionHudPacket.hidden());
    }

    /** Kunci kamera + gambar intro cutscene -- lihat GameManager#startCutscene. */
    public static void sendCutscene(ServerPlayer player, boolean active, float yaw, float pitch, boolean showImage) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CutscenePacket(active, yaw, pitch, showImage));
    }

    /** Bersihin state cutscene di layar player ini (kamera bebas lagi, gambar ilang). */
    public static void clearCutscene(ServerPlayer player) {
        sendCutscene(player, false, 0F, 0F, false);
    }
}