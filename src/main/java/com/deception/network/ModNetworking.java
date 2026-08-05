package com.deception.network;

import com.deception.DeceptionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Channel networking mod ini. Baru cuma ada 1 packet (BlindfoldStatePacket)
 * tapi struktur-nya sengaja dibikin nampung packet lain kalo nanti butuh.
 */
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
    }

    /** Kirim ke SATU player spesifik (bukan broadcast semua orang). */
    public static void sendBlindfoldState(ServerPlayer player, boolean closing) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlindfoldStatePacket(closing));
    }

    /** Broadcast title+subtitle ke SEMUA client yang connect -- lihat javadoc NightTitlePacket kenapa broadcast. */
    public static void broadcastNightTitle(net.minecraft.network.chat.Component title, net.minecraft.network.chat.Component subtitle,
                                            int fadeIn, int stay, int fadeOut) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new NightTitlePacket(title, subtitle, fadeIn, stay, fadeOut));
    }

    /** Broadcast actionbar ke SEMUA client yang connect. */
    public static void broadcastNightActionBar(net.minecraft.network.chat.Component text) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new NightActionBarPacket(text));
    }
}