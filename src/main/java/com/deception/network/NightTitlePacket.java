package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> SEMUA client (broadcast, bukan per-player): title + subtitle
 * buat announcement fase night ("Semua orang tutup mata", "Murderer buka
 * mata", dst). Sengaja di-broadcast ke semua orang (bukan cuma ke role yang
 * bersangkutan) biar semua pemain tau lagi giliran siapa -- sama kayak
 * moderator ngomong keras-keras di party game aslinya, gak nge-reveal SIAPA
 * orangnya, cuma fase-nya doang.
 *
 * Dirender manual di client oleh init/NightTitleClientState + overlay-nya
 * (BUKAN pake vanilla ClientboundSetTitleTextPacket) soalnya kita perlu
 * gambar ini PERSIS SETELAH eyelid blindfold overlay, biar teksnya gak
 * ketutup pas mata lagi "merem" -- vanilla title system gak ngasih kontrol
 * urutan gambar seperti itu.
 */
public class NightTitlePacket {

    private final Component title;
    private final Component subtitle;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;

    public NightTitlePacket(Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        this.title = title;
        this.subtitle = subtitle;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    public static void encode(NightTitlePacket msg, FriendlyByteBuf buf) {
        buf.writeComponent(msg.title);
        buf.writeComponent(msg.subtitle);
        buf.writeVarInt(msg.fadeIn);
        buf.writeVarInt(msg.stay);
        buf.writeVarInt(msg.fadeOut);
    }

    public static NightTitlePacket decode(FriendlyByteBuf buf) {
        Component title = buf.readComponent();
        Component subtitle = buf.readComponent();
        int fadeIn = buf.readVarInt();
        int stay = buf.readVarInt();
        int fadeOut = buf.readVarInt();
        return new NightTitlePacket(title, subtitle, fadeIn, stay, fadeOut);
    }

    public static void handle(NightTitlePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.deception.init.NightTitleClientState.showTitle(
                msg.title, msg.subtitle, msg.fadeIn, msg.stay, msg.fadeOut));
        ctx.setPacketHandled(true);
    }
}