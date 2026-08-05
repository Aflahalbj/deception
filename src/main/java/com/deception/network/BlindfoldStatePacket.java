package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client (per-player, bukan broadcast): kasih tau client kalo
 * blindfold PLAYER INI mulai nutup atau mulai buka. Client nyimpen ini di
 * BlindfoldClientState terus jalanin animasinya sendiri pake tick CLIENT
 * (lihat class itu buat kurva "kedip dulu baru nutup beneran"). Dikirim
 * dari GameManager#putBlindfold / #removeBlindfold.
 *
 * Ini WAJIB pake packet (bukan baca GameManager langsung dari client) kalo
 * server & client jalan di JVM/proses terpisah (dedicated server) --
 * GameManager di client itu instance yang BEDA dari yang di server, jadi
 * gak ada state yang otomatis "nyambung" tanpa dikirim eksplisit.
 */
public class BlindfoldStatePacket {

    private final boolean closing;

    public BlindfoldStatePacket(boolean closing) {
        this.closing = closing;
    }

    public static void encode(BlindfoldStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.closing);
    }

    public static BlindfoldStatePacket decode(FriendlyByteBuf buf) {
        return new BlindfoldStatePacket(buf.readBoolean());
    }

    public static void handle(BlindfoldStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (msg.closing) {
                com.deception.init.BlindfoldClientState.startTransition(true);
            } else {
                com.deception.init.BlindfoldClientState.startTransition(false);
            }
        });
        ctx.setPacketHandled(true);
    }
}