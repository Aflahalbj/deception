package com.deception.game;

import com.deception.entity.BotManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class GameManager {

    public enum State {
        IDLE, COUNTDOWN, DISCUSS, RUNNING
    }

    private static final GameManager INSTANCE = new GameManager();

    public static GameManager get() {
        return INSTANCE;
    }

    private State state = State.IDLE;
    private final LinkedHashSet<UUID> registeredPlayers = new LinkedHashSet<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Map<UUID, Role> roleAssignments = new HashMap<>();

    // override manual dari /customrole; null = pakai default tabel komposisi
    private Boolean accompliceOverride = null;
    private Boolean witnessOverride = null;

    // "random" atau nama player spesifik
    private String forensicScientistMode = "random";

    private int discussTimerSeconds = 600; // default 10 menit

    private int countdownTicks = 0;
    private int discussTicksLeft = 0;

    private MinecraftServer serverRef;
    private final Random shuffleRandom = new Random();

    // ---------- Registrasi ----------

    public boolean registerPlayer(MinecraftServer server, String name) {
        ServerPlayer player = BotManager.resolvePlayer(server, name);
        if (player == null) {
            return false;
        }
        registeredPlayers.add(player.getUUID());
        playerNames.put(player.getUUID(), name);
        return true;
    }

    public boolean unregisterPlayer(String name) {
        UUID target = null;
        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                target = entry.getKey();
                break;
            }
        }
        if (target == null) return false;
        registeredPlayers.remove(target);
        roleAssignments.remove(target);
        return true;
    }

    public int registerAll(MinecraftServer server) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            registeredPlayers.add(player.getUUID());
            playerNames.put(player.getUUID(), player.getGameProfile().getName());
            count++;
        }
        for (ServerPlayer bot : BotManager.get().getActiveBots().values()) {
            registeredPlayers.add(bot.getUUID());
            playerNames.put(bot.getUUID(), bot.getGameProfile().getName());
            count++;
        }
        return count;
    }

    public int unregisterAll() {
        int count = registeredPlayers.size();
        registeredPlayers.clear();
        roleAssignments.clear();
        return count;
    }

    public Set<UUID> getRegisteredPlayers() {
        return registeredPlayers;
    }

    // ---------- Custom role toggle ----------

    public void setCustomRole(Role role, boolean enabled) {
        if (role == Role.ACCOMPLICE) {
            accompliceOverride = enabled;
        } else if (role == Role.WITNESS) {
            witnessOverride = enabled;
        }
    }

    // ---------- Forensic scientist & timer ----------

    public void setForensicScientistMode(String mode) {
        this.forensicScientistMode = mode;
    }

    public void setDiscussTimerSeconds(int seconds) {
        this.discussTimerSeconds = seconds;
    }

    // ---------- Role info / debug ----------

    public Map<UUID, Role> getRoleAssignments() {
        return roleAssignments;
    }

    public String getPlayerName(UUID uuid) {
        return playerNames.getOrDefault(uuid, uuid.toString());
    }

    // ---------- Start / stop ----------

    public boolean startGame(MinecraftServer server) {
        if (state != State.IDLE) {
            return false;
        }
        if (registeredPlayers.size() < 4 || registeredPlayers.size() > 12) {
            return false;
        }
        this.serverRef = server;
        this.state = State.COUNTDOWN;
        this.countdownTicks = 100; // 5 detik * 20 tick
        broadcast(server, Component.literal("Game akan dimulai dalam 5 detik...").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    public void stopGame() {
        this.state = State.IDLE;
        this.roleAssignments.clear();
        this.countdownTicks = 0;
        this.discussTicksLeft = 0;
        if (serverRef != null) {
            broadcast(serverRef, Component.literal("Game dihentikan.").withStyle(ChatFormatting.RED));
        }
    }

    private void assignRoles(MinecraftServer server) {
        RoleComposition composition = new RoleComposition(registeredPlayers.size());
        if (accompliceOverride != null) composition.setAccompliceEnabled(accompliceOverride);
        if (witnessOverride != null) composition.setWitnessEnabled(witnessOverride);

        Map<Role, Integer> counts = composition.resolve();

        List<UUID> pool = new ArrayList<>(registeredPlayers);
        Collections.shuffle(pool);

        roleAssignments.clear();
        int index = 0;
        for (Map.Entry<Role, Integer> entry : counts.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                if (index >= pool.size()) break;
                roleAssignments.put(pool.get(index), entry.getKey());
                index++;
            }
        }

        // Override forensic scientist kalau diminta spesifik (bukan random)
        if (!"random".equalsIgnoreCase(forensicScientistMode)) {
            UUID targetFs = null;
            for (Map.Entry<UUID, String> e : playerNames.entrySet()) {
                if (e.getValue().equalsIgnoreCase(forensicScientistMode) && registeredPlayers.contains(e.getKey())) {
                    targetFs = e.getKey();
                    break;
                }
            }
            if (targetFs != null) {
                UUID currentFs = null;
                for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
                    if (e.getValue() == Role.FORENSIC_SCIENTIST) {
                        currentFs = e.getKey();
                        break;
                    }
                }
                if (currentFs != null && !currentFs.equals(targetFs)) {
                    Role targetOldRole = roleAssignments.get(targetFs);
                    roleAssignments.put(targetFs, Role.FORENSIC_SCIENTIST);
                    roleAssignments.put(currentFs, targetOldRole);
                }
            }
        }

        for (Map.Entry<UUID, Role> entry : roleAssignments.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                Component title = Component.literal(entry.getValue().getDisplayName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                Component subtitle = Component.literal("Peran kamu!").withStyle(ChatFormatting.YELLOW);
                sendTitle(player, title, subtitle, 5, 50, 15);
                player.sendSystemMessage(Component.literal("Peran lo: " + entry.getValue().getDisplayName())
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /**
     * Animasi "ngocok" role — dipanggil tiap tick pas COUNTDOWN, nge-flash nama
     * role random ke semua player terdaftar biar kerasa kayak diacak beneran
     * sebelum reveal final di assignRoles().
     */
    private void spinRoleTitle() {
        if (countdownTicks <= 0 || countdownTicks % 4 != 0) {
            return;
        }
        Role[] roles = Role.values();
        Component title = Component.literal(roles[shuffleRandom.nextInt(roles.length)].getDisplayName())
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        Component subtitle = Component.literal("Mengocok peran...").withStyle(ChatFormatting.GRAY);
        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = serverRef.getPlayerList().getPlayer(uuid);
            if (player != null) {
                sendTitle(player, title, subtitle, 0, 4, 0);
            }
        }
    }

    private void sendTitle(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    // ---------- Tick loop, dipanggil dari server tick event ----------

    public void tick() {
        if (state == State.COUNTDOWN) {
            countdownTicks--;
            spinRoleTitle();
            if (countdownTicks == 60 || countdownTicks == 40 || countdownTicks == 20) {
                broadcast(serverRef, Component.literal((countdownTicks / 20) + "...").withStyle(ChatFormatting.YELLOW));
            }
            if (countdownTicks <= 0) {
                assignRoles(serverRef);
                state = State.DISCUSS;
                discussTicksLeft = discussTimerSeconds * 20;
                broadcast(serverRef, Component.literal("Peran sudah dibagikan! Diskusi dimulai.").withStyle(ChatFormatting.GREEN));
            }
        } else if (state == State.DISCUSS) {
            discussTicksLeft--;
            if (discussTicksLeft <= 0) {
                state = State.RUNNING;
                broadcast(serverRef, Component.literal("Waktu diskusi habis!").withStyle(ChatFormatting.RED));
            }
        }
    }

    private void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    public State getState() {
        return state;
    }
}
