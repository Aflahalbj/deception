package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> SEMUA client (broadcast): si {@code uuid} baru aja make police
 * badge-nya ("confession"). Dipake buat mainin animasi pop totem DI SEMUA
 * LAYAR, bukan cuma di layar yang make.
 *
 * Kenapa gak cukup ServerLevel#broadcastEntityEvent(player, 35) doang: event
 * itu emang nyampe ke semua client yang lagi nge-track playernya (partikel +
 * suara jalan), TAPI popup gede item activation-nya di vanilla
 * (LivingEntity#handleEntityEvent) di-guard `if (this == Minecraft
 * .getInstance().player)` -- jadi cuma yang make yang liat. Makanya
 * animasinya kita mainin sendiri lewat packet ini (lihat
 * init/PoliceBadgeUseAnimation), dan broadcastEntityEvent-nya dibuang biar
 * yang make gak dapet popup dobel.
 */
public class PoliceBadgeUsePacket {

    private final UUID uuid;

    public PoliceBadgeUsePacket(UUID uuid) {
        this.uuid = uuid;
    }

    public static void encode(PoliceBadgeUsePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.uuid);
    }

    public static PoliceBadgeUsePacket decode(FriendlyByteBuf buf) {
        return new PoliceBadgeUsePacket(buf.readUUID());
    }

    public static void handle(PoliceBadgeUsePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.deception.init.PoliceBadgeUseAnimation.play(msg.uuid));
        ctx.setPacketHandled(true);
    }
}
