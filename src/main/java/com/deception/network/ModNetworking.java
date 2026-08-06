package com.deception.network;

import java.util.UUID;

import com.deception.DeceptionMod;
import com.deception.game.GameManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
}