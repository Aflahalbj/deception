package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> SEMUA client (broadcast): kasih tau siapa yang lagi/gak lagi
 * pegang police_badge. Dipake PoliceBadgeNameTagRenderer buat
 * nampilin/nyembunyiin icon badge di sebelah nametag. Perlu di-broadcast
 * (bukan cukup baca inventory lokal) soalnya slot hotbar player LAIN yang
 * gak lagi dipegang gak ke-sync ke client lain secara default di vanilla.
 */
public class PoliceBadgeHolderPacket {

    private final UUID uuid;
    private final boolean hasBadge;

    public PoliceBadgeHolderPacket(UUID uuid, boolean hasBadge) {
        this.uuid = uuid;
        this.hasBadge = hasBadge;
    }

    public static void encode(PoliceBadgeHolderPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.uuid);
        buf.writeBoolean(msg.hasBadge);
    }

    public static PoliceBadgeHolderPacket decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        boolean hasBadge = buf.readBoolean();
        return new PoliceBadgeHolderPacket(uuid, hasBadge);
    }

    public static void handle(PoliceBadgeHolderPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.deception.init.PoliceBadgeClientState.setHasBadge(msg.uuid, msg.hasBadge));
        ctx.setPacketHandled(true);
    }
}
