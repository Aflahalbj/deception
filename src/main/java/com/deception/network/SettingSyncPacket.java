package com.deception.network;

import com.deception.game.SettingSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: kondisi terkini semua setting.
 *
 * <p>Dipakai dua-duanya buat MEMBUKA GUI (dari /deception setting, {@code open}
 * = true) dan buat NGE-REFRESH GUI yang lagi kebuka abis server nerapin aksi
 * ({@code open} = false). Satu packet buat dua-duanya biar gak ada jalur kode
 * terpisah yang bisa beda isinya.
 */
public class SettingSyncPacket {

    private final SettingSnapshot snapshot;
    private final boolean open;

    public SettingSyncPacket(SettingSnapshot snapshot, boolean open) {
        this.snapshot = snapshot;
        this.open = open;
    }

    public static void encode(SettingSyncPacket msg, FriendlyByteBuf buf) {
        msg.snapshot.encode(buf);
        buf.writeBoolean(msg.open);
    }

    public static SettingSyncPacket decode(FriendlyByteBuf buf) {
        SettingSnapshot snapshot = SettingSnapshot.decode(buf);
        boolean open = buf.readBoolean();
        return new SettingSyncPacket(snapshot, open);
    }

    public static void handle(SettingSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                com.deception.client.gui.ClientSettingState.accept(msg.snapshot, msg.open));
        ctx.setPacketHandled(true);
    }
}
