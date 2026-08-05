package com.deception.game;

import com.deception.command.RoleDescriptions;
import com.deception.init.ClusterData;
import com.deception.init.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.registries.RegistryObject;
import com.deception.block.ClueBlock;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraftforge.common.ForgeMod;
import org.joml.Quaternionf;

import java.util.*;

public class GameManager {

    public enum State {
        IDLE, COUNTDOWN, SHUFFLE, NIGHT, DISCUSS, RUNNING
    }

    private static final GameManager INSTANCE = new GameManager();

    public static GameManager get() {
        return INSTANCE;
    }

    private State state = State.IDLE;
    private final LinkedHashSet<UUID> registeredPlayers = new LinkedHashSet<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    public static final Map<BlockPos, ClusterData> CLUSTERS = new HashMap<>();
    private final Map<UUID, Role> roleAssignments = new HashMap<>();

    // override manual dari /customrole; null = pakai default tabel komposisi
    private Boolean accompliceOverride = null;
    private Boolean witnessOverride = null;

    // "random" atau nama player spesifik
    private String forensicScientistMode = "random";

    private int discussTimerSeconds = 600; // default 10 menit

    // ---------- Countdown (title/subtitle only, gak ada spam chat) ----------
    private static final int COUNTDOWN_SECONDS = 5;
    private int countdownTicks = 0;
    private int lastCountdownSecondShown = -1;

    // ---------- Shuffle role (animasi "ngocok" peran) ----------
    private static final int SHUFFLE_DURATION_TICKS = 100; // 5 detik
    private int shuffleTicksLeft = 0;
    private List<Role> activeRolePool = new ArrayList<>();

    private int discussTicksLeft = 0;

    // ---------- Night sequence (tutup mata -> murderer/accomplice pilih item -> witness liat) ----------
    private static final int NIGHT_CLOSE_HOLD = 60;    // 3 detik "semua tutup mata" sebelum killer dibangunin
    private static final int NIGHT_WITNESS_HOLD = 60;  // 3 detik witness liat killer nyala (glowing)
    private static final int NIGHT_WAKE_HOLD = 20;     // 1 detik title "semua bangun" sebelum lanjut discuss
    private final Set<UUID> confirmedMurderers = new HashSet<>();

    private int nightTicksElapsed;
    private int nightKillersWakeAt;
    private int nightWitnessRevealAt;
    private int nightWitnessEndAt;
    private int nightWakeAllAt;
    private int nightDoneAt;
    private final Set<UUID> nightBlindfolded = new HashSet<>();
    private final List<UUID> nightKillers = new ArrayList<>();
    private UUID nightWitnessUuid;
    private boolean murdererConfirmed = false;

    // ---------- Murderer pilih item means/clue asli pas night ----------
    // 2 posisi: 1 untuk means, 1 untuk clue
    private BlockPos murdererSelectedMeansBacking = null;
    private BlockPos murdererSelectedClueBacking = null;

    // reverse-lookup posisi block means/clue -> UUID pemilik cluster-nya
    private final Map<BlockPos, UUID> clusterOwnerByPos = new HashMap<>();

    private static final String MURDERER_CONFIRM_HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjVhM2I0OWJlZWMzYWIyM2FlMGI2MGRhYjU2ZTljYzhmYTE2NzY5YTI1ODMwYjVkOGQ2YzQ2Mzc4ZjU0NDMwIn19fQ==";
    private static final String CONFIRM_HEAD_TAG = "DeceptionConfirmHead";

    private long globalTickCounter = 0;

    private MinecraftServer serverRef;
    private final Random shuffleRandom = new Random();
    private final List<UUID> spawnedHeadEntities = new ArrayList<>();
    private final Map<UUID, HeadPlacement> headPlacementByOwner = new HashMap<>();
    private final Map<UUID, List<UUID>> headEntitiesByOwner = new HashMap<>();
    private Difficulty previousDifficulty;

    // ---------- Teleport + pasang clue/means random pas countdown selesai ----------
    private static final BlockPos ARENA_TELEPORT_POS = new BlockPos(119, -59, 179);

    private static final int MEANS_Y = -57;
    private static final int CLUE_Y = -58;
    private static final int HEAD_Y = MEANS_Y + 1;

    private static final int MAX_CLUSTERS_PER_SIDE_WALL = 4;
    private static final int CLUSTER_WIDTH = 4;

    private static final float HEAD_SURFACE_INSET = 0.06F;

    private record WallSpec(String label, boolean alongX, int fixedCoord, int center, int length, Direction facing) {}

    private record HeadPlacement(WallSpec wall, int[] columns) {}

    private static final WallSpec[] ARENA_WALLS = new WallSpec[]{
            new WallSpec("kiri", true, 189, 120, 21, Direction.NORTH),
            new WallSpec("kanan", true, 169, 120, 21, Direction.SOUTH),
            new WallSpec("belakang", false, 130, 179, 21, Direction.WEST),
    };

    private static int[][] clusterColumnGroups(int length, int clusterCount) {
        if (clusterCount <= 0) return new int[0][];

        int gap = clusterCount > 1 ? 1 : 0;
        int totalWidth = clusterCount * CLUSTER_WIDTH + gap * (clusterCount - 1);
        int startCol = -totalWidth / 2;

        int[][] groups = new int[clusterCount][CLUSTER_WIDTH];
        for (int i = 0; i < clusterCount; i++) {
            int colStart = startCol + i * (CLUSTER_WIDTH + gap);
            for (int j = 0; j < CLUSTER_WIDTH; j++) {
                groups[i][j] = colStart + j;
            }
        }
        return groups;
    }

    private static int[] computeClusterCountsPerWall(int totalPlayers) {
        int kiri = Math.min(MAX_CLUSTERS_PER_SIDE_WALL, (int) Math.ceil(totalPlayers / 2.0));
        int kanan = Math.min(MAX_CLUSTERS_PER_SIDE_WALL, totalPlayers - kiri);
        int belakang = totalPlayers - kiri - kanan;
        return new int[]{kiri, kanan, belakang};
    }

    // ---------- Registrasi ----------

    public boolean registerPlayer(MinecraftServer server, String name) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
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

    // ---------- Set role untuk testing ----------

    public boolean setPlayerRole(String playerName, Role role) {
        UUID targetUuid = null;
        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(playerName)) {
                targetUuid = entry.getKey();
                break;
            }
        }
        if (targetUuid == null || !registeredPlayers.contains(targetUuid)) {
            return false;
        }
        roleAssignments.put(targetUuid, role);
        return true;
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

    public enum StartResult {
        OK, ALREADY_RUNNING, INVALID_PLAYER_COUNT, PLAYER_OFFLINE
    }

    public StartResult startGame(MinecraftServer server) {
        if (state != State.IDLE) {
            return StartResult.ALREADY_RUNNING;
        }
        if (registeredPlayers.size() < 4 || registeredPlayers.size() > 12) {
            return StartResult.INVALID_PLAYER_COUNT;
        }
        if (!getOfflineRegisteredPlayerNames(server).isEmpty()) {
            return StartResult.PLAYER_OFFLINE;
        }
        this.serverRef = server;
        this.state = State.COUNTDOWN;
        this.countdownTicks = COUNTDOWN_SECONDS * 20;
        this.lastCountdownSecondShown = -1;
        server.setPvpAllowed(false);
        this.previousDifficulty = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.PEACEFUL, true);

        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.setGameMode(GameType.ADVENTURE);
                AttributeInstance blockReach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
                if (blockReach != null) {
                    blockReach.setBaseValue(30.0D);
                }
            }
        }
        return StartResult.OK;
    }

    public List<String> getOfflineRegisteredPlayerNames(MinecraftServer server) {
        List<String> offline = new ArrayList<>();
        for (UUID uuid : registeredPlayers) {
            if (server.getPlayerList().getPlayer(uuid) == null) {
                offline.add(getPlayerName(uuid));
            }
        }
        return offline;
    }

    public List<String> getRegisteredPlayerStatusLines(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        for (UUID uuid : registeredPlayers) {
            boolean online = server.getPlayerList().getPlayer(uuid) != null;
            lines.add(getPlayerName(uuid) + (online ? " (online)" : " (offline)"));
        }
        return lines;
    }

    public void stopGame(MinecraftServer server) {
        abortGame(server, Component.literal("Game dihentikan oleh admin.").withStyle(ChatFormatting.RED));
    }

    private void abortGame(MinecraftServer server, Component reason) {
        this.state = State.IDLE;
        this.roleAssignments.clear();
        this.countdownTicks = 0;
        this.lastCountdownSecondShown = -1;
        this.shuffleTicksLeft = 0;
        this.discussTicksLeft = 0;
        this.nightTicksElapsed = 0;
        this.murdererConfirmed = false;
        this.murdererSelectedMeansBacking = null;
        this.murdererSelectedClueBacking = null;
        this.nightWitnessRevealAt = Integer.MAX_VALUE;
        this.nightWitnessEndAt = Integer.MAX_VALUE;
        this.nightWakeAllAt = Integer.MAX_VALUE;
        this.nightDoneAt = Integer.MAX_VALUE;

        MinecraftServer target = server != null ? server : serverRef;
        if (target != null) {
            target.setPvpAllowed(true);
            if (previousDifficulty != null) {
                target.setDifficulty(previousDifficulty, true);
            }
            
            // Balikin backing ke warna asli sebelum restoreArena
            ServerLevel level = target.overworld();
            if (murdererSelectedMeansBacking != null) {
                restoreBacking(level, murdererSelectedMeansBacking);
            }
            if (murdererSelectedClueBacking != null) {
                restoreBacking(level, murdererSelectedClueBacking);
            }
            
            restoreArena(target);
            for (UUID uuid : registeredPlayers) {
                ServerPlayer player = target.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    player.setGameMode(GameType.SURVIVAL);
                    removeBlindfold(player);
                    removeGlow(player);
                    AttributeInstance blockReach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
                    if (blockReach != null) {
                        blockReach.setBaseValue(blockReach.getAttribute().getDefaultValue());
                    }
                }
            }
            nightBlindfolded.clear();
            nightKillers.clear();
            nightWitnessUuid = null;
            broadcast(target, reason);
        }
    }

    private void restoreArena(MinecraftServer server) {
        ServerLevel level = server.overworld();

        for (UUID headId : spawnedHeadEntities) {
            var entity = level.getEntity(headId);
            if (entity != null) {
                entity.discard();
            }
        }
        for (Entity entity : level.getEntities().getAll()) {
            if (entity.getTags().contains("deception_display")) {
                entity.discard();
            }
        }
        spawnedHeadEntities.clear();
        headPlacementByOwner.clear();
        headEntitiesByOwner.clear();
        CLUSTERS.clear();
        clusterOwnerByPos.clear();

        resetArenaBlocks(level);
    }

    private void resetArenaBlocks(ServerLevel level) {
        BlockState deepslateTiles = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (WallSpec wall : ARENA_WALLS) {
            Direction backingDir = wall.facing().getOpposite();
            int half = wall.length() / 2;
            for (int offset = -half; offset <= half; offset++) {
                int x = wall.alongX() ? wall.center() + offset : wall.fixedCoord();
                int z = wall.alongX() ? wall.fixedCoord() : wall.center() + offset;

                BlockPos meansPos = new BlockPos(x, MEANS_Y, z);
                BlockPos cluePos = new BlockPos(x, CLUE_Y, z);
                level.setBlock(meansPos, air, 3);
                level.setBlock(cluePos, air, 3);
                level.setBlock(meansPos.relative(backingDir), deepslateTiles, 3);
                level.setBlock(cluePos.relative(backingDir), deepslateTiles, 3);
            }
        }
    }

    private AABB computeArenaBounds() {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (WallSpec wall : ARENA_WALLS) {
            if (wall.alongX()) {
                minX = Math.min(minX, wall.center() - wall.length() / 2.0);
                maxX = Math.max(maxX, wall.center() + wall.length() / 2.0);
                minZ = Math.min(minZ, wall.fixedCoord());
                maxZ = Math.max(maxZ, wall.fixedCoord());
            } else {
                minZ = Math.min(minZ, wall.center() - wall.length() / 2.0);
                maxZ = Math.max(maxZ, wall.center() + wall.length() / 2.0);
                minX = Math.min(minX, wall.fixedCoord());
                maxX = Math.max(maxX, wall.fixedCoord());
            }
        }
        double pad = 3;
        return new AABB(minX - pad, CLUE_Y - 3, minZ - pad, maxX + pad, HEAD_Y + 3, maxZ + pad);
    }

    public void forceCleanupOnStartup(MinecraftServer server) {
        this.state = State.IDLE;
        ServerLevel level = server.overworld();

        resetArenaBlocks(level);

        for (UUID headId : spawnedHeadEntities) {
            var entity = level.getEntity(headId);
            if (entity != null) {
                entity.discard();
            }
        }
        for (Entity entity : level.getEntities().getAll()) {
            if (entity.getTags().contains("deception_display")) {
                entity.discard();
            }
        }

        spawnedHeadEntities.clear();
        headPlacementByOwner.clear();
        headEntitiesByOwner.clear();
        CLUSTERS.clear();
        clusterOwnerByPos.clear();
    }

    private List<Role> computeActiveRolePool() {
        RoleComposition composition = new RoleComposition(registeredPlayers.size());
        if (accompliceOverride != null) composition.setAccompliceEnabled(accompliceOverride);
        if (witnessOverride != null) composition.setWitnessEnabled(witnessOverride);

        Map<Role, Integer> counts = composition.resolve();
        List<Role> pool = new ArrayList<>();
        for (Map.Entry<Role, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0) {
                pool.add(entry.getKey());
            }
        }
        return pool;
    }

    private void computeRoleAssignments() {
        RoleComposition composition = new RoleComposition(registeredPlayers.size());
        if (accompliceOverride != null) composition.setAccompliceEnabled(accompliceOverride);
        if (witnessOverride != null) composition.setWitnessEnabled(witnessOverride);

        Map<Role, Integer> counts = composition.resolve();

        // Cek apakah ada role yang udah di-set manual via /deception setrole
        Map<UUID, Role> manualRoles = new HashMap<>();
        for (UUID uuid : registeredPlayers) {
            if (roleAssignments.containsKey(uuid)) {
                manualRoles.put(uuid, roleAssignments.get(uuid));
            }
        }

        // Kalo ada manual role, kita adjust counts-nya
        if (!manualRoles.isEmpty()) {
            // Validasi: cek apakah role yang di-set manual valid
            for (Role role : manualRoles.values()) {
                int currentCount = counts.getOrDefault(role, 0);
                if (currentCount > 0) {
                    counts.put(role, currentCount - 1);
                } else {
                    // Kalo role udah penuh, override tetap dipake tapi kita kurangi dari Investigator
                    counts.put(Role.INVESTIGATOR, counts.getOrDefault(Role.INVESTIGATOR, 0) - 1);
                    if (counts.get(Role.INVESTIGATOR) < 0) counts.put(Role.INVESTIGATOR, 0);
                }
            }
            
            // Hapus player yang udah di-set manual dari pool
            List<UUID> pool = new ArrayList<>(registeredPlayers);
            pool.removeAll(manualRoles.keySet());
            
            // Random shuffle sisa pool
            Collections.shuffle(pool);
            
            // Assign role manual dulu
            roleAssignments.clear();
            roleAssignments.putAll(manualRoles);
            
            // Assign sisa role ke pool
            int index = 0;
            for (Map.Entry<Role, Integer> entry : counts.entrySet()) {
                for (int i = 0; i < entry.getValue(); i++) {
                    if (index >= pool.size()) break;
                    roleAssignments.put(pool.get(index), entry.getKey());
                    index++;
                }
            }
        } else {
            // Normal: random semua
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
        }

        // Override forensic scientist (ini tetep jalan kalo ada mode spesifik)
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
                    roleAssignments.put(currentFs, targetOldRole != null ? targetOldRole : Role.INVESTIGATOR);
                }
            }
        }
    }

    private void revealRoles(MinecraftServer server) {
        for (Map.Entry<UUID, Role> entry : roleAssignments.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                Component title = Component.literal("Peran kamu!").withStyle(ChatFormatting.GOLD);
                Component subtitle = Component.literal(entry.getValue().getDisplayName())
                        .withStyle(ChatFormatting.YELLOW);
                sendTitle(player, title, subtitle, 5, 50, 15);
                playSoundTo(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 3.0F, 1.0F);

                player.sendSystemMessage(Component.literal("Peran kamu adalah: " + entry.getValue().getDisplayName())
                        .withStyle(ChatFormatting.GOLD));
                player.sendSystemMessage(Component.literal(RoleDescriptions.get(entry.getValue()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static final int NIGHT_PRECLOSE_DELAY = 60;

    private final Set<UUID> nightPendingBlindfold = new HashSet<>();

    private void startNightSequence(MinecraftServer server) {
        this.state = State.NIGHT;
        this.nightTicksElapsed = 0;
        this.murdererConfirmed = false;
        this.murdererSelectedMeansBacking = null;
        this.murdererSelectedClueBacking = null;

        UUID fsUuid = null;
        nightKillers.clear();
        nightWitnessUuid = null;
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.FORENSIC_SCIENTIST) fsUuid = e.getKey();
            else if (e.getValue() == Role.MURDERER || e.getValue() == Role.ACCOMPLICE) nightKillers.add(e.getKey());
            else if (e.getValue() == Role.WITNESS) nightWitnessUuid = e.getKey();
        }

        nightKillersWakeAt = NIGHT_PRECLOSE_DELAY + NIGHT_CLOSE_HOLD;
        
        boolean hasWitness = nightWitnessUuid != null;
        nightWitnessRevealAt = Integer.MAX_VALUE;
        nightWitnessEndAt = Integer.MAX_VALUE;
        nightWakeAllAt = Integer.MAX_VALUE;
        nightDoneAt = Integer.MAX_VALUE;

        nightBlindfolded.clear();
        nightPendingBlindfold.clear();
        for (UUID uuid : registeredPlayers) {
            if (uuid.equals(fsUuid)) continue;
            nightPendingBlindfold.add(uuid);
        }

        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal("Semua orang tutup mata").withStyle(ChatFormatting.DARK_GRAY),
                Component.empty(), 10, NIGHT_PRECLOSE_DELAY + NIGHT_CLOSE_HOLD - 20, 10);
    }

    private void tickNightSequence() {
        nightTicksElapsed++;

        for (UUID uuid : nightBlindfolded) {
            ServerPlayer bp = serverRef.getPlayerList().getPlayer(uuid);
            if (bp != null && bp.getItemBySlot(EquipmentSlot.HEAD).getItem() != Items.NOTE_BLOCK) {
                bp.setItemSlot(EquipmentSlot.HEAD, createBlindfoldItem());
            }
        }

        if (nightTicksElapsed == NIGHT_PRECLOSE_DELAY) {
            for (UUID uuid : nightPendingBlindfold) {
                ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
                if (p != null) {
                    putBlindfold(p);
                    nightBlindfolded.add(uuid);
                }
            }
        }

        if (nightTicksElapsed == nightKillersWakeAt) {
            String killerLabel = nightKillers.size() > 1 ? "Murderer & Accomplice" : "Murderer";
            com.deception.network.ModNetworking.broadcastNightTitle(
                    Component.literal(killerLabel + " buka mata").withStyle(ChatFormatting.RED),
                    Component.empty(), 10, 40, 10);
            
            for (UUID uuid : nightKillers) {
                ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
                if (p != null) {
                    removeBlindfold(p);
                    nightBlindfolded.remove(uuid);
                    if (roleAssignments.get(uuid) == Role.MURDERER) {
                        giveConfirmHead(p);
                    }
                }
            }
            com.deception.network.ModNetworking.broadcastNightActionBar(
                    Component.literal("Menunggu murderer memilih item").withStyle(ChatFormatting.RED));
        }

        for (UUID uuid : nightKillers) {
            ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
            if (p != null && roleAssignments.get(uuid) == Role.MURDERER && !murdererConfirmed) {
                lockMurdererInventory(p);
            }
        }

        if (nightTicksElapsed == nightWitnessRevealAt && nightWitnessRevealAt != Integer.MAX_VALUE) {
            com.deception.network.ModNetworking.broadcastNightActionBar(Component.empty());
            
            if (nightWitnessUuid != null) {
                ServerPlayer witnessPlayer = serverRef.getPlayerList().getPlayer(nightWitnessUuid);
                if (witnessPlayer != null) {
                    removeBlindfold(witnessPlayer);
                    nightBlindfolded.remove(nightWitnessUuid);
                }
                com.deception.network.ModNetworking.broadcastNightTitle(
                        Component.literal("Perhatikan baik-baik").withStyle(ChatFormatting.AQUA),
                        Component.empty(), 10, NIGHT_WITNESS_HOLD, 10);
                
                for (UUID uuid : nightKillers) {
                    ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
                    if (p != null) applyGlow(p);
                }
            }
        }

        if (nightTicksElapsed == nightWitnessEndAt && nightWitnessEndAt != Integer.MAX_VALUE) {
            if (nightWitnessUuid != null) {
                for (UUID uuid : nightKillers) {
                    ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
                    if (p != null) removeGlow(p);
                }
                ServerPlayer witnessPlayer = serverRef.getPlayerList().getPlayer(nightWitnessUuid);
                if (witnessPlayer != null) {
                    putBlindfold(witnessPlayer);
                    nightBlindfolded.add(nightWitnessUuid);
                }
            }
        }

        if (nightTicksElapsed == nightWakeAllAt && nightWakeAllAt != Integer.MAX_VALUE) {
            for (UUID uuid : new ArrayList<>(nightBlindfolded)) {
                ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
                if (p != null) removeBlindfold(p);
            }
            nightBlindfolded.clear();
            com.deception.network.ModNetworking.broadcastNightTitle(
                    Component.literal("Semua orang bangun").withStyle(ChatFormatting.GOLD),
                    Component.empty(), 10, NIGHT_WAKE_HOLD, 10);
        }

        if (nightTicksElapsed >= nightDoneAt && nightDoneAt != Integer.MAX_VALUE) {
            state = State.DISCUSS;
            discussTicksLeft = discussTimerSeconds * 20;
            broadcast(serverRef, Component.literal("Diskusi dimulai.").withStyle(ChatFormatting.GREEN));
        }
    }

    private void putBlindfold(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, createBlindfoldItem());
        com.deception.network.ModNetworking.sendBlindfoldState(player, true);
    }

    private void removeBlindfold(ServerPlayer player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.NOTE_BLOCK) {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        com.deception.network.ModNetworking.sendBlindfoldState(player, false);
    }

    private ItemStack createBlindfoldItem() {
        ItemStack helmet = new ItemStack(Items.NOTE_BLOCK);
        helmet.setHoverName(Component.literal("Mata Tertutup").withStyle(ChatFormatting.DARK_GRAY));
        return helmet;
    }

    private void giveConfirmHead(ServerPlayer player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.setHoverName(Component.literal("Konfirmasi Pilihan").withStyle(ChatFormatting.GREEN));

        GameProfile fakeProfile = new GameProfile(UUID.randomUUID(), "confirm");
        fakeProfile.getProperties().put("textures", new Property("textures", MURDERER_CONFIRM_HEAD_TEXTURE));

        CompoundTag tag = head.getOrCreateTag();
        tag.put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), fakeProfile));
        tag.putBoolean(CONFIRM_HEAD_TAG, true);

        player.getInventory().add(head);
    }

    public boolean isConfirmHead(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!stack.hasTag()) return false;
        return stack.getTag().getBoolean(CONFIRM_HEAD_TAG);
    }

    public void lockMurdererInventory(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (roleAssignments.get(uuid) != Role.MURDERER) return;
        if (murdererConfirmed) return;
        if (state != State.NIGHT) return;
        
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isConfirmHead(stack)) {
                if (i != 0) {
                    ItemStack mainHand = player.getInventory().items.get(0);
                    player.getInventory().items.set(0, stack);
                    player.getInventory().items.set(i, mainHand);
                    player.sendSystemMessage(Component.literal("Kepala konfirmasi tidak bisa dipindah!").withStyle(ChatFormatting.RED));
                }
                break;
            }
        }
    }

    public boolean canDropItem(ServerPlayer player, ItemStack stack) {
        if (state != State.NIGHT) return true;
        if (roleAssignments.get(player.getUUID()) != Role.MURDERER) return true;
        if (murdererConfirmed) return true;
        if (isConfirmHead(stack)) return false;
        return true;
    }

    public boolean canMoveItem(ServerPlayer player, ItemStack stack) {
        if (state != State.NIGHT) return true;
        if (roleAssignments.get(player.getUUID()) != Role.MURDERER) return true;
        if (murdererConfirmed) return true;
        if (isConfirmHead(stack)) return false;
        return true;
    }

    public boolean isMurdererConfirmed() {
        return murdererConfirmed;
    }

    /**
     * Dipanggil dari event handler pas ServerPlayer klik kanan sebuah block.
     */
    public boolean onMurdererClickClue(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (state != State.NIGHT || murdererConfirmed) return false;

        UUID uuid = player.getUUID();
        if (roleAssignments.get(uuid) != Role.MURDERER) return false;
        if (nightBlindfolded.contains(uuid)) return false;

        BlockState clickedState = level.getBlockState(pos);
        if (!(clickedState.getBlock() instanceof ClueBlock)) return false;

        UUID clusterOwner = clusterOwnerByPos.get(pos);
        if (clusterOwner == null || !clusterOwner.equals(uuid)) {
            player.sendSystemMessage(Component.literal("Itu bukan cluster kamu.").withStyle(ChatFormatting.RED));
            return true;
        }

        if (clickedState.getValue(ClueBlock.FACE) != AttachFace.WALL) return false;
        Direction facing = clickedState.getValue(ClueBlock.FACING);
        BlockPos backingPos = pos.relative(facing.getOpposite());

        // Tentukan ini means atau clue berdasarkan Y
        boolean isMeans = pos.getY() == MEANS_Y;
        
        if (isMeans) {
            if (murdererSelectedMeansBacking != null && !murdererSelectedMeansBacking.equals(backingPos)) {
                restoreBacking(level, murdererSelectedMeansBacking);
            }
            level.setBlock(backingPos, Blocks.GREEN_CONCRETE.defaultBlockState(), 3);
            murdererSelectedMeansBacking = backingPos;
            player.sendSystemMessage(Component.literal("Means dipilih (" + getItemName(level, pos) + ").").withStyle(ChatFormatting.GREEN));
        } else {
            if (murdererSelectedClueBacking != null && !murdererSelectedClueBacking.equals(backingPos)) {
                restoreBacking(level, murdererSelectedClueBacking);
            }
            level.setBlock(backingPos, Blocks.GREEN_CONCRETE.defaultBlockState(), 3);
            murdererSelectedClueBacking = backingPos;
            player.sendSystemMessage(Component.literal("Clue dipilih (" + getItemName(level, pos) + ").").withStyle(ChatFormatting.GREEN));
        }

        boolean bothSelected = (murdererSelectedMeansBacking != null && murdererSelectedClueBacking != null);
        if (bothSelected) {
            player.sendSystemMessage(Component.literal("Keduanya sudah dipilih! Klik kanan kepala konfirmasi.").withStyle(ChatFormatting.GOLD));
        } else {
            String next = murdererSelectedMeansBacking == null ? "means" : "clue";
            player.sendSystemMessage(Component.literal("Pilih " + next + " juga, atau klik kanan kepala konfirmasi.").withStyle(ChatFormatting.YELLOW));
        }
        return true;
    }

    private String getItemName(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ClueBlock) {
            ItemStack stack = new ItemStack(state.getBlock().asItem());
            return stack.getHoverName().getString();
        }
        return "item";
    }

    /**
     * Dipanggil dari event handler pas ServerPlayer klik kanan (di udara)
     * pake item yang ditandai CONFIRM_HEAD_TAG.
     */
    public boolean onMurdererConfirmHead(ServerPlayer player, ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().getBoolean(CONFIRM_HEAD_TAG)) return false;
        if (state != State.NIGHT) return false;

        UUID uuid = player.getUUID();
        if (roleAssignments.get(uuid) != Role.MURDERER) return false;
        if (murdererConfirmed) return false;

        if (murdererSelectedMeansBacking == null || murdererSelectedClueBacking == null) {
            String missing = murdererSelectedMeansBacking == null ? "means" : "clue";
            player.sendSystemMessage(Component.literal("Pilih " + missing + " dulu sebelum konfirmasi.").withStyle(ChatFormatting.RED));
            return true;
        }

        murdererConfirmed = true;
        stack.shrink(1);

        boolean hasWitness = nightWitnessUuid != null;
        if (hasWitness) {
            nightWitnessRevealAt = nightTicksElapsed + 5;
            nightWitnessEndAt = nightWitnessRevealAt + NIGHT_WITNESS_HOLD;
            nightWakeAllAt = nightWitnessEndAt;
            nightDoneAt = nightWakeAllAt + NIGHT_WAKE_HOLD;
            broadcast(serverRef, Component.literal("Murderer telah memilih itemnya.").withStyle(ChatFormatting.GRAY));
        } else {
            nightWakeAllAt = nightTicksElapsed + 5;
            nightDoneAt = nightWakeAllAt + NIGHT_WAKE_HOLD;
        }

        player.sendSystemMessage(Component.literal("Pilihan dikonfirmasi!").withStyle(ChatFormatting.GREEN));
        return true;
    }

    private void restoreBacking(ServerLevel level, BlockPos pos) {
        Block original = pos.getY() == MEANS_Y ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE;
        level.setBlock(pos, original.defaultBlockState(), 3);
    }

    private void applyGlow(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false));
    }

    private void removeGlow(ServerPlayer player) {
        player.removeEffect(MobEffects.GLOWING);
    }

    private void sendTitleTo(MinecraftServer server, Collection<UUID> uuids, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        for (UUID uuid : uuids) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) sendTitle(p, title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    private void sendActionBarTo(MinecraftServer server, Collection<UUID> uuids, Component message) {
        for (UUID uuid : uuids) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.connection.send(new ClientboundSetActionBarTextPacket(message));
        }
    }

    private void showCountdownTitle(int secondsLeft) {
        Component title = Component.literal("Game dimulai dalam").withStyle(ChatFormatting.GOLD);
        Component subtitle = Component.literal(String.valueOf(secondsLeft)).withStyle(ChatFormatting.YELLOW);
        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = serverRef.getPlayerList().getPlayer(uuid);
            if (player != null) {
                sendTitle(player, title, subtitle, 0, 20, 5);
                playSoundTo(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 3.0F, 1.0F);
            }
        }
    }

    private void spinRoleTitle() {
        if (activeRolePool.isEmpty() || shuffleTicksLeft % 2 != 0) {
            return;
        }

        Role randomRole = activeRolePool.get(shuffleRandom.nextInt(activeRolePool.size()));
        Component subtitle = Component.literal(randomRole.getDisplayName())
                .withStyle(ChatFormatting.YELLOW);

        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = serverRef.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                playSoundTo(player, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 3.0F, 1.2F);
            }
        }
    }

    private void sendTitle(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private void playSoundTo(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, SoundSource source, float volume, float pitch) {
        player.playNotifySound(sound, source, volume, pitch);
    }

    private void teleportPlayersAndDecorateArena(MinecraftServer server) {
        ServerLevel level = server.overworld();

        List<UUID> allPlayers = new ArrayList<>(registeredPlayers);
        for (UUID uuid : allPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.teleportTo(ARENA_TELEPORT_POS.getX() + 0.5, ARENA_TELEPORT_POS.getY(),
                        ARENA_TELEPORT_POS.getZ() + 0.5);
            }
        }

        List<UUID> players = new ArrayList<>(allPlayers);
        players.removeIf(uuid -> roleAssignments.get(uuid) == Role.FORENSIC_SCIENTIST);

        List<String> meansPool = new ArrayList<>();
        List<String> cluePool = new ArrayList<>();
        for (String id : ModBlocks.CLUE_IDS) {
            if (id.startsWith("means_")) meansPool.add(id);
            else cluePool.add(id);
        }
        Collections.shuffle(meansPool, shuffleRandom);
        Collections.shuffle(cluePool, shuffleRandom);
        int meansIndex = 0;
        int clueIndex = 0;
        int playerIndex = 0;

        int[] clusterCounts = computeClusterCountsPerWall(players.size());

        for (int wallIdx = 0; wallIdx < ARENA_WALLS.length; wallIdx++) {
            WallSpec wall = ARENA_WALLS[wallIdx];

            for (int[] columns : clusterColumnGroups(wall.length(), clusterCounts[wallIdx])) {
                if (playerIndex >= players.size()) break;
                UUID ownerUuid = players.get(playerIndex++);
                ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);

                List<ItemStack> clusterMeans = new ArrayList<>();
                List<ItemStack> clusterClues = new ArrayList<>();

                for (int offset : columns) {
                    if (meansIndex >= meansPool.size() || clueIndex >= cluePool.size()) break;

                    int x = wall.alongX() ? wall.center() + offset : wall.fixedCoord();
                    int z = wall.alongX() ? wall.fixedCoord() : wall.center() + offset;

                    BlockPos meansPos = new BlockPos(x, MEANS_Y, z);
                    BlockPos cluePos = new BlockPos(x, CLUE_Y, z);
                    String meansId = meansPool.get(meansIndex++);
                    String clueId = cluePool.get(clueIndex++);

                    placeOnWall(level, meansId, meansPos, wall.facing());
                    placeOnWall(level, clueId, cluePos, wall.facing());

                    clusterOwnerByPos.put(meansPos, ownerUuid);
                    clusterOwnerByPos.put(cluePos, ownerUuid);

                    clusterMeans.add(new ItemStack(ModBlocks.CLUE_BLOCKS.get(meansId).get()));
                    clusterClues.add(new ItemStack(ModBlocks.CLUE_BLOCKS.get(clueId).get()));

                    Direction backingDir = wall.facing().getOpposite();
                    level.setBlock(meansPos.relative(backingDir), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                    level.setBlock(cluePos.relative(backingDir), Blocks.BLUE_CONCRETE.defaultBlockState(), 3);
                }

                int mid = columns[columns.length / 2];

                int cx = wall.alongX() ? wall.center() + mid : wall.fixedCoord();
                int cz = wall.alongX() ? wall.fixedCoord() : wall.center() + mid;

                CLUSTERS.put(
                    new BlockPos(cx, CLUE_Y, cz),
                    new ClusterData(
                        ownerUuid,
                        getPlayerName(ownerUuid),
                        new BlockPos(cx, CLUE_Y, cz),
                        clusterMeans,
                        clusterClues
                    )
                );

                spawnOwnerHead(level, ownerUuid, getPlayerName(ownerUuid), owner, wall, columns);
            }
        }
    }

    public void refreshOwnerHeadSkin(MinecraftServer server, UUID ownerUuid) {
        if (state == State.IDLE) return;
        HeadPlacement placement = headPlacementByOwner.get(ownerUuid);
        if (placement == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid);
        if (player == null) return;

        spawnOwnerHead(server.overworld(), ownerUuid, getPlayerName(ownerUuid), player, placement.wall(), placement.columns());
    }

    private void placeOnWall(ServerLevel level, String id, BlockPos pos, Direction facing) {
        RegistryObject<Block> holder = ModBlocks.CLUE_BLOCKS.get(id);
        if (holder == null) return;
        BlockState state = holder.get().defaultBlockState()
                .setValue(ClueBlock.FACE, AttachFace.WALL)
                .setValue(ClueBlock.FACING, facing);
        level.setBlock(pos, state, 3);
    }

    private void spawnOwnerHead(ServerLevel level, UUID ownerUuid, String ownerName, ServerPlayer ownerOnline, WallSpec wall, int[] columns) {
        headPlacementByOwner.put(ownerUuid, new HeadPlacement(wall, columns));

        List<UUID> previousEntities = headEntitiesByOwner.remove(ownerUuid);
        if (previousEntities != null) {
            for (UUID id : previousEntities) {
                var old = level.getEntity(id);
                if (old != null) old.discard();
                spawnedHeadEntities.remove(id);
            }
        }
        List<UUID> ownEntities = new ArrayList<>();
        headEntitiesByOwner.put(ownerUuid, ownEntities);

        double centerOffset = columns[0] + CLUSTER_WIDTH / 2.0;

        Direction backingDir = wall.facing().getOpposite();
        int backingStep = wall.alongX() ? backingDir.getStepZ() : backingDir.getStepX();
        int facingStep = -backingStep;
        double fixedCoordFlush = wall.fixedCoord() + Math.max(backingStep, 0) + facingStep * HEAD_SURFACE_INSET;

        double headX = wall.alongX() ? wall.center() + centerOffset : fixedCoordFlush;
        double headZ = wall.alongX() ? fixedCoordFlush : wall.center() + centerOffset;
        double headY = HEAD_Y + 0.5;

        ItemStack headStack = new ItemStack(Items.PLAYER_HEAD);
        GameProfile profile = ownerOnline != null
                ? ownerOnline.getGameProfile()
                : new GameProfile(ownerUuid, ownerName);
        headStack.getOrCreateTag().put("SkullOwner",
                NbtUtils.writeGameProfile(new CompoundTag(), profile));

        float yaw = wall.facing().toYRot();
        Quaternionf leftRotation = new Quaternionf();

        CompoundTag entityTag = new CompoundTag();
        entityTag.put("item", headStack.save(new CompoundTag()));
        entityTag.putString("item_display", "fixed");
        entityTag.putString("billboard", "fixed");

        CompoundTag transformationTag = new CompoundTag();
        transformationTag.put("translation", floatList(0F, 0F, 0F));
        transformationTag.put("left_rotation", floatList(leftRotation.x, leftRotation.y, leftRotation.z, leftRotation.w));
        transformationTag.put("right_rotation", floatList(0F, 0F, 0F, 1F));
        transformationTag.put("scale", floatList(0.9F, 0.9F, 0.9F));
        entityTag.put("transformation", transformationTag);

        Display.ItemDisplay display = (Display.ItemDisplay) EntityType.ITEM_DISPLAY.create(level);
        if (display == null) return;
        display.load(entityTag);
        display.addTag("deception_display");

        display.setNoGravity(true);
        display.setYRot(yaw);
        display.setPos(headX, headY, headZ);

        level.addFreshEntity(display);
        spawnedHeadEntities.add(display.getUUID());
        ownEntities.add(display.getUUID());
        
        Display.TextDisplay text = (Display.TextDisplay) EntityType.TEXT_DISPLAY.create(level);
        if (text != null) {
            double offset = 0.08;

            double textX = headX;
            double textY = headY + 0.45;
            double textZ = headZ;

            switch (wall.facing()) {
                case NORTH -> textZ -= offset;
                case SOUTH -> textZ += offset;
                case WEST  -> textX -= offset;
                case EAST  -> textX += offset;
            }

            CompoundTag tag = new CompoundTag();
            tag.putString("text", Component.Serializer.toJson(Component.literal(ownerName)));
            tag.putString("billboard", "fixed");
            tag.putInt("background", 0);
            tag.putInt("line_width", 200);
            tag.putByte("see_through", (byte)1);
            tag.putByte("default_background", (byte)0);
            
            text.load(tag);
            text.addTag("deception_display");
            text.setYRot(yaw);
            text.setPos(textX, textY, textZ);

            level.addFreshEntity(text);
            spawnedHeadEntities.add(text.getUUID());
            ownEntities.add(text.getUUID());
        }
    }

    private static ListTag floatList(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }

    // ---------- Tick loop ----------

    public void tick() {
        globalTickCounter++;
        if (state == State.COUNTDOWN) {
            countdownTicks--;
            int secondsLeft = (int) Math.ceil(countdownTicks / 20.0);
            if (secondsLeft != lastCountdownSecondShown && secondsLeft > 0) {
                lastCountdownSecondShown = secondsLeft;
                showCountdownTitle(secondsLeft);
            }
            if (countdownTicks <= 0) {
                computeRoleAssignments();
                teleportPlayersAndDecorateArena(serverRef);
                state = State.SHUFFLE;
                shuffleTicksLeft = SHUFFLE_DURATION_TICKS;
                activeRolePool = computeActiveRolePool();
                for (UUID uuid : registeredPlayers) {
                    ServerPlayer player = serverRef.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        sendTitle(
                            player,
                            Component.literal("Mengocok Peran").withStyle(ChatFormatting.GOLD),
                            Component.empty(),
                            0, SHUFFLE_DURATION_TICKS, 0
                        );
                    }
                }
            }
        } else if (state == State.SHUFFLE) {
            shuffleTicksLeft--;
            spinRoleTitle();
            if (shuffleTicksLeft <= 0) {
                revealRoles(serverRef);
                startNightSequence(serverRef);
            }
        } else if (state == State.NIGHT) {
            tickNightSequence();
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