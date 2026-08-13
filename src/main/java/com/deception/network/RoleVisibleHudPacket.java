package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> satu client: role DIA SENDIRI, buat ditempel di pojok kanan atas
 * layar (lihat init/RoleVisibleOverlay). Cuma nyala kalo OP ngidupin
 * /deception rolevisible on.
 *
 * <p>Yang dikirim nama role + flag timnya doang, WARNANYA ditentuin client
 * (lihat RoleVisibleOverlay) -- server gak perlu tau soal warna, dan ini juga
 * bikin gak ada kemungkinan warna beda-beda antar tempat pemanggilan.
 *
 * <p>{@code visible == false} = perintah nge-clear; dua field sisanya
 * diabaikan.
 */
public class RoleVisibleHudPacket {

    private final boolean visible;
    private final String roleName;
    private final boolean evilTeam;

    public RoleVisibleHudPacket(boolean visible, String roleName, boolean evilTeam) {
        this.visible = visible;
        this.roleName = roleName == null ? "" : roleName;
        this.evilTeam = evilTeam;
    }

    /** Perintah nge-clear HUD. */
    public static RoleVisibleHudPacket hidden() {
        return new RoleVisibleHudPacket(false, "", false);
    }

    public static void encode(RoleVisibleHudPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.visible);
        buf.writeUtf(msg.roleName);
        buf.writeBoolean(msg.evilTeam);
    }

    public static RoleVisibleHudPacket decode(FriendlyByteBuf buf) {
        return new RoleVisibleHudPacket(buf.readBoolean(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(RoleVisibleHudPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (msg.visible) {
                com.deception.init.RoleVisibleHudState.show(msg.roleName, msg.evilTeam);
            } else {
                com.deception.init.RoleVisibleHudState.clear();
            }
        });
        ctx.setPacketHandled(true);
    }
}
