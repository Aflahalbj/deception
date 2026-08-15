package com.deception.network;

import com.deception.game.GameManager;
import com.deception.game.PresentationManager;
import com.deception.game.Role;
import com.deception.game.SettingSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: satu aksi dari GUI /deception setting.
 *
 * <p>Semua validasi ada di sini, BUKAN di GUI: permission dicek ulang tiap
 * packet (client bisa aja ngirim ini tanpa pernah buka GUI-nya), dan angka
 * timer di-clamp ke batas yang sama kayak command /deception settimer. GUI
 * cuma boleh nentuin aksi mana yang keliatan, bukan aksi mana yang boleh.
 */
public class SettingActionPacket {

    public enum Action {
        /** Tambah/kurang timer diskusi, {@code amount} dalam MENIT (boleh negatif). */
        DISCUSS_ADD,
        /** Tambah/kurang timer presentasi, {@code amount} dalam DETIK (boleh negatif). */
        PRESENTATION_ADD,
        /** Balikin dua timer ke default. */
        TIMER_RESET,
        /** Muter status accomplice: AUTO -> AKTIF -> NONAKTIF -> AUTO. */
        CYCLE_ACCOMPLICE,
        /** Muter status witness. */
        CYCLE_WITNESS,
        /** Muter status protective detail. */
        CYCLE_PROTECTIVE_DETAIL,
        /** Muter status lab technician. */
        CYCLE_LAB_TECHNICIAN,
        /** Muter status inside man. */
        CYCLE_INSIDE_MAN,
        /** Paksa role satu player. {@code amount} = ordinal Role, -1 = balik ke auto. */
        SET_PLAYER_ROLE,
        /** Nyalain/matiin HUD role sendiri di pojok kanan atas layar peserta. */
        TOGGLE_ROLE_VISIBLE
    }

    private final Action action;
    private final int amount;
    private final String playerName;

    public SettingActionPacket(Action action, int amount, String playerName) {
        this.action = action;
        this.amount = amount;
        this.playerName = playerName == null ? "" : playerName;
    }

    public SettingActionPacket(Action action, int amount) {
        this(action, amount, "");
    }

    public SettingActionPacket(Action action) {
        this(action, 0, "");
    }

    public static void encode(SettingActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeInt(msg.amount);
        buf.writeUtf(msg.playerName);
    }

    public static SettingActionPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        int amount = buf.readInt();
        String playerName = buf.readUtf();
        return new SettingActionPacket(action, amount, playerName);
    }

    public static void handle(SettingActionPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Gate yang sama kayak semua subcommand setting di ModCommands.
            if (!player.hasPermissions(2)) return;

            apply(msg, player.getServer());

            // Balikin kondisi terbaru biar GUI-nya gambar ulang dari server.
            ModNetworking.sendSettingState(player);
        });
        ctx.setPacketHandled(true);
    }

    private static void apply(SettingActionPacket msg, net.minecraft.server.MinecraftServer server) {
        GameManager game = GameManager.get();

        switch (msg.action) {
            case DISCUSS_ADD -> {
                int minutes = game.getDiscussTimerSeconds() / 60 + msg.amount;
                minutes = clamp(minutes, SettingSnapshot.DISCUSS_MIN_MINUTES, SettingSnapshot.DISCUSS_MAX_MINUTES);
                game.setDiscussTimerSeconds(minutes * 60);
            }
            case PRESENTATION_ADD -> {
                int seconds = PresentationManager.get().getPresentasiTurnSeconds() + msg.amount;
                seconds = clamp(seconds, SettingSnapshot.PRESENTATION_MIN_SECONDS, SettingSnapshot.PRESENTATION_MAX_SECONDS);
                PresentationManager.get().setPresentasiTurnSeconds(seconds);
            }
            case TIMER_RESET -> {
                game.resetDiscussTimerToDefault();
                PresentationManager.get().resetPresentasiTurnToDefault();
            }
            case CYCLE_ACCOMPLICE -> cycleCustomRole(Role.accomplice, game.getAccompliceOverride());
            case CYCLE_WITNESS -> cycleCustomRole(Role.witness, game.getWitnessOverride());
            case CYCLE_PROTECTIVE_DETAIL -> cycleCustomRole(Role.protective_detail, game.getProtectiveDetailOverride());
            case CYCLE_LAB_TECHNICIAN -> cycleCustomRole(Role.lab_technician, game.getLabTechnicianOverride());
            case CYCLE_INSIDE_MAN -> cycleCustomRole(Role.inside_man, game.getInsideManOverride());
            case TOGGLE_ROLE_VISIBLE -> game.setRoleVisible(server, !game.isRoleVisible());
            case SET_PLAYER_ROLE -> {
                if (msg.playerName.isEmpty()) return;
                if (msg.amount < 0) {
                    game.clearPlayerRole(msg.playerName);
                    return;
                }
                Role[] roles = Role.values();
                if (msg.amount >= roles.length) return;
                Role role = roles[msg.amount];

                // Dicek ulang di sini, bukan cuma di GUI: tombolnya emang
                // dimatiin di client, tapi packet-nya tetep bisa dikirim
                // manual. Role nonaktif / jatahnya udah abis -> tolak.
                if (!SettingSnapshot.capture().canAssign(msg.playerName, role)) return;

                game.setPlayerRole(msg.playerName, role);
            }
        }
    }

    private static void cycleCustomRole(Role role, Boolean currentOverride) {
        SettingSnapshot.Mode next = SettingSnapshot.Mode.of(currentOverride).next();
        Boolean value = next.toOverride();
        if (value == null) {
            GameManager.get().clearCustomRoleOverride(role);
        } else {
            GameManager.get().setCustomRole(role, value);
        }

        // Lepas SEMUA player yang terlanjur dipaksa ke role yang sekarang
        // jatahnya 0. Sengaja disapu semua role, bukan cuma yang barusan
        // di-toggle: matiin Witness ikut matiin Protective Detail (dia gak
        // punya siapa-siapa buat diliat), jadi kalau cuma role yang di-toggle
        // yang dicek, bisa nyangkut player yang dipaksa jadi Protective
        // Detail padahal role-nya udah gak kepake.
        SettingSnapshot after = SettingSnapshot.capture();
        var capacity = after.roleCapacity();

        for (SettingSnapshot.PlayerEntry entry : after.players()) {
            Role forced = entry.role();
            if (forced != null && capacity.getOrDefault(forced, 0) == 0) {
                GameManager.get().clearPlayerRole(entry.name());
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
