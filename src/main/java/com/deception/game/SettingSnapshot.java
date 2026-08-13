package com.deception.game;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Potret semua setting yang bisa diubah lewat GUI /deception setting, dikirim
 * server -> client tiap kali ada yang berubah.
 *
 * <p>GUI-nya SENGAJA gak nyimpen state sendiri: tiap klik ngirim aksi ke
 * server, server yang nerapin + validasi, terus balikin snapshot baru dan GUI
 * gambar ulang dari situ. Jadi gak ada kemungkinan tampilan GUI beda sama
 * kondisi asli di server (misal ada 2 OP buka setting barengan).
 */
public record SettingSnapshot(
        int discussSeconds,
        int presentationSeconds,
        Mode accompliceMode,
        Mode witnessMode,
        boolean accompliceAutoValue,
        boolean witnessAutoValue,
        boolean roleVisible,
        List<PlayerEntry> players) {

    /** Batas yang sama persis kayak /deception settimer, biar gak beda aturan. */
    public static final int DISCUSS_MIN_MINUTES = 1;
    public static final int DISCUSS_MAX_MINUTES = 120;
    public static final int PRESENTATION_MIN_SECONDS = 5;
    public static final int PRESENTATION_MAX_SECONDS = 300;

    /** Status custom role: ikut tabel default, atau dipaksa nyala/mati. */
    public enum Mode {
        AUTO, AKTIF, NONAKTIF;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** null = auto (belum di-override manual). */
        public static Mode of(Boolean override) {
            if (override == null) return AUTO;
            return override ? AKTIF : NONAKTIF;
        }

        /** Kebalikan {@link #of}: nilai buat disimpan di GameManager. */
        public Boolean toOverride() {
            return switch (this) {
                case AUTO -> null;
                case AKTIF -> Boolean.TRUE;
                case NONAKTIF -> Boolean.FALSE;
            };
        }
    }

    /** {@code roleOrdinal} -1 = belum dipaksa, ikut random pas start game. */
    public record PlayerEntry(String name, int roleOrdinal) {

        public Role role() {
            Role[] roles = Role.values();
            return (roleOrdinal < 0 || roleOrdinal >= roles.length) ? null : roles[roleOrdinal];
        }
    }

    /** Baca kondisi terkini dari GameManager & PresentationManager. Server-only. */
    public static SettingSnapshot capture() {
        GameManager game = GameManager.get();

        List<PlayerEntry> players = new ArrayList<>();
        for (UUID uuid : game.getRegisteredPlayers()) {
            Role role = game.getRoleAssignments().get(uuid);
            players.add(new PlayerEntry(game.getPlayerName(uuid), role == null ? -1 : role.ordinal()));
        }
        // Urutan Set<UUID> gak stabil -- kalo gak disortir, tombol player bisa
        // loncat-loncat posisinya tiap kali GUI di-refresh.
        players.sort(Comparator.comparing(PlayerEntry::name, String.CASE_INSENSITIVE_ORDER));

        return new SettingSnapshot(
                game.getDiscussTimerSeconds(),
                PresentationManager.get().getPresentasiTurnSeconds(),
                Mode.of(game.getAccompliceOverride()),
                Mode.of(game.getWitnessOverride()),
                game.resolveAccompliceDefault(),
                game.resolveWitnessDefault(),
                game.isRoleVisible(),
                players);
    }

    /** Nilai efektif accomplice: kalau AUTO, pakai hasil tabel default. */
    public boolean accompliceEffective() {
        return accompliceMode == Mode.AUTO ? accompliceAutoValue : accompliceMode == Mode.AKTIF;
    }

    /** Nilai efektif witness: kalau AUTO, pakai hasil tabel default. */
    public boolean witnessEffective() {
        return witnessMode == Mode.AUTO ? witnessAutoValue : witnessMode == Mode.AKTIF;
    }

    // ------------------------------------------------- aturan pemberian role

    /**
     * Jatah tiap role di komposisi yang lagi berlaku. Role yang gak kepake
     * (accomplice/witness pas nonaktif) jatahnya 0.
     *
     * <p>Ini dipake DUA-DUANYA: server buat validasi packet, client buat
     * nentuin tombol mana yang dimatiin. Sengaja satu method biar aturan yang
     * dilihat player sama persis sama aturan yang dipaksain server.
     */
    public Map<Role, Integer> roleCapacity() {
        RoleComposition composition = new RoleComposition(players.size());
        composition.setAccompliceEnabled(accompliceEffective());
        composition.setWitnessEnabled(witnessEffective());
        return composition.resolve();
    }

    /**
     * Player pertama yang udah dipaksa ke {@code role}, gak ngitung
     * {@code exceptName}. null kalau belum ada yang megang.
     */
    public String holderOf(Role role, String exceptName) {
        for (PlayerEntry entry : players) {
            if (entry.name().equalsIgnoreCase(exceptName)) continue;
            if (entry.role() == role) return entry.name();
        }
        return null;
    }

    /** Berapa player yang udah dipaksa ke {@code role}, gak ngitung {@code exceptName}. */
    public int assignedCount(Role role, String exceptName) {
        int count = 0;
        for (PlayerEntry entry : players) {
            if (entry.name().equalsIgnoreCase(exceptName)) continue;
            if (entry.role() == role) count++;
        }
        return count;
    }

    /**
     * Boleh gak {@code playerName} dipaksa jadi {@code role}? Gagal kalau
     * role-nya lagi nonaktif (jatah 0) atau jatahnya udah keabisan diambil
     * player lain -- FS/Murderer/Accomplice/Witness jatahnya cuma 1.
     */
    public boolean canAssign(String playerName, Role role) {
        int capacity = roleCapacity().getOrDefault(role, 0);
        return assignedCount(role, playerName) < capacity;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(discussSeconds);
        buf.writeVarInt(presentationSeconds);
        buf.writeEnum(accompliceMode);
        buf.writeEnum(witnessMode);
        buf.writeBoolean(accompliceAutoValue);
        buf.writeBoolean(witnessAutoValue);
        buf.writeBoolean(roleVisible);
        buf.writeVarInt(players.size());
        for (PlayerEntry entry : players) {
            buf.writeUtf(entry.name());
            // roleOrdinal bisa -1, jadi pakai writeInt bukan writeVarInt.
            buf.writeInt(entry.roleOrdinal());
        }
    }

    public static SettingSnapshot decode(FriendlyByteBuf buf) {
        int discussSeconds = buf.readVarInt();
        int presentationSeconds = buf.readVarInt();
        Mode accompliceMode = buf.readEnum(Mode.class);
        Mode witnessMode = buf.readEnum(Mode.class);
        boolean accompliceAuto = buf.readBoolean();
        boolean witnessAuto = buf.readBoolean();
        boolean roleVisible = buf.readBoolean();

        int size = buf.readVarInt();
        List<PlayerEntry> players = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            players.add(new PlayerEntry(buf.readUtf(), buf.readInt()));
        }

        return new SettingSnapshot(discussSeconds, presentationSeconds,
                accompliceMode, witnessMode, accompliceAuto, witnessAuto, roleVisible, players);
    }
}
