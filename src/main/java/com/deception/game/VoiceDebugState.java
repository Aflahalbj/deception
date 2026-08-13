package com.deception.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Debug voice chat buat testing: ngasih tau di chat siapa yang mic-nya
 * BENERAN nyampe server, dan tiap paketnya DITERUSKAN apa DIBLOKIR sama gate
 * presentasi (lihat voicechat/DeceptionVoicechatPlugin).
 *
 * <p>Gunanya mbedain dua kemungkinan yang dari luar keliatan sama persis
 * ("kok gak kedengeran?"): mic-nya emang gak nyampe server sama sekali
 * (masalah setting/device/mod), atau nyampe tapi sengaja diblokir gate
 * presentasi (berarti mod-nya jalan bener).
 *
 * <p>Class ini SENGAJA gak nyentuh API Simple Voice Chat sama sekali -- cuma
 * tipe vanilla & primitif. API-nya compileOnly: kalo mod voicechat-nya gak
 * ke-install, class plugin-nya gagal di-load, dan kalo state ini nempel di
 * sana, command /deception voicedebug ikut mati juga.
 *
 * <p>PENTING: {@link #recordPacket} dipanggil dari THREAD VOICECHAT, bukan
 * server thread -- makanya penampungnya concurrent dan pengiriman chat-nya
 * ditunda ke {@link #tick}, yang jalan di server thread.
 */
public final class VoiceDebugState {

    private VoiceDebugState() {}

    /** Gak ada paket mic selama ini = dianggap udah berhenti ngomong. */
    private static final long SILENCE_TIMEOUT_MS = 400;

    private static volatile boolean enabled = false;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    /** Satu "sesi ngomong": dari paket mic pertama sampe diem lagi. */
    private static final class Session {
        private volatile long lastPacketMs;
        private final AtomicInteger passedCount = new AtomicInteger();
        private final AtomicInteger blockedCount = new AtomicInteger();
        /** Pesan "mulai ngomong"-nya udah dikirim belum. */
        private volatile boolean announced;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        // Jangan tinggalin sesi setengah jalan -- kalo debug-nya dinyalain
        // lagi nanti, sisa ini bakal kelaporan sebagai "berhenti ngomong"
        // padahal kejadiannya udah lama lewat.
        SESSIONS.clear();
    }

    /**
     * Dipanggil DeceptionVoicechatPlugin tiap ada paket mic nyampe server.
     *
     * @param passed true kalo paketnya diteruskan, false kalo diblokir gate presentasi
     */
    public static void recordPacket(UUID sender, boolean passed) {
        if (!enabled) return;

        Session session = SESSIONS.computeIfAbsent(sender, uuid -> new Session());
        session.lastPacketMs = System.currentTimeMillis();
        (passed ? session.passedCount : session.blockedCount).incrementAndGet();
    }

    /** Dipanggil tiap server tick (lihat DeceptionMod#onServerTick). Server thread. */
    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (!enabled) {
            if (!SESSIONS.isEmpty()) SESSIONS.clear();
            return;
        }
        if (SESSIONS.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            UUID uuid = entry.getKey();
            Session session = entry.getValue();

            if (!session.announced) {
                session.announced = true;
                report(server, Component.literal("[voice] ").withStyle(ChatFormatting.DARK_AQUA)
                        .append(Component.literal(nameOf(server, uuid) + " MULAI kirim mic")
                                .withStyle(ChatFormatting.GREEN)));
            }

            if (now - session.lastPacketMs <= SILENCE_TIMEOUT_MS) continue;

            int passed = session.passedCount.get();
            int blocked = session.blockedCount.get();
            SESSIONS.remove(uuid);

            report(server, Component.literal("[voice] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(nameOf(server, uuid) + " berhenti -- ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal((passed + blocked) + " paket").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(passed + " diteruskan").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(blocked + " diblokir").withStyle(
                            blocked > 0 ? ChatFormatting.RED : ChatFormatting.GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.GRAY)));
        }
    }

    /**
     * Laporan cuma ke OP yang online -- command-nya OP-only, dan ini bakal
     * berisik banget kalo kekirim ke semua peserta.
     */
    private static void report(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) player.sendSystemMessage(message);
        }
    }

    /** Nama dari player list; kalo lagi offline, jatuh ke nama yang dicatet GameManager. */
    private static String nameOf(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) return player.getGameProfile().getName();

        String recorded = GameManager.get().getPlayerName(uuid);
        return (recorded == null || recorded.isEmpty()) ? uuid.toString().substring(0, 8) : recorded;
    }

    /** Siapa aja yang DETIK INI lagi kirim mic -- dipake status di /deception voicedebug. */
    public static List<String> activeSpeakers(MinecraftServer server) {
        List<String> names = new ArrayList<>();
        for (UUID uuid : SESSIONS.keySet()) {
            names.add(nameOf(server, uuid));
        }
        return names;
    }
}
