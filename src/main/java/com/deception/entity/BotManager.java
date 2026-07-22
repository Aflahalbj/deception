package com.deception.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kelola fake player (bot) untuk keperluan testing tanpa perlu banyak player asli online.
 * Bot ini adalah ServerPlayer sungguhan (via Forge FakePlayer) sehingga bisa
 * diperlakukan sama seperti player biasa oleh GameManager (bisa di-/regis, dikasih role, dll).
 */
public class BotManager {

    private static final BotManager INSTANCE = new BotManager();
    private final Map<UUID, ServerPlayer> activeBots = new LinkedHashMap<>();

    public static BotManager get() {
        return INSTANCE;
    }

    /**
     * Spawn satu bot baru dengan nama tertentu. Nama harus unik (tidak boleh sama
     * dengan player online atau bot lain) supaya bisa dipanggil lewat /regis <playername>.
     */
    public ServerPlayer spawnBot(ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("deception_bot_" + name).getBytes()), name);
        ServerPlayer bot = FakePlayerFactory.get(level, profile);
        bot.setPos(level.getSharedSpawnPos().getX() + 0.5, level.getSharedSpawnPos().getY(), level.getSharedSpawnPos().getZ() + 0.5);
        activeBots.put(bot.getUUID(), bot);
        return bot;
    }

    public boolean despawnBot(String name) {
        ServerPlayer toRemove = null;
        for (ServerPlayer bot : activeBots.values()) {
            if (bot.getGameProfile().getName().equalsIgnoreCase(name)) {
                toRemove = bot;
                break;
            }
        }
        if (toRemove != null) {
            activeBots.remove(toRemove.getUUID());
            return true;
        }
        return false;
    }

    public void despawnAll() {
        activeBots.clear();
    }

    public ServerPlayer getBotByName(String name) {
        for (ServerPlayer bot : activeBots.values()) {
            if (bot.getGameProfile().getName().equalsIgnoreCase(name)) {
                return bot;
            }
        }
        return null;
    }

    /**
     * Cari player (asli ATAU bot) berdasarkan nama, dipakai oleh semua command
     * yang menerima <playername> supaya offline-friendly.
     */
    public static ServerPlayer resolvePlayer(MinecraftServer server, String name) {
        ServerPlayer real = server.getPlayerList().getPlayerByName(name);
        if (real != null) {
            return real;
        }
        return BotManager.get().getBotByName(name);
    }

    public Map<UUID, ServerPlayer> getActiveBots() {
        return activeBots;
    }
}
