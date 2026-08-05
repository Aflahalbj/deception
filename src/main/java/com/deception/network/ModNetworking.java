package com.deception.network;

import com.deception.DeceptionMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    }

    public static void sendBlindfoldState(ServerPlayer player, boolean closing) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlindfoldStatePacket(closing));
    }

    public static void sendForceCloseBlindfold(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlindfoldForceClosePacket());
    }

    public static void broadcastNightTitle(Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new NightTitlePacket(title, subtitle, fadeIn, stay, fadeOut));
    }

    public static void broadcastNightActionBar(Component text) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new NightActionBarPacket(text));
    }
}