package com.deception.network;

import com.deception.game.ForensicPaperData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: player milih satu opsi di ForensicPaperScreen. Server
 * yang nentuin ulang stack mana yang diubah (item di tangan sesuai hand pas
 * packet dikirim) & validasi ulang kategori/opsi-nya -- JANGAN percaya
 * begitu aja isi packet dari client.
 */
public class ChooseForensicOptionPacket {

    private final InteractionHand hand;
    private final String category;
    private final String chosen;

    public ChooseForensicOptionPacket(InteractionHand hand, String category, String chosen) {
        this.hand = hand;
        this.category = category;
        this.chosen = chosen;
    }

    public static void encode(ChooseForensicOptionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeUtf(msg.category);
        buf.writeUtf(msg.chosen);
    }

    public static ChooseForensicOptionPacket decode(FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        String category = buf.readUtf();
        String chosen = buf.readUtf();
        return new ChooseForensicOptionPacket(hand, category, chosen);
    }

    public static void handle(ChooseForensicOptionPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            ForensicPaperData.Category category = ForensicPaperData.findByDisplayName(msg.category);
            if (category == null || !category.options().contains(msg.chosen)) return;

            ItemStack stack = player.getItemInHand(msg.hand);
            CompoundTag tag = stack.getTag();
            if (tag == null || !msg.category.equals(tag.getString("ForensicCategory"))) return;

            tag.putString("ForensicChoice", msg.chosen);
            stack.setHoverName(Component.literal(msg.category + ": " + msg.chosen));
        });
        ctx.setPacketHandled(true);
    }
}
