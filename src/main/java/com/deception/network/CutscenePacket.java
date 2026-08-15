package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> SATU client: state cutscene intro (lihat GameManager#startCutscene).
 *
 * <p>Isinya dua hal yang GAK BISA diurus server sendiri:
 * <ul>
 *   <li>{@code active} + yaw/pitch -- kunci arah pandang. Server cuma bisa
 *       nge-teleport (yang bakal keliatan patah-patah kalo dipaksa tiap
 *       tick); yang beneran nahan mouse itu client, lihat CutsceneClientState.</li>
 *   <li>{@code showImage} -- munculin gambar intro di tengah layar
 *       (init/CutsceneOverlay). Title vanilla cuma bisa teks, bukan texture.</li>
 * </ul>
 */
public class CutscenePacket {

    private final boolean active;
    private final float yaw;
    private final float pitch;
    private final boolean showImage;

    public CutscenePacket(boolean active, float yaw, float pitch, boolean showImage) {
        this.active = active;
        this.yaw = yaw;
        this.pitch = pitch;
        this.showImage = showImage;
    }

    public static void encode(CutscenePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.pitch);
        buf.writeBoolean(msg.showImage);
    }

    public static CutscenePacket decode(FriendlyByteBuf buf) {
        return new CutscenePacket(buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(CutscenePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.deception.init.CutsceneClientState.set(
                msg.active, msg.yaw, msg.pitch, msg.showImage));
        ctx.setPacketHandled(true);
    }
}
