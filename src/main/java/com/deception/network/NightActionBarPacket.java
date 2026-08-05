package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> SEMUA client (broadcast): teks actionbar buat announcement fase
 * night ("Menunggu murderer memilih item" dst). PERSISTEN di client (gak
 * ada auto-fade timer, animasi titik jalan dihitung sendiri di client) --
 * kirim SEKALI pas fase mulai, lalu kirim lagi dengan Component KOSONG buat
 * nge-clear pas fase-nya abis (lihat NightTitleClientState#clearActionBar).
 */
public class NightActionBarPacket {

    private final Component text;

    public NightActionBarPacket(Component text) {
        this.text = text;
    }

    public static void encode(NightActionBarPacket msg, FriendlyByteBuf buf) {
        buf.writeComponent(msg.text);
    }

    public static NightActionBarPacket decode(FriendlyByteBuf buf) {
        return new NightActionBarPacket(buf.readComponent());
    }

    public static void handle(NightActionBarPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (msg.text.getString().isEmpty()) {
                com.deception.init.NightTitleClientState.clearActionBar();
            } else {
                com.deception.init.NightTitleClientState.showPersistentActionBar(msg.text);
            }
        });
        ctx.setPacketHandled(true);
    }
}