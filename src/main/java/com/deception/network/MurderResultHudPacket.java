package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client-nya FORENSIC SCIENTIST doang: item means & clue yang
 * dipilih murderer, buat ditempel PERSISTEN di HUD (lihat
 * init/MurderResultOverlay) -- biar FS gak perlu scroll chat lagi cuma buat
 * inget hasil malam. Dua-duanya ItemStack.EMPTY = perintah nge-clear HUD-nya
 * (dipake pas game selesai).
 */
public class MurderResultHudPacket {

    private final ItemStack means;
    private final ItemStack clue;

    public MurderResultHudPacket(ItemStack means, ItemStack clue) {
        this.means = means;
        this.clue = clue;
    }

    public static void encode(MurderResultHudPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.means);
        buf.writeItem(msg.clue);
    }

    public static MurderResultHudPacket decode(FriendlyByteBuf buf) {
        return new MurderResultHudPacket(buf.readItem(), buf.readItem());
    }

    public static void handle(MurderResultHudPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.deception.init.MurderResultHudState.set(msg.means, msg.clue));
        ctx.setPacketHandled(true);
    }
}
