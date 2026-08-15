package com.deception.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> satu client: panel "pilihan kamu sekarang" yang nempel di
 * KIRI-TENGAH layar (lihat init/SelectionOverlay).
 *
 * <p>Ada karena fase malam & fase Allies itu jenis interaksi yang diulang-ulang:
 * murderer gonta-ganti means/clue, lab technician ganti item, inside man ganti
 * target. Kalau tiap klik dibales satu baris chat, chat-nya kebanjiran dan
 * yang penting (tombol [KONFIRMASI], pengumuman offline) ketimbun. Panel ini
 * DIGANTI UTUH tiap kali dikirim, jadi ngeklik 20 kali tetap kelihatan satu
 * kotak yang isinya berubah -- efeknya kayak pesan yang diedit, sesuatu yang
 * chat Minecraft sendiri gak bisa lakuin.
 *
 * <p>{@code lines} kosong = perintah nyembunyiin panelnya.
 *
 * <p>{@code warning} itu baris sementara buat penolakan ("itu bukan cluster
 * kamu", "dia udah gak punya badge") -- ilang sendiri setelah beberapa detik
 * di client, jadi gak numpuk juga. Server SELALU ngirim ulang seluruh isi
 * panel bareng warning-nya, biar client gak perlu nebak-nebak state lama.
 */
public class SelectionHudPacket {

    private final List<Component> lines;
    private final Component warning;

    public SelectionHudPacket(List<Component> lines, Component warning) {
        this.lines = lines == null ? List.of() : lines;
        this.warning = warning;
    }

    /** Perintah nyembunyiin panel. */
    public static SelectionHudPacket hidden() {
        return new SelectionHudPacket(List.of(), null);
    }

    public static void encode(SelectionHudPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.lines.size());
        for (Component line : msg.lines) {
            buf.writeComponent(line);
        }
        buf.writeBoolean(msg.warning != null);
        if (msg.warning != null) {
            buf.writeComponent(msg.warning);
        }
    }

    public static SelectionHudPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Component> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lines.add(buf.readComponent());
        }
        Component warning = buf.readBoolean() ? buf.readComponent() : null;
        return new SelectionHudPacket(lines, warning);
    }

    public static void handle(SelectionHudPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> com.deception.init.SelectionHudState.set(msg.lines, msg.warning));
        ctx.setPacketHandled(true);
    }
}
