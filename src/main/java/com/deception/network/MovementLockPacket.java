package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> SATU client: kunci/lepas input gerak (WASD, lompat, sneak).
 * Dipake fase shootout biar target gak bisa kabur -- lihat game/PlayerFreeze
 * buat kenapa ini dipasangin sama safety-net server-side.
 */
public class MovementLockPacket {

    private final boolean locked;

    public MovementLockPacket(boolean locked) {
        this.locked = locked;
    }

    public static void encode(MovementLockPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.locked);
    }

    public static MovementLockPacket decode(FriendlyByteBuf buf) {
        return new MovementLockPacket(buf.readBoolean());
    }

    public static void handle(MovementLockPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.deception.init.MovementLockClientState.setLocked(msg.locked));
        ctx.setPacketHandled(true);
    }
}
