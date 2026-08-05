package com.deception.network;

import com.deception.init.BlindfoldClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BlindfoldForceClosePacket {

    public BlindfoldForceClosePacket() {}

    public static void encode(BlindfoldForceClosePacket msg, FriendlyByteBuf buf) {}

    public static BlindfoldForceClosePacket decode(FriendlyByteBuf buf) {
        return new BlindfoldForceClosePacket();
    }

    public static void handle(BlindfoldForceClosePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            BlindfoldClientState.forceClose();
        });
        ctx.setPacketHandled(true);
    }
}