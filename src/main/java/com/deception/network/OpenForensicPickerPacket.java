package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client (per-player): kasih tau client buat buka
 * ForensicPaperScreen buat milih opsi investigation paper yang lagi
 * dipegang. Dikirim dari InvestigationPaperItem#use / #useOn pas stack
 * punya "ForensicCategory" tapi belum ada "ForensicChoice".
 */
public class OpenForensicPickerPacket {

    private final String category;
    private final List<String> options;
    private final InteractionHand hand;

    public OpenForensicPickerPacket(String category, List<String> options, InteractionHand hand) {
        this.category = category;
        this.options = options;
        this.hand = hand;
    }

    public static void encode(OpenForensicPickerPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.category);
        buf.writeVarInt(msg.options.size());
        for (String option : msg.options) {
            buf.writeUtf(option);
        }
        buf.writeEnum(msg.hand);
    }

    public static OpenForensicPickerPacket decode(FriendlyByteBuf buf) {
        String category = buf.readUtf();
        int size = buf.readVarInt();
        List<String> options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            options.add(buf.readUtf());
        }
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        return new OpenForensicPickerPacket(category, options, hand);
    }

    public static void handle(OpenForensicPickerPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.deception.init.ForensicPaperScreen.open(msg.category, msg.options, msg.hand));
        ctx.setPacketHandled(true);
    }
}
