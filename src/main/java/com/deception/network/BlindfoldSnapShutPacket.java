package com.deception.network;

import com.deception.init.BlindfoldClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client (per-player): dipake pas player rejoin di tengah night
 * dan harusnya masih tutup mata. Client yang baru konek gak punya state
 * animasi apa-apa (BlindfoldClientState fresh), jadi daripada ngulang
 * animasi nutup dari awal, langsung "snap" ke kondisi tertutup penuh.
 */
public class BlindfoldSnapShutPacket {

    public BlindfoldSnapShutPacket() {}

    public static void encode(BlindfoldSnapShutPacket msg, FriendlyByteBuf buf) {}

    public static BlindfoldSnapShutPacket decode(FriendlyByteBuf buf) {
        return new BlindfoldSnapShutPacket();
    }

    public static void handle(BlindfoldSnapShutPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            BlindfoldClientState.snapShut();
        });
        ctx.setPacketHandled(true);
    }
}