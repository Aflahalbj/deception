package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client: pemain ini lagi pake jas lab atau kagak.
 *
 * <p>Yang dikirim UUID + boolean, BUKAN role-nya: client gak pernah dikasih
 * tau peran siapa pun (itu rahasia game), dia cuma disuruh gambar jas di
 * badan orang tertentu. Kalo nanti role lain juga dapet pakaian, tinggal
 * tambahin jenis pakaiannya di sini.
 */
public class LabCoatPacket {

    private final UUID uuid;
    private final boolean wearing;

    public LabCoatPacket(UUID uuid, boolean wearing) {
        this.uuid = uuid;
        this.wearing = wearing;
    }

    public static void encode(LabCoatPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.uuid);
        buf.writeBoolean(msg.wearing);
    }

    public static LabCoatPacket decode(FriendlyByteBuf buf) {
        return new LabCoatPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(LabCoatPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.deception.init.LabCoatClientState.set(msg.uuid, msg.wearing));
        ctx.setPacketHandled(true);
    }
}
