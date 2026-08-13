package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: buka GUI /deception inforole. Tanpa payload -- isinya
 * statis di client (lihat RoleInfo), gak ada state game yang perlu dikirim.
 * Command-nya sengaja gak butuh OP: cuma nampilin penjelasan role, sama sekali
 * gak bocorin siapa dapat role apa di game yang lagi jalan.
 */
public class OpenRoleInfoPacket {

    public static void encode(OpenRoleInfoPacket msg, FriendlyByteBuf buf) {
    }

    public static OpenRoleInfoPacket decode(FriendlyByteBuf buf) {
        return new OpenRoleInfoPacket();
    }

    public static void handle(OpenRoleInfoPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.deception.client.gui.RoleInfoScreen.open());
        ctx.setPacketHandled(true);
    }
}
