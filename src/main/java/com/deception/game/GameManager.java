package com.deception.game;

import com.deception.command.RoleDescriptions;
import com.deception.init.ClusterData;
import com.deception.init.ModBlocks;
import com.deception.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
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
import net.minecraft.nbt.StringTag;
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
        IDLE, COUNTDOWN, SHUFFLE, REVEAL_DELAY, NIGHT, DISCUSS, PRESENTASI, RUNNING
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

    public static final int DEFAULT_DISCUSS_TIMER_SECONDS = 600; // 10 menit
    private int discussTimerSeconds = DEFAULT_DISCUSS_TIMER_SECONDS;

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
    private static final int NIGHT_REVEAL_DELAY = 100;
    private static final int NIGHT_CLOSE_HOLD = 60;    // 3 detik "semua tutup mata" sebelum killer dibangunin
    private static final int NIGHT_WITNESS_HOLD = 60;  // 3 detik witness liat killer nyala (glowing)
    private static final int NIGHT_WAKE_HOLD = 20;     // 1 detik title "semua bangun" sebelum lanjut discuss
    private static final int NIGHT_PRE_WAKE_HOLD = 60; // 3 detik jeda sebelum "Semua orang buka mata" (abis witness tutup mata / abis murderer&accomplice tutup mata kalo gaada witness)

    private int revealDelayTicks = 0;
    private int nightTicksElapsed;
    private int nightKillersWakeAt;
    private int nightWitnessRevealAt;
    private int nightWakeAllAt;
    private int nightDoneAt;
    private final Set<UUID> nightBlindfolded = new HashSet<>();
    private final List<UUID> nightKillers = new ArrayList<>();
    private UUID nightWitnessUuid;
    private boolean murdererConfirmed = false;
    private final Set<BlockPos> processedClicks = new HashSet<>();
    private long lastClickTick = 0;
    private int murdererAutoPickTicksLeft = 0;
    private int witnessAutoSkipTicksLeft = 0;
    private UUID leftMurdererUuid = null;
    private UUID leftWitnessUuid = null;
    private boolean hasMurdererLeft = false;
    private boolean hasWitnessLeft = false;
    private String leftMurdererName = "";
    private String leftWitnessName = "";

    // ---------- Murderer pilih item means/clue asli pas night ----------
    // 2 posisi: 1 untuk means, 1 untuk clue
    private BlockPos murdererSelectedMeansBacking = null;
    private BlockPos murdererSelectedClueBacking = null;
    // posisi block clue asli (bukan backing-nya) buat ambil nama item pas kirim pesan
    private BlockPos murdererSelectedMeansPos = null;
    private BlockPos murdererSelectedCluePos = null;

    // ---------- Witness konfirmasi udah liat (kaya murdererConfirmed tapi buat witness) ----------
    private boolean witnessConfirmed = false;

    // ---------- Handle murderer/witness left pas lagi "buka mata" ----------
    public static final int DEFAULT_OFFLINE_REVEAL_SECONDS = 90;
    // Bisa di-override /deception setting -> Set Timer -> Offline Reveal.
    private int LEAVE_TIMEOUT_TICKS = DEFAULT_OFFLINE_REVEAL_SECONDS * 20;
    private boolean murdererWindowOpen = false; // true selama murderer lagi milih item (mata udah kebuka)
    private boolean witnessWindowOpen = false;  // true selama witness lagi liat killer glowing (mata udah kebuka)
    private int murdererAutoPickAt = Integer.MAX_VALUE;
    private int witnessAutoSkipAt = Integer.MAX_VALUE;
    // Tick pas witness baru boleh /deception confirm (3 detik abis matanya
    // kebuka & killer nyala) & tick pas prompt "[KONFIRMASI]" dikirim ke chat.
    private int witnessConfirmReadyAt = Integer.MAX_VALUE;

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
    // Posisi block investigation_paper -> UUID entitas TextDisplay-nya --
    // dipake despawnInvestigationPaperText biar HAPUS PERSIS satu entitas
    // yang bener (dulu pake radius AABB.inflate, ikut kehapus punya
    // tetangga kalo paper-nya ditempel berdempetan).
    private final Map<BlockPos, UUID> investigationPaperTextByPos = new HashMap<>();
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
        if (role == Role.accomplice) {
            accompliceOverride = enabled;
        } else if (role == Role.witness) {
            witnessOverride = enabled;
        }
    }

    // Lepas override manual balik ke "auto" (ikut tabel RoleComposition) --
    // dipake sama toggle 3-state (Auto/Aktif/Nonaktif) di GUI /deception setting.
    public void clearCustomRoleOverride(Role role) {
        if (role == Role.accomplice) {
            accompliceOverride = null;
        } else if (role == Role.witness) {
            witnessOverride = null;
        }
    }

    public void resetCustomRoleOverrides() {
        accompliceOverride = null;
        witnessOverride = null;
    }

    public Boolean getAccompliceOverride() {
        return accompliceOverride;
    }

    public Boolean getWitnessOverride() {
        return witnessOverride;
    }

    // Nilai yang bakal dipake kalo mode-nya "auto" (belum di-override manual),
    // dihitung dari tabel default RoleComposition sesuai jumlah player teregistrasi.
    public boolean resolveAccompliceDefault() {
        return new RoleComposition(registeredPlayers.size()).isAccompliceEnabled();
    }

    public boolean resolveWitnessDefault() {
        return new RoleComposition(registeredPlayers.size()).isWitnessEnabled();
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

    // Lepas role paksa balik ke "auto" (ikut random shuffle biasa) -- dipake
    // sama opsi "AUTO" di role sub-screen GUI /deception setting.
    public boolean clearPlayerRole(String playerName) {
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
        roleAssignments.remove(targetUuid);
        return true;
    }

    // ---------- Forensic scientist & timer ----------

    public void setForensicScientistMode(String mode) {
        this.forensicScientistMode = mode;
    }

    public String getForensicScientistMode() {
        return forensicScientistMode;
    }

    public void setDiscussTimerSeconds(int seconds) {
        this.discussTimerSeconds = seconds;
    }

    public int getDiscussTimerSeconds() {
        return discussTimerSeconds;
    }

    public void resetDiscussTimerToDefault() {
        this.discussTimerSeconds = DEFAULT_DISCUSS_TIMER_SECONDS;
    }

    // ---------- Role visible (HUD role sendiri di pojok kanan atas) ----------
    // SENGAJA gak direset di abortGame: ini setting server, bukan state game
    // -- sama kayak discussTimerSeconds, sekali diatur ya kepake terus sampe
    // diubah lagi.
    private boolean roleVisible = true;

    public boolean isRoleVisible() {
        return roleVisible;
    }

    /** Nyalain/matiin HUD role, sekalian langsung nerapin ke layar semua peserta. */
    public void setRoleVisible(MinecraftServer server, boolean visible) {
        this.roleVisible = visible;
        syncRoleVisibleHudToAll(server);
    }

    /** Kirim ulang HUD role ke SEMUA peserta yang online -- dipake pas toggle & pas role kelar dibagi. */
    public void syncRoleVisibleHudToAll(MinecraftServer server) {
        if (server == null) return;
        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) sendRoleVisibleHud(player);
        }
    }

    /**
     * Kirim HUD role ke SATU player. Otomatis ngirim perintah clear kalo
     * fiturnya lagi mati / dia belum kebagian role, jadi caller gak perlu
     * ngecek apa-apa.
     */
    public void sendRoleVisibleHud(ServerPlayer player) {
        Role role = roleAssignments.get(player.getUUID());
        if (!roleVisible || role == null) {
            com.deception.network.ModNetworking.sendRoleVisibleHud(player,
                    com.deception.network.RoleVisibleHudPacket.hidden());
            return;
        }
        com.deception.network.ModNetworking.sendRoleVisibleHud(player,
                new com.deception.network.RoleVisibleHudPacket(true, role.getDisplayName(), isMurdererTeam(role)));
    }

    public int getOfflineRevealSeconds() {
        return LEAVE_TIMEOUT_TICKS / 20;
    }

    public void setOfflineRevealSeconds(int seconds) {
        this.LEAVE_TIMEOUT_TICKS = seconds * 20;
    }

    public void resetOfflineRevealToDefault() {
        this.LEAVE_TIMEOUT_TICKS = DEFAULT_OFFLINE_REVEAL_SECONDS * 20;
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

        // Bersihin arena dulu (blok means/clue, head entity, cluster data
        // sisa dari game sebelumnya -- jaga-jaga kalo restore pas
        // stopGame/abortGame kemarin kepotong/gak sempurna) SEBELUM mulai
        // nyusun game baru.
        restoreArena(server);

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
                player.getInventory().clearContent();
                player.removeAllEffects();

                // Sinkronin ulang noteblock (blindfold) & overlay blindfold
                // client -- jaga-jaga ada sisa dari sesi sebelumnya yang
                // belum sempet ke-clear pas player ini join.
                if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.NOTE_BLOCK) {
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                }
                com.deception.network.ModNetworking.sendForceCloseBlindfold(player);

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

    /**
     * Dipanggil PresentationManager pas ada yang menang (confession benar,
     * badge abis semua, atau ronde 3 abis gak ketauan). Broadcast title
     * kemenangan ke semua player (plus subtitle personal "Kamu Menang!"/
     * "Kamu Kalah!" tergantung role dia ada di tim yang menang apa kagak),
     * terus full cleanup kayak stopGame (restore arena, balikin gamemode
     * survival, state balik IDLE).
     */
    public void endGameWithResult(MinecraftServer server, Component title, Component chatMessage, boolean murdererTeamWon) {
        ServerLevel level = ArenaDimension.level(server);
        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                boolean onMurdererTeam = isMurdererTeam(roleAssignments.get(uuid));
                boolean personalWon = onMurdererTeam == murdererTeamWon;
                Component subtitle = personalWon
                        ? Component.literal("Kamu Menang!").withStyle(ChatFormatting.GREEN)
                        : Component.literal("Kamu Kalah!").withStyle(ChatFormatting.RED);
                sendTitle(player, title, subtitle, 10, 100, 20);
                playSoundTo(player, net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                        player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.5, 0.5, 0.08);
            }
        }
        revealAllRoles(server);
        abortGame(server, chatMessage);
    }

    private boolean isMurdererTeam(Role role) {
        return role == Role.murderer || role == Role.accomplice;
    }

    /** Reveal semua role ke chat pas game selesai -- dipanggil endGameWithResult SEBELUM abortGame ngosongin roleAssignments. */
    private void revealAllRoles(MinecraftServer server) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(" \n===== List Role =====").withStyle(ChatFormatting.GOLD), false);
                Component.literal("");
        for (UUID uuid : registeredPlayers) {
            Role role = roleAssignments.get(uuid);
            if (role == null) continue;
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("  " + getPlayerName(uuid) + ": " + role.getDisplayName()).withStyle(ChatFormatting.WHITE), false);
        }
    }

    private void abortGame(MinecraftServer server, Component reason) {
        // Disalin DULUAN: field aslinya di-null-in di bawah (bagian reset
        // state), padahal restore backing-nya baru jalan setelah itu -- kalo
        // baca langsung dari field-nya, yang kebaca selalu null dan
        // restoreBacking gak pernah kepanggil.
        BlockPos selectedMeansBacking = this.murdererSelectedMeansBacking;
        BlockPos selectedClueBacking = this.murdererSelectedClueBacking;

        this.state = State.IDLE;
        this.roleAssignments.clear();
        this.countdownTicks = 0;
        this.lastCountdownSecondShown = -1;
        this.shuffleTicksLeft = 0;
        this.discussTicksLeft = 0;
        this.revealDelayTicks = 0;
        this.nightTicksElapsed = 0;
        this.murdererConfirmed = false;
        this.witnessConfirmed = false;
        this.murdererSelectedMeansBacking = null;
        this.murdererSelectedClueBacking = null;
        this.murdererSelectedMeansPos = null;
        this.murdererSelectedCluePos = null;
        this.nightWitnessRevealAt = Integer.MAX_VALUE;
        this.nightWakeAllAt = Integer.MAX_VALUE;
        this.nightDoneAt = Integer.MAX_VALUE;
        this.murdererWindowOpen = false;
        this.witnessWindowOpen = false;
        this.witnessConfirmReadyAt = Integer.MAX_VALUE;
        this.murdererAutoPickAt = Integer.MAX_VALUE;
        this.witnessAutoSkipAt = Integer.MAX_VALUE;
        this.murdererAutoPickTicksLeft = 0;
        this.witnessAutoSkipTicksLeft = 0;
        this.leftMurdererUuid = null;
        this.leftWitnessUuid = null;
        this.murdererAutoPickTicksLeft = 0;
        this.witnessAutoSkipTicksLeft = 0;
        this.hasMurdererLeft = false;
        this.hasWitnessLeft = false;
        this.leftMurdererName = "";
        this.leftWitnessName = "";

        MinecraftServer target = server != null ? server : serverRef;
        if (target != null) {
            target.setPvpAllowed(true);
            if (previousDifficulty != null) {
                target.setDifficulty(previousDifficulty, true);
            }

            ServerLevel level = ArenaDimension.level(target);
            if (selectedMeansBacking != null) {
                restoreBacking(level, selectedMeansBacking);
            }
            if (selectedClueBacking != null) {
                restoreBacking(level, selectedClueBacking);
            }

            restoreArena(target);

            for (UUID uuid : registeredPlayers) {
                ServerPlayer player = target.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    player.setGameMode(GameType.SURVIVAL);

                    // Bersihin inventory (termasuk armor/noteblock di slot
                    // kepala) & semua effect (glowing dll) yang mungkin
                    // masih nempel pas game dihentikan.
                    player.getInventory().clearContent();
                    player.removeAllEffects();

                    // FORCE CLOSE - langsung ilang tanpa animasi
                    com.deception.network.ModNetworking.sendForceCloseBlindfold(player);

                    // Copot HUD hasil malam-nya FS. Dikirim ke SEMUA player
                    // (bukan cuma yang FS ronde ini) -- murah, dan bikin
                    // layar orang yang pernah jadi FS di game sebelumnya
                    // ikut bersih.
                    com.deception.network.ModNetworking.clearMurderResultHud(player);

                    // Role udah dikosongin di atas, jadi ini otomatis ngirim
                    // clear -- HUD role gak boleh nyangkut abis game selesai.
                    sendRoleVisibleHud(player);

                    // Clear semua title dan actionbar
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 0, 0));
                    player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                    player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));

                    AttributeInstance blockReach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
                    if (blockReach != null) {
                        blockReach.setBaseValue(blockReach.getAttribute().getDefaultValue());
                    }

                    // Paling belakangan: arena ada di dimensi sendiri, jadi
                    // tanpa ini pemain ketinggalan di sana tanpa jalan pulang.
                    sendPlayerHome(target, player);
                }
            }

            // Clear custom actionbar
            com.deception.network.ModNetworking.broadcastNightActionBar(Component.empty());

            nightBlindfolded.clear();
            nightKillers.clear();
            nightWitnessUuid = null;
            broadcast(target, reason);
        }
    }

    private void restoreArena(MinecraftServer server) {
        ServerLevel level = ArenaDimension.level(server);

        for (UUID headId : spawnedHeadEntities) {
            var entity = level.getEntity(headId);
            if (entity != null) {
                entity.discard();
            }
        }
        // Text display-nya investigation paper GAK ikut kecatet di
        // spawnedHeadEntities (cuma di investigationPaperTextByPos), jadi
        // dibersihin lewat map-nya sendiri -- lookup by UUID, gak nyentuh
        // koleksi entitas level.
        for (UUID textId : investigationPaperTextByPos.values()) {
            var entity = level.getEntity(textId);
            if (entity != null) {
                entity.discard();
            }
        }
        // Sapu sisa display yang UUID-nya gak kecatet di dua map di atas
        // (misal sisa game yang kepotong crash). KUMPULIN DULU baru discard:
        // getAll() itu VIEW LANGSUNG ke map entitas level-nya (EntityLookup
        // #byId), dan discard() ujung-ujungnya manggil remove() di map yang
        // sama persis. Map-nya fastutil, gak fail-fast -- jadi gak bakal
        // error, iteratornya cuma nyasar di linked-list yang udah berubah
        // dan sebagian entitas kelewat gak kehapus.
        List<Entity> strayDisplays = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity.getTags().contains("deception_display")) {
                strayDisplays.add(entity);
            }
        }
        for (Entity entity : strayDisplays) {
            entity.discard();
        }
        spawnedHeadEntities.clear();
        headPlacementByOwner.clear();
        headEntitiesByOwner.clear();
        CLUSTERS.clear();
        clusterOwnerByPos.clear();
        pendingForensicScientistUuid = null;
        pendingForensicPaperCategories = null;
        forensicPapersGivenThisRound = false;
        investigationPaperTextByPos.clear();
        PresentationManager.get().reset(server);

        resetArenaBlocks(level);
        resetForensicBoard(level);
    }

    // Board tempat Forensic Scientist naro investigation paper yang udah
    // dipilih (di atas black_wool) -- bukan bagian dari ARENA_WALLS, jadi
    // dibersihin manual di sini pas stopgame/restoreArena.
    private static final BlockPos FORENSIC_BOARD_MIN = new BlockPos(107, -58, 175);
    private static final BlockPos FORENSIC_BOARD_MAX = new BlockPos(108, -56, 182);

    private void resetForensicBoard(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(FORENSIC_BOARD_MIN, FORENSIC_BOARD_MAX)) {
            if (level.getBlockState(pos).is(ModBlocks.INVESTIGATION_PAPER.get())) {
                level.setBlock(pos.immutable(), air, 3);
            }
        }
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

    // ---------- Titik baris-berbaris fase shootout ----------
    // Dipake PresentationManager#startWitnessShootout: semua calon target
    // dijejerin di depan cluster-nya MASING-MASING (jadi kebaca siapa
    // siapa, tapi murderer tetep gak tau yang mana witness-nya), FS dipisah
    // ke depan board biar gak ikut kena garis tembak.

    /** Jarak berdiri dari muka dinding cluster -- cukup maju biar badannya gak nempel/nembus dinding. */
    private static final double LINEUP_DISTANCE_FROM_WALL = 2.5;

    /** Satu titik berdiri + arah hadap. Yaw-nya bikin playernya ngadep ke tengah arena (ke arah murderer). */
    public record LineupSpot(double x, double y, double z, float yaw) {}

    /**
     * Titik berdiri di depan cluster punya {@code ownerUuid}. Null kalo dia
     * gak punya cluster sama sekali (FS, atau player yang offline pas arena
     * dibangun) -- caller yang mutusin fallback-nya.
     */
    public LineupSpot getClusterLineupSpot(UUID ownerUuid) {
        HeadPlacement placement = headPlacementByOwner.get(ownerUuid);
        if (placement == null) return null;

        WallSpec wall = placement.wall();
        // Sejajar sama titik kepala owner-nya (lihat spawnOwnerHead), biar
        // dia beneran berdiri persis di depan cluster punya dia sendiri.
        double alongWall = wall.center() + placement.columns()[0] + CLUSTER_WIDTH / 2.0;

        Direction backingDir = wall.facing().getOpposite();
        int backingStep = wall.alongX() ? backingDir.getStepZ() : backingDir.getStepX();
        int facingStep = -backingStep;
        // Muka dinding: kalo backing-nya ke arah koordinat NAIK, muka blok-nya
        // ada di sisi fixedCoord+1; kalo backing-nya ke arah TURUN, di fixedCoord.
        double wallFace = wall.fixedCoord() + Math.max(backingStep, 0);
        double awayFromWall = wallFace + facingStep * LINEUP_DISTANCE_FROM_WALL;

        double x = wall.alongX() ? alongWall : awayFromWall;
        double z = wall.alongX() ? awayFromWall : alongWall;
        return new LineupSpot(x, ARENA_TELEPORT_POS.getY(), z, wall.facing().toYRot());
    }

    /**
     * Titik berdiri FS: di depan board, ngadep board (arah -X, board-nya ada
     * di sisi barat arena). Sengaja dipisah jauh dari barisan cluster biar FS
     * gak kena peluru nyasar.
     */
    public LineupSpot getForensicBoardSpot() {
        double x = FORENSIC_BOARD_MAX.getX() + 3.5;
        double z = (FORENSIC_BOARD_MIN.getZ() + FORENSIC_BOARD_MAX.getZ() + 1) / 2.0;
        return new LineupSpot(x, ARENA_TELEPORT_POS.getY(), z, Direction.WEST.toYRot());
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

    /**
     * Urutan giliran presentasi: clockwise (dilihat dari atas) berdasarkan
     * posisi cluster asli tiap player (bukan urutan ARENA_WALLS mentah,
     * yang gak nurut fisik keliling arena), lalu di-rotate biar mulai dari
     * cluster kolom pertama di dinding "kanan" (headPlacementByOwner udah
     * nyimpen wall+kolom tiap owner dari teleportPlayersAndDecorateArena).
     */
    public List<UUID> computeClockwisePresentationOrder() {
        if (CLUSTERS.isEmpty()) return List.of();

        AABB bounds = computeArenaBounds();
        double centerX = (bounds.minX + bounds.maxX) / 2.0;
        double centerZ = (bounds.minZ + bounds.maxZ) / 2.0;

        List<ClusterData> clusters = new ArrayList<>(CLUSTERS.values());
        // Bearing ala kompas: atan2(komponen timur, komponen utara) naik
        // searah jarum jam (utara->timur->selatan->barat).
        clusters.sort(Comparator.comparingDouble(c -> {
            double east = c.center.getX() - centerX;
            double north = centerZ - c.center.getZ();
            return Math.atan2(east, north);
        }));

        List<UUID> order = new ArrayList<>();
        for (ClusterData c : clusters) {
            if (!order.contains(c.owner)) order.add(c.owner);
        }

        UUID startUuid = null;
        int minCol = Integer.MAX_VALUE;
        for (Map.Entry<UUID, HeadPlacement> e : headPlacementByOwner.entrySet()) {
            HeadPlacement placement = e.getValue();
            if (placement.wall().label().equals("kanan") && placement.columns().length > 0
                    && placement.columns()[0] < minCol) {
                minCol = placement.columns()[0];
                startUuid = e.getKey();
            }
        }
        if (startUuid != null) {
            int idx = order.indexOf(startUuid);
            if (idx > 0) {
                Collections.rotate(order, -idx);
            }
        }
        return order;
    }

    public void forceCleanupOnStartup(MinecraftServer server) {
        this.state = State.IDLE;
        restoreArena(server);
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
                    counts.put(Role.investigator, counts.getOrDefault(Role.investigator, 0) - 1);
                    if (counts.get(Role.investigator) < 0) counts.put(Role.investigator, 0);
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
                    if (e.getValue() == Role.forensic_scientist) {
                        currentFs = e.getKey();
                        break;
                    }
                }
                if (currentFs != null && !currentFs.equals(targetFs)) {
                    Role targetOldRole = roleAssignments.get(targetFs);
                    roleAssignments.put(targetFs, Role.forensic_scientist);
                    roleAssignments.put(currentFs, targetOldRole != null ? targetOldRole : Role.investigator);
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
                player.sendSystemMessage(Component.literal(""));
                player.sendSystemMessage(Component.literal("  Peran kamu adalah: " + entry.getValue().getDisplayName())
                        .withStyle(ChatFormatting.GOLD));
                player.sendSystemMessage(Component.literal(""));
                player.sendSystemMessage(Component.literal(RoleDescriptions.get(entry.getValue()))
                        .withStyle(ChatFormatting.WHITE));
            }
        }

        // Role baru kebagi -- baru sekarang HUD-nya ada isinya (lihat
        // sendRoleVisibleHud; sebelum ini dia cuma bakal ngirim clear).
        syncRoleVisibleHudToAll(server);
    }

    private static final int NIGHT_PRECLOSE_DELAY = 60;

    private final Set<UUID> nightPendingBlindfold = new HashSet<>();

    private void startNightSequence(MinecraftServer server) {
        this.state = State.NIGHT;
        this.nightTicksElapsed = 0;
        this.murdererConfirmed = false;
        this.witnessConfirmed = false;
        this.murdererSelectedMeansBacking = null;
        this.murdererSelectedClueBacking = null;
        this.murdererSelectedMeansPos = null;
        this.murdererSelectedCluePos = null;
        this.murdererWindowOpen = false;
        this.witnessWindowOpen = false;
        this.witnessConfirmReadyAt = Integer.MAX_VALUE;
        this.murdererAutoPickAt = Integer.MAX_VALUE;
        this.witnessAutoSkipAt = Integer.MAX_VALUE;

        UUID fsUuid = null;
        nightKillers.clear();
        nightWitnessUuid = null;
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.forensic_scientist) fsUuid = e.getKey();
            else if (e.getValue() == Role.murderer || e.getValue() == Role.accomplice) nightKillers.add(e.getKey());
            else if (e.getValue() == Role.witness) nightWitnessUuid = e.getKey();
        }

        nightKillersWakeAt = NIGHT_PRECLOSE_DELAY + NIGHT_CLOSE_HOLD;
        
        boolean hasWitness = nightWitnessUuid != null;
        nightWitnessRevealAt = Integer.MAX_VALUE;
        nightWakeAllAt = Integer.MAX_VALUE;
        nightDoneAt = Integer.MAX_VALUE;

        nightBlindfolded.clear();
        nightPendingBlindfold.clear();
        for (UUID uuid : registeredPlayers) {
            if (uuid.equals(fsUuid)) continue;
            nightPendingBlindfold.add(uuid);
        }

        // PAKE TITLE BAWAAN MINECRAFT buat "Semua orang tutup mata"
        // Ini akan ikut ketutup sama eyelid overlay
        Component title = Component.literal("Semua orang tutup mata").withStyle(ChatFormatting.RED);
        Component subtitle = Component.empty();
        int fadeIn = 10;
        int stay = NIGHT_PRECLOSE_DELAY + NIGHT_CLOSE_HOLD - 20;
        int fadeOut = 10;
        
        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
            }
        }
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
                }
            }
            com.deception.network.ModNetworking.broadcastNightActionBar(
                    Component.literal("Menunggu murderer memilih item").withStyle(ChatFormatting.RED));
            murdererWindowOpen = true;

            checkMurdererWakeUpOffline();
        }

        if (nightTicksElapsed == murdererAutoPickAt && murdererAutoPickAt != Integer.MAX_VALUE) {
            murdererAutoPickAt = Integer.MAX_VALUE;
            if (autoPickAndConfirmMurderer()) {
                closeKillersEyesAndScheduleNext();
            }
        }

        if (nightTicksElapsed == witnessAutoSkipAt && witnessAutoSkipAt != Integer.MAX_VALUE) {
            finalizeWitnessConfirm();
        }

        if (nightTicksElapsed == nightWitnessRevealAt && nightWitnessRevealAt != Integer.MAX_VALUE) {
            openWitnessWindow();
        }

        if (nightTicksElapsed == witnessConfirmReadyAt && witnessConfirmReadyAt != Integer.MAX_VALUE) {
            witnessConfirmReadyAt = Integer.MAX_VALUE;
            if (witnessWindowOpen && !witnessConfirmed && nightWitnessUuid != null) {
                ServerPlayer wp = serverRef.getPlayerList().getPlayer(nightWitnessUuid);
                if (wp != null) {
                    wp.sendSystemMessage(Component.literal("Selesai melihat? ").withStyle(ChatFormatting.AQUA)
                            .append(confirmButton()));
                }
            }
        }

        // Countdown untuk auto-pick murderer
        if (murdererAutoPickAt != Integer.MAX_VALUE && murdererWindowOpen && !murdererConfirmed) {
            murdererAutoPickTicksLeft = murdererAutoPickAt - nightTicksElapsed;
            if (murdererAutoPickTicksLeft > 0 && murdererAutoPickTicksLeft % 20 == 0) {
                updateAutoPickCountdown();
            }
        }

        // Countdown untuk auto-skip witness
        if (witnessAutoSkipAt != Integer.MAX_VALUE && witnessWindowOpen && !witnessConfirmed) {
            witnessAutoSkipTicksLeft = witnessAutoSkipAt - nightTicksElapsed;
            if (witnessAutoSkipTicksLeft > 0 && witnessAutoSkipTicksLeft % 20 == 0) {
                updateWitnessSkipCountdown();
            }
        }

        if (nightTicksElapsed == nightWakeAllAt && nightWakeAllAt != Integer.MAX_VALUE) {
            for (UUID uuid : new ArrayList<>(nightBlindfolded)) {
                ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
                if (p != null) removeBlindfold(p);
            }
            nightBlindfolded.clear();
            com.deception.network.ModNetworking.broadcastNightTitle(
                    Component.literal("Semua orang buka mata").withStyle(ChatFormatting.GOLD),
                    Component.empty(), 10, NIGHT_WAKE_HOLD, 10);

            UUID fsUuid = null;
            for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
                if (e.getValue() == Role.forensic_scientist) { fsUuid = e.getKey(); break; }
            }
            giveForensicScientistPapers(serverRef, fsUuid);
            broadcastForensicWaitActionBar();
        }

        // Titik-titiknya jalan sepanjang jeda "semua orang buka mata"
        // (nightWakeAllAt fired, tapi belum reset ke MAX_VALUE) sampe diskusi mulai.
        if (nightDoneAt != Integer.MAX_VALUE && nightTicksElapsed > nightWakeAllAt
                && globalTickCounter % 10 == 0) {
            broadcastForensicWaitActionBar();
        }

        if (nightTicksElapsed >= nightDoneAt && nightDoneAt != Integer.MAX_VALUE) {
            state = State.DISCUSS;
            discussTicksLeft = discussTimerSeconds * 20;
            broadcast(serverRef, Component.literal("═══════════════════════").withStyle(ChatFormatting.GOLD));
            broadcast(serverRef, Component.literal(""));
            broadcast(serverRef, Component.literal("  🔪 GAME DIMULAI 🔪").withStyle(ChatFormatting.GREEN));
            broadcast(serverRef, Component.literal("  Cari & tangkap pembunuh misterius itu, \n  bawa saksi mata untuk melapor.").withStyle(ChatFormatting.YELLOW));
            broadcast(serverRef, Component.literal("  ").append(discordIcon()).append(Component.literal(" @aflahall").withStyle(ChatFormatting.AQUA)).append(Component.literal(" Dev Of ").withStyle(ChatFormatting.WHITE).append(Component.literal("@corazonid").withStyle(ChatFormatting.AQUA))));
            broadcast(serverRef, Component.literal(""));
            broadcast(serverRef, Component.literal("═══════════════════════").withStyle(ChatFormatting.GOLD));
            // broadcast(Component.text(" "));
            // broadcast(Component.text("  🤫 GAME DIMULAI 🤫", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            // broadcast(Component.text("  Jaga & bantu merlin mendapatkan 3 tanaman untuk menang!", NamedTextColor.YELLOW));
            // broadcast(Component.text("  Jangan biarkan kubu jahat menggagalkan misi!", NamedTextColor.RED));
            // broadcast(Component.text("  Plugin By ", NamedTextColor.GREEN).append(Component.text("Aflahal", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
            // broadcast(Component.text(" "));
            // broadcast(Component.text("═══════════════════════", NamedTextColor.GOLD));
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
        helmet.setHoverName(Component.literal("Penutup mata").withStyle(ChatFormatting.RED));
        return helmet;
    }

    public boolean isMurdererConfirmed() {
        return murdererConfirmed;
    }

    /**
     * Dipanggil dari event handler pas ServerPlayer klik kanan sebuah block.
     */
    public boolean onMurdererClickClue(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (state != State.NIGHT || murdererConfirmed) return false;

        // Cegah double processing di tick yang sama
        long currentTick = level.getGameTime();
        if (currentTick == lastClickTick && processedClicks.contains(pos)) {
            return false;
        }
        if (currentTick != lastClickTick) {
            processedClicks.clear();
            lastClickTick = currentTick;
        }
        processedClicks.add(pos);

        UUID uuid = player.getUUID();
        if (roleAssignments.get(uuid) != Role.murderer) return false;
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

        boolean isMeans = pos.getY() == MEANS_Y;
        
        if (isMeans) {
            if (murdererSelectedMeansBacking != null && !murdererSelectedMeansBacking.equals(backingPos)) {
                restoreBacking(level, murdererSelectedMeansBacking);
            }
            level.setBlock(backingPos, Blocks.GREEN_CONCRETE.defaultBlockState(), 3);
            murdererSelectedMeansBacking = backingPos;
            murdererSelectedMeansPos = pos;
            player.sendSystemMessage(Component.literal("Means dipilih (" + getItemName(level, pos) + ").").withStyle(ChatFormatting.GREEN));
        } else {
            if (murdererSelectedClueBacking != null && !murdererSelectedClueBacking.equals(backingPos)) {
                restoreBacking(level, murdererSelectedClueBacking);
            }
            level.setBlock(backingPos, Blocks.GREEN_CONCRETE.defaultBlockState(), 3);
            murdererSelectedClueBacking = backingPos;
            murdererSelectedCluePos = pos;
            player.sendSystemMessage(Component.literal("Clue dipilih (" + getItemName(level, pos) + ").").withStyle(ChatFormatting.GREEN));
        }

        boolean bothSelected = (murdererSelectedMeansBacking != null && murdererSelectedClueBacking != null);
        if (bothSelected) {
            player.sendSystemMessage(Component.literal("Keduanya sudah dipilih! ").withStyle(ChatFormatting.GOLD)
                    .append(confirmButton()));
        } else {
            String next = murdererSelectedMeansBacking == null ? "means" : "clue";
            player.sendSystemMessage(Component.literal("Pilih " + next + " untuk konfirmasi.").withStyle(ChatFormatting.YELLOW));
        }
        return true;
    }

    /**
     * Tempel means & clue pilihan murderer di HUD-nya FS (lihat
     * init/MurderResultOverlay). Item-nya dibaca ulang dari block yang masih
     * nempel di dinding -- yang diganti pas dipilih cuma backing-nya, block
     * clue-nya sendiri gak keganggu.
     */
    private void sendMurderResultHud(ServerPlayer fsPlayer) {
        ServerLevel level = ArenaDimension.level(serverRef);
        com.deception.network.ModNetworking.sendMurderResultHud(fsPlayer,
                clueStackAt(level, murdererSelectedMeansPos),
                clueStackAt(level, murdererSelectedCluePos));
    }

    private ItemStack clueStackAt(ServerLevel level, BlockPos pos) {
        if (pos == null) return ItemStack.EMPTY;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ClueBlock)) return ItemStack.EMPTY;
        return new ItemStack(state.getBlock().asItem());
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
     * Tombol "[KONFIRMASI]" yang bisa langsung diklik di chat -- ganti
     * mekanisme lama yang pake kepala konfirmasi + klik kanan. Klik
     * tombol ini bakal jalanin /deception confirm.
     */
    private Component confirmButton() {
        return Component.literal("[KONFIRMASI]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/deception confirm"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Klik untuk konfirmasi"))));
    }

    /**
     * Dipanggil dari /deception confirm. Nentuin konfirmasi mana yang
     * berlaku buat player yang ngejalanin command (murderer lagi milih
     * means/clue, atau witness abis liat killer nyala >=3 detik).
     */
    public boolean onConfirmCommand(ServerPlayer player) {
        if (state != State.NIGHT) return false;
        UUID uuid = player.getUUID();
        Role role = roleAssignments.get(uuid);
        if (role == null) return false;

        if (role == Role.murderer) {
            if (murdererConfirmed) return false;

            if (murdererSelectedMeansBacking == null || murdererSelectedClueBacking == null) {
                String missing = murdererSelectedMeansBacking == null && murdererSelectedClueBacking == null
                        ? "means & clue" : murdererSelectedClueBacking == null ? "clue" : "means";
                player.sendSystemMessage(Component.literal("Pilih " + missing + " dulu sebelum konfirmasi.").withStyle(ChatFormatting.RED));
                return true;
            }

            applyMurdererSelectionResult();
            closeKillersEyesAndScheduleNext();

            player.sendSystemMessage(Component.literal("Pilihan dikonfirmasi!").withStyle(ChatFormatting.GREEN));
            return true;
        }

        if (role == Role.witness) {
            if (witnessConfirmed) return false;
            if (!uuid.equals(nightWitnessUuid)) return false;
            if (!witnessWindowOpen) {
                player.sendSystemMessage(Component.literal("Belum waktunya konfirmasi.").withStyle(ChatFormatting.RED));
                return true;
            }
            if (witnessConfirmReadyAt != Integer.MAX_VALUE && nightTicksElapsed < witnessConfirmReadyAt) {
                player.sendSystemMessage(Component.literal("Tunggu beberapa detik dulu sebelum konfirmasi.").withStyle(ChatFormatting.RED));
                return true;
            }

            finalizeWitnessConfirm();

            player.sendSystemMessage(Component.literal("Berhasil konfirmasi!").withStyle(ChatFormatting.GREEN));
            return true;
        }

        player.sendSystemMessage(Component.literal("Tidak ada yang perlu dikonfirmasi sekarang.").withStyle(ChatFormatting.RED));
        return true;
    }

    // Method untuk update countdown auto-pick murderer
    private void updateAutoPickCountdown() {
        if (serverRef == null) return;
        int secondsLeft = (int) Math.ceil(murdererAutoPickTicksLeft / 20.0);
        if (secondsLeft <= 0) return;
        
        Component actionBar = Component.literal("⏱ Auto-pick Murderer dalam ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW));
        
        // Kirim ke Forensic Scientist
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.forensic_scientist) {
                ServerPlayer p = serverRef.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    p.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
                }
            }
        }
        
        // Kirim ke Accomplice (jika ada)
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.accomplice) {
                ServerPlayer p = serverRef.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    p.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
                }
            }
        }
    }

    // Method untuk update countdown auto-skip witness
    private void updateWitnessSkipCountdown() {
        if (serverRef == null) return;
        int secondsLeft = (int) Math.ceil(witnessAutoSkipTicksLeft / 20.0);
        if (secondsLeft <= 0) return;
        
        Component actionBar = Component.literal("⏱ Auto-skip Witness dalam ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW));
        
        // Kirim ke Forensic Scientist saja
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.forensic_scientist) {
                ServerPlayer p = serverRef.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    p.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
                }
            }
        }
    }

    /**
     * Cari & random-in slot means/clue punya murderer buat cluster dia
     * sendiri (dipake pas dia left/di-skip sebelum sempet milih manual).
     * Kalo salah satu means/clue udah sempet dipilih manual, itu
     * dipertahanin, cuma yang belum dipilih yang di-random-in.
     */
    private boolean autoPickAndConfirmMurderer() {
        if (murdererConfirmed) return true;

        UUID murdererUuid = null;
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.murderer) { murdererUuid = e.getKey(); break; }
        }
        if (murdererUuid == null) return false;

        // Reset status left karena sudah di-auto-pick
        if (leftMurdererUuid != null && leftMurdererUuid.equals(murdererUuid)) {
            hasMurdererLeft = false;
            leftMurdererUuid = null;
            leftMurdererName = "";
        }

        ServerLevel level = ArenaDimension.level(serverRef);

        if (murdererSelectedMeansPos == null) {
            List<BlockPos> candidates = new ArrayList<>();
            for (Map.Entry<BlockPos, UUID> e : clusterOwnerByPos.entrySet()) {
                if (murdererUuid.equals(e.getValue()) && e.getKey().getY() == MEANS_Y) candidates.add(e.getKey());
            }
            if (candidates.isEmpty()) return false;
            BlockPos meansPos = candidates.get(shuffleRandom.nextInt(candidates.size()));
            murdererSelectedMeansPos = meansPos;
            murdererSelectedMeansBacking = backingOf(level, meansPos);
            level.setBlock(murdererSelectedMeansBacking, Blocks.GREEN_CONCRETE.defaultBlockState(), 3);
        }

        if (murdererSelectedCluePos == null) {
            List<BlockPos> candidates = new ArrayList<>();
            for (Map.Entry<BlockPos, UUID> e : clusterOwnerByPos.entrySet()) {
                if (murdererUuid.equals(e.getValue()) && e.getKey().getY() == CLUE_Y) candidates.add(e.getKey());
            }
            if (candidates.isEmpty()) return false;
            BlockPos cluePos = candidates.get(shuffleRandom.nextInt(candidates.size()));
            murdererSelectedCluePos = cluePos;
            murdererSelectedClueBacking = backingOf(level, cluePos);
            level.setBlock(murdererSelectedClueBacking, Blocks.GREEN_CONCRETE.defaultBlockState(), 3);
        }

        applyMurdererSelectionResult();
        return true;
    }

    private BlockPos backingOf(ServerLevel level, BlockPos pos) {
        BlockState clickedState = level.getBlockState(pos);
        Direction facing = clickedState.getValue(ClueBlock.FACING);
        return pos.relative(facing.getOpposite());
    }

    /**
     * Bagian "kunci" pilihan murderer: tandain confirmed, balikin green
     * concrete, kirim hasil ke Forensic Scientist & murderer/accomplice.
     * Belum nutup mata / belum jadwalin fase witness -- itu dilakuin di
     * closeKillersEyesAndScheduleNext() biar bisa dipisah pas dipake
     * skipreveal (yang gak perlu nutup-buka mata lagi).
     */
    private void applyMurdererSelectionResult() {
        murdererConfirmed = true;
        murdererWindowOpen = false;
        murdererAutoPickAt = Integer.MAX_VALUE;

        ServerLevel level = ArenaDimension.level(serverRef);
        if (murdererSelectedMeansBacking != null) restoreBacking(level, murdererSelectedMeansBacking);
        if (murdererSelectedClueBacking != null) restoreBacking(level, murdererSelectedClueBacking);

        UUID fsUuid = null;
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.forensic_scientist) { fsUuid = e.getKey(); break; }
        }
        if (fsUuid != null) {
            ServerPlayer fsPlayer = serverRef.getPlayerList().getPlayer(fsUuid);
            if (fsPlayer != null) {
                fsPlayer.sendSystemMessage(Component.literal(""));
                fsPlayer.sendSystemMessage(Component.literal("===== Hasil Malam =====").withStyle(ChatFormatting.GOLD));
                fsPlayer.sendSystemMessage(Component.literal(""));
                for (Component line : buildFsResultMurder()) fsPlayer.sendSystemMessage(line);
                sendMurderResultHud(fsPlayer);
            }
        }

        for (UUID kUuid : nightKillers) {
            ServerPlayer kp = serverRef.getPlayerList().getPlayer(kUuid);
            if (kp != null) {
                for (Component line : buildKillerResultLines()) kp.sendSystemMessage(line);
            }
        }
    }

    // Murderer & accomplice tutup mata lagi, lalu jadwalin fase witness
    // (atau langsung ke fase bangun kalo gaada witness).
    private void closeKillersEyesAndScheduleNext() {
        for (UUID kUuid : nightKillers) {
            ServerPlayer kp = serverRef.getPlayerList().getPlayer(kUuid);
            if (kp != null) {
                putBlindfold(kp);
                nightBlindfolded.add(kUuid);
            }
        }

        String killerLabel = nightKillers.size() > 1 ? "Murderer & Accomplice" : "Murderer";
        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal(killerLabel + " tutup mata").withStyle(ChatFormatting.RED),
                Component.empty(), 10, 40, 10);
        com.deception.network.ModNetworking.broadcastNightActionBar(Component.empty());

        // Kalo witness udah kepegang duluan (misal auto-skip 90 detik keburu
        // abis sebelum murderer sempet confirm), gak usah nunggu jendela
        // witness lagi -- anggep aja kayak gaada witness, langsung ke wake-all.
        boolean hasWitness = nightWitnessUuid != null && !witnessConfirmed;
        if (hasWitness) {
            nightWitnessRevealAt = nightTicksElapsed + NIGHT_CLOSE_HOLD;
        } else {
            nightWakeAllAt = nightTicksElapsed + NIGHT_PRE_WAKE_HOLD;
            nightDoneAt = nightWakeAllAt + NIGHT_WAKE_HOLD;
        }
    }

    // Buka mata witness & nyalain glow di killer. Dipanggil pas timer
    // nightWitnessRevealAt kena (tick normal) ATAU dipanggil langsung
    // dari skipReveal() buat majuin fase murderer->witness tanpa nunggu.
    private void openWitnessWindow() {
        nightWitnessRevealAt = Integer.MAX_VALUE;
        if (nightWitnessUuid == null) return;
        if (witnessConfirmed) return; // udah di-auto-skip duluan sebelum sempet direveal

        ServerPlayer witnessPlayer = serverRef.getPlayerList().getPlayer(nightWitnessUuid);
        if (witnessPlayer != null) {
            removeBlindfold(witnessPlayer);
            nightBlindfolded.remove(nightWitnessUuid);
        }
        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal("Witness buka mata").withStyle(ChatFormatting.AQUA),
                Component.empty(), 10, 40, 10);

        for (UUID uuid : nightKillers) {
            ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
            if (p != null) applyGlow(p);
        }
        com.deception.network.ModNetworking.broadcastNightActionBar(
                Component.literal("Menunggu witness melihat").withStyle(ChatFormatting.AQUA));
        witnessWindowOpen = true;
        // Baru boleh /deception confirm abis liat killer nyala >=3 detik;
        // begitu tick ini kena, prompt "[KONFIRMASI]" dikirim ke chat witness.
        witnessConfirmReadyAt = nightTicksElapsed + NIGHT_WITNESS_HOLD;

        checkWitnessWakeUpOffline();
    }

    // Witness ketutup lagi matanya abis konfirmasi (baik klik manual
    // maupun ke-skip otomatis karena left kelamaan / di-skipreveal).
    private void finalizeWitnessConfirm() {
        if (witnessConfirmed) return;
        witnessConfirmed = true;
        witnessWindowOpen = false;
        witnessAutoSkipAt = Integer.MAX_VALUE;
        witnessConfirmReadyAt = Integer.MAX_VALUE;
        
        // Reset status left karena sudah di-auto-skip
        if (leftWitnessUuid != null) {
            hasWitnessLeft = false;
            leftWitnessUuid = null;
            leftWitnessName = "";
        }

        UUID fsUuid = null;
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.forensic_scientist) { fsUuid = e.getKey(); break; }
        }

        if (fsUuid != null) {
            ServerPlayer fsPlayer = serverRef.getPlayerList().getPlayer(fsUuid);
            if (fsPlayer != null) {
                fsPlayer.sendSystemMessage(Component.literal(""));
                fsPlayer.sendSystemMessage(Component.literal("===== Hasil Malam =====").withStyle(ChatFormatting.GOLD));
                fsPlayer.sendSystemMessage(Component.literal(""));
                for (Component line : buildFsResultLines()) fsPlayer.sendSystemMessage(line);
            }
        }

        for (UUID kUuid : nightKillers) {
            ServerPlayer kp = serverRef.getPlayerList().getPlayer(kUuid);
            if (kp != null) removeGlow(kp);
        }
        com.deception.network.ModNetworking.broadcastNightActionBar(Component.empty());

        if (nightWitnessUuid != null) {
            ServerPlayer witnessPlayer = serverRef.getPlayerList().getPlayer(nightWitnessUuid);
            if (witnessPlayer != null) {
                putBlindfold(witnessPlayer);
                nightBlindfolded.add(nightWitnessUuid);
            }
        }

        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal("Witness tutup mata").withStyle(ChatFormatting.RED),
                Component.empty(), 10, 40, 10);

        nightWakeAllAt = nightTicksElapsed + NIGHT_PRE_WAKE_HOLD;
        nightDoneAt = nightWakeAllAt + NIGHT_WAKE_HOLD;
    }

    // ---------- Baris pesan hasil malam, dipake pas konfirmasi & pas rejoin ----------

    private List<Component> buildFsResultMurder() {
        List<Component> lines = new ArrayList<>();
        ServerLevel level = ArenaDimension.level(serverRef);
        String meansName = murdererSelectedMeansPos != null ? getItemName(level, murdererSelectedMeansPos) : "?";
        String clueName = murdererSelectedCluePos != null ? getItemName(level, murdererSelectedCluePos) : "?";

        String murdererName = "-";
        List<String> accompliceNames = new ArrayList<>();
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.murderer) murdererName = getPlayerName(e.getKey());
            else if (e.getValue() == Role.accomplice) accompliceNames.add(getPlayerName(e.getKey()));
        }

        lines.add(Component.literal("  Murderer: " + murdererName).withStyle(ChatFormatting.RED));
        lines.add(Component.literal("  Accomplice: " + (accompliceNames.isEmpty() ? "-" : String.join(", ", accompliceNames))).withStyle(ChatFormatting.RED));
        lines.add(Component.literal("  " + meansName).withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal("  " + clueName).withStyle(ChatFormatting.WHITE));
        return lines;
    }

    private List<Component> buildFsResultLines() {
        List<Component> lines = new ArrayList<>();
        ServerLevel level = ArenaDimension.level(serverRef);
        String meansName = murdererSelectedMeansPos != null ? getItemName(level, murdererSelectedMeansPos) : "?";
        String clueName = murdererSelectedCluePos != null ? getItemName(level, murdererSelectedCluePos) : "?";

        String murdererName = "-";
        List<String> accompliceNames = new ArrayList<>();
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.murderer) murdererName = getPlayerName(e.getKey());
            else if (e.getValue() == Role.accomplice) accompliceNames.add(getPlayerName(e.getKey()));
        }
        String witnessName = nightWitnessUuid != null ? getPlayerName(nightWitnessUuid) : "-";

        lines.add(Component.literal("  Murderer: " + murdererName).withStyle(ChatFormatting.RED));
        lines.add(Component.literal("  Accomplice: " + (accompliceNames.isEmpty() ? "-" : String.join(", ", accompliceNames))).withStyle(ChatFormatting.RED));
        lines.add(Component.literal("  Witness: " + witnessName).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("  " + meansName).withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal("  " + clueName).withStyle(ChatFormatting.WHITE));
        return lines;
    }

    private List<Component> buildKillerResultLines() {
        List<Component> lines = new ArrayList<>();
        ServerLevel level = ArenaDimension.level(serverRef);
        String meansName = murdererSelectedMeansPos != null ? getItemName(level, murdererSelectedMeansPos) : "?";
        String clueName = murdererSelectedCluePos != null ? getItemName(level, murdererSelectedCluePos) : "?";

        List<String> killerNames = new ArrayList<>();
        for (UUID k : nightKillers) killerNames.add(getPlayerName(k));

        lines.add(Component.literal("  Murderer & Accomplice: " + String.join(", ", killerNames)).withStyle(ChatFormatting.RED));
        lines.add(Component.literal("  " + meansName).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("  " + clueName).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    // Witness cuma dikasih tau nama-nama yang keliatan glowing, tanpa
    // nyebut peran ("murderer"/"accomplice") -- soalnya emang gitu yang
    // dia liat pas malam itu, dua-duanya nyala bareng gak dibedain.
    private Component buildWitnessResultLine() {
        List<String> killerNames = new ArrayList<>();
        for (UUID k : nightKillers) killerNames.add(getPlayerName(k));
        return Component.literal("  Orang yang terlibat: " + String.join(", ", killerNames)).withStyle(ChatFormatting.RED);
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

    public void broadcastActionBarToAll(Component message) {
        if (serverRef == null) return;
        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = serverRef.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(message));
            }
        }
    }

    // Actionbar bawaan (bukan overlay custom) "Menunggu forensic scientist
    // memberi clue..." -- mulai pas "Semua orang buka mata", terus nyala
    // terus (di-resend tiap 10 tick biar titik-titiknya keliatan jalan &
    // gak keburu fade vanilla) sepanjang fase diskusi.
    private void broadcastForensicWaitActionBar() {
        int dotCount = (int) ((globalTickCounter / 10) % 4);
        Component actionBar = Component.literal("Menunggu forensic scientist memberi petunjuk" + ".".repeat(dotCount))
                .withStyle(ChatFormatting.YELLOW);
        broadcastActionBarToAll(actionBar);
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

    /**
     * Di mana tiap peserta berdiri SEBELUM ditarik ke arena, biar pas game
     * kelar mereka bisa dibalikin ke situ.
     *
     * <p>Wajib ada gara-gara arena pindah ke dimensi sendiri: dulu pas arena
     * masih nempel di overworld, abis main tinggal ditinggal di tempat dan
     * pemain bisa jalan pulang sendiri. Sekarang kalo gak dibalikin, mereka
     * nyangkut di dimensi arena tanpa jalan keluar.
     */
    private record HomeLocation(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {}

    private final Map<UUID, HomeLocation> homeLocations = new HashMap<>();

    private void rememberHomeLocation(ServerPlayer player) {
        // Jangan nimpa kalo dia UDAH di arena -- misal rejoin di tengah game,
        // yang kecatet nanti malah posisi di dalem arena, bukan rumah aslinya.
        if (player.level().dimension().equals(ArenaDimension.ARENA)) return;
        homeLocations.put(player.getUUID(), new HomeLocation(
                player.level().dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()));
    }

    /** Balikin satu peserta ke tempat dia sebelum ditarik ke arena. */
    private void sendPlayerHome(MinecraftServer server, ServerPlayer player) {
        HomeLocation home = homeLocations.remove(player.getUUID());
        if (player.level().dimension().equals(ArenaDimension.ARENA)) {
            ServerLevel target = home != null ? server.getLevel(home.dimension()) : null;
            if (target == null) {
                // Gak kecatet (atau dimensinya udah gak ada) -- jangan
                // ditinggal nyangkut di arena, buang ke spawn overworld.
                target = server.overworld();
                BlockPos spawn = target.getSharedSpawnPos();
                player.teleportTo(target, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
                return;
            }
            player.teleportTo(target, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        }
    }

    private void teleportPlayersAndDecorateArena(MinecraftServer server) {
        ServerLevel level = ArenaDimension.level(server);

        List<UUID> allPlayers = new ArrayList<>(registeredPlayers);
        for (UUID uuid : allPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                rememberHomeLocation(player);
                // Versi 5-argumen yang ada ServerLevel-nya: arena ada di
                // dimensi sendiri, jadi ini pindah dimensi, bukan cuma pindah
                // koordinat.
                player.teleportTo(level,
                        ARENA_TELEPORT_POS.getX() + 0.5, ARENA_TELEPORT_POS.getY(),
                        ARENA_TELEPORT_POS.getZ() + 0.5, player.getYRot(), player.getXRot());
            }
        }

        List<UUID> players = new ArrayList<>(allPlayers);
        players.removeIf(uuid -> roleAssignments.get(uuid) == Role.forensic_scientist);

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

        spawnOwnerHead(ArenaDimension.level(server), ownerUuid, getPlayerName(ownerUuid), player, placement.wall(), placement.columns());
    }

    // ---------- Handle player left/rejoin di tengah game ----------

    public void onPlayerLeft(MinecraftServer server, UUID uuid) {
        if (state == State.IDLE) return;
        if (!registeredPlayers.contains(uuid)) return;
        Role role = roleAssignments.get(uuid);
        if (role == null) return;
        if (state != State.NIGHT) return;

        String name = getPlayerName(uuid);

        if (role == Role.murderer) {
            if (!murdererConfirmed) {
                // Cuma react di sini kalo jendela milihnya UDAH kebuka.
                // Kalo dia left pas masih sama-sama tutup mata (sebelum
                // giliran dia), gak usah diapa-apain sekarang -- pas
                // "murderer buka mata" nanti (nightKillersWakeAt di
                // tickNightSequence), ada checkMurdererWakeUpOffline() yang
                // ngecek ulang status online murderer/accomplice & bakal
                // langsung mulai timer kalo ternyata dia emang udah gak ada.
                if (murdererWindowOpen && murdererAutoPickAt == Integer.MAX_VALUE) {
                    murdererAutoPickAt = nightTicksElapsed + LEAVE_TIMEOUT_TICKS;
                    murdererAutoPickTicksLeft = LEAVE_TIMEOUT_TICKS;
                    leftMurdererUuid = uuid;
                    leftMurdererName = name;
                    hasMurdererLeft = true;

                    Component fsMsg = Component.literal(name + " (Murderer) keluar server. Auto-pick dalam ")
                            .withStyle(ChatFormatting.RED)
                            .append(Component.literal(getOfflineRevealSeconds() + " detik").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(".").withStyle(ChatFormatting.RED))
                            .append(skipRevealPrompt());
                    notifyRole(Role.forensic_scientist, fsMsg);

                    Component accMsg = Component.literal(name + " (Murderer) keluar server. Item bakal dipilih otomatis dalam " + getOfflineRevealSeconds() + " detik.")
                            .withStyle(ChatFormatting.RED);
                    notifyRole(Role.accomplice, accMsg);

                    // Witness masih tutup mata & belum masuk giliran dia di
                    // titik ini (jendela witness baru kebuka SETELAH
                    // murderer confirm) -- jadi JANGAN notify witness di
                    // sini. Kalo murderer/accomplice masih offline pas
                    // giliran witness beneran mulai nanti, dia bakal dikasih
                    // tau lewat notifyWitnessKillerLeft() (dipanggil dari
                    // checkWitnessWakeUpOffline / openWitnessWindow).
                    updateAutoPickCountdown();
                }
            } else {
                // Udah confirm (misal lagi fase witness liat / abis itu) --
                // gak ada lagi yang perlu di-auto-pick, tetep kasih tau ke
                // Forensic Scientist. Kalo giliran witness lagi berlangsung
                // (witnessWindowOpen), witness juga dikasih tau -- ini yang
                // beneran dia alami real-time (murderer ilang pas dia lagi
                // "buka mata" liat killer).
                Component msg = Component.literal(name + " (Murderer) keluar server.").withStyle(ChatFormatting.RED);
                notifyRole(Role.forensic_scientist, msg);
                if (witnessWindowOpen && !witnessConfirmed) {
                    notifyWitnessKillerLeft(name);
                }
            }
        }

        if (role == Role.accomplice) {
            // Accomplice gak milih apa-apa sendiri, jadi gak ada timer --
            // kapanpun dia left (sebelum atau sesudah murderer confirm),
            // selalu cuma notifikasi ke Forensic Scientist & Murderer.
            // Witness cuma dikasih tau kalo giliran dia (witnessWindowOpen)
            // lagi berlangsung pas ini kejadian -- sebelum itu (termasuk
            // pas murderer masih milih) witness gak boleh tau apa-apa dulu.
            Component msg = Component.literal(name + " (Accomplice) keluar server.").withStyle(ChatFormatting.RED);
            notifyRole(Role.forensic_scientist, msg);
            notifyRole(Role.murderer, msg);
            if (witnessWindowOpen && !witnessConfirmed) {
                notifyWitnessKillerLeft(name);
            }
        }

        if (role == Role.witness) {
            if (!witnessConfirmed) {
                // Sama kayak murderer -- cuma react kalo jendela liatnya
                // UDAH kebuka. Leave sebelum giliran dia ketangkep sama
                // checkWitnessWakeUpOffline() pas "witness buka mata".
                if (witnessWindowOpen && witnessAutoSkipAt == Integer.MAX_VALUE) {
                    witnessAutoSkipAt = nightTicksElapsed + LEAVE_TIMEOUT_TICKS;
                    witnessAutoSkipTicksLeft = LEAVE_TIMEOUT_TICKS;
                    leftWitnessUuid = uuid;
                    leftWitnessName = name;
                    hasWitnessLeft = true;

                    Component fsMsg = Component.literal(name + " (Witness) keluar server. Auto-skip dalam ")
                            .withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(getOfflineRevealSeconds() + " detik").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(".").withStyle(ChatFormatting.AQUA))
                            .append(skipRevealPrompt());
                    notifyRole(Role.forensic_scientist, fsMsg);

                    updateWitnessSkipCountdown();
                }
            } else {
                Component msg = Component.literal(name + " (Witness) keluar server.").withStyle(ChatFormatting.AQUA);
                notifyRole(Role.forensic_scientist, msg);
            }
        }
    }

    // Notify witness kalo murderer/accomplice ilang (offline) pas giliran
    // witness lagi berlangsung (witnessWindowOpen) -- baik karena dia baru
    // left SEKARANG (dipanggil dari onPlayerLeft) maupun karena dia udah
    // offline DARI SEBELUM jendela witness kebuka (dipanggil dari
    // checkWitnessWakeUpOffline pas openWitnessWindow). Sengaja tetep gak
    // nyebut role spesifik (Murderer/Accomplice), cuma nama pemainnya --
    // biar gak bocorin identitas orang jahat ke witness.
    private void notifyWitnessKillerLeft(String name) {
        notifyRole(Role.witness, Component.literal(name + " orang yang terlibat telah keluar server").withStyle(ChatFormatting.RED));
    }

    // Nempel di pesan "X keluar server" yang dikirim ke Forensic
    // Scientist -- tombol [SKIP] yang bisa langsung diklik buat jalanin
    // /deception skipreveal, gausah cape-cape ngetik commandnya.
    private Component skipRevealPrompt() {
        Component button = Component.literal("[SKIP]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/deception skipreveal"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Klik untuk skip reveal ini"))));
        return Component.literal("\n Terlalu lama? ").withStyle(ChatFormatting.GRAY).append(button);
    }

    public void onPlayerRejoined(MinecraftServer server, ServerPlayer player) {
        if (state == State.IDLE) return;
        UUID uuid = player.getUUID();
        if (!registeredPlayers.contains(uuid)) return;
        Role role = roleAssignments.get(uuid);
        if (role == null) return;

        // ===== SINKRONISASI VISUAL =====
        syncBlindfoldState(player, uuid, role);
        syncActionBar(player);
        syncTitle(player, uuid, role);
        syncGlowEffect(player, uuid, role);
        // HUD role itu state client-only, ke-reset pas dia disconnect.
        sendRoleVisibleHud(player);

        // Timer auto-pick/auto-skip gak berlaku lagi kalo yang ditunggu udah balik
        if (role == Role.murderer) {
            murdererAutoPickAt = Integer.MAX_VALUE;
            murdererAutoPickTicksLeft = 0;
            if (leftMurdererUuid != null && leftMurdererUuid.equals(uuid)) {
                hasMurdererLeft = false;
                leftMurdererUuid = null;
                leftMurdererName = "";
            }
            // Reset actionbar untuk Forensic Scientist dan Accomplice
            resetCountdownActionBars();
        }
        
        if (role == Role.witness) {
            witnessAutoSkipAt = Integer.MAX_VALUE;
            witnessAutoSkipTicksLeft = 0;
            if (leftWitnessUuid != null && leftWitnessUuid.equals(uuid)) {
                hasWitnessLeft = false;
                leftWitnessUuid = null;
                leftWitnessName = "";
            }
            // Reset actionbar untuk Forensic Scientist
            resetCountdownActionBars();
        }

        // KIRIM ULANG SKIP BUTTON ke Forensic Scientist jika masih ada player yang left
        if (role == Role.forensic_scientist) {
            resendSkipButtonToForensicScientist(player);

            // HUD hasil malam itu state client-only, jadi ke-reset pas dia
            // disconnect -- kirim ulang kalo murderer emang udah konfirmasi.
            if (murdererConfirmed) {
                sendMurderResultHud(player);
            }

            // FS ini yang tadi offline pas giveForensicScientistPapers manggil --
            // kasih sekarang, kategorinya sama kayak yang udah diacak sebelumnya.
            if (uuid.equals(pendingForensicScientistUuid) && pendingForensicPaperCategories != null) {
                deliverForensicPapers(player, pendingForensicPaperCategories);
                pendingForensicScientistUuid = null;
                pendingForensicPaperCategories = null;
            }
        }

        // Kasih tau info yang kelewat pas dia offline
        if ((role == Role.murderer || role == Role.accomplice) && murdererConfirmed) {
            for (Component line : buildKillerResultLines()) player.sendSystemMessage(line);
        } else if (role == Role.witness && murdererConfirmed) {
            player.sendSystemMessage(buildWitnessResultLine());
        } else if (role == Role.forensic_scientist && murdererConfirmed) {
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("===== Hasil Malam =====").withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.literal(""));
            for (Component line : buildFsResultLines()) player.sendSystemMessage(line);
        }

        // Kirim ulang pesan konfirmasi jika diperlukan
        resendConfirmMessage(player, role);

        // Badge police: jatah yang kelewat pas dia offline + sinkron ulang
        // icon badge di nametag semua orang (lihat PresentationManager).
        PresentationManager.get().onPlayerRejoined(player, server);
    }

    // Method untuk reset actionbar countdown
    private void resetCountdownActionBars() {
        if (serverRef == null) return;
        
        // Reset actionbar Forensic Scientist ke default
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.forensic_scientist) {
                ServerPlayer p = serverRef.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    // Cek apakah masih ada yang left
                    if (hasMurdererLeft && leftMurdererUuid != null && !murdererConfirmed) {
                        int secondsLeft = (int) Math.ceil(murdererAutoPickTicksLeft / 20.0);
                        if (secondsLeft > 0) {
                            p.connection.send(new ClientboundSetActionBarTextPacket(
                                Component.literal("⏱ Auto-pick Murderer dalam ")
                                    .withStyle(ChatFormatting.RED)
                                    .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW))
                            ));
                            continue;
                        }
                    }
                    if (hasWitnessLeft && leftWitnessUuid != null && !witnessConfirmed) {
                        int secondsLeft = (int) Math.ceil(witnessAutoSkipTicksLeft / 20.0);
                        if (secondsLeft > 0) {
                            p.connection.send(new ClientboundSetActionBarTextPacket(
                                Component.literal("⏱ Auto-skip Witness dalam ")
                                    .withStyle(ChatFormatting.AQUA)
                                    .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW))
                            ));
                            continue;
                        }
                    }
                    // Default actionbar
                    if (witnessWindowOpen && !witnessConfirmed && nightWitnessUuid != null) {
                        p.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.literal("Menunggu witness melihat...").withStyle(ChatFormatting.AQUA)
                        ));
                    } else if (murdererWindowOpen && !murdererConfirmed) {
                        p.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.literal("Menunggu murderer memilih item").withStyle(ChatFormatting.RED)
                        ));
                    } else {
                        p.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
                    }
                }
            }
        }
        
        // Reset actionbar Accomplice
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.accomplice) {
                ServerPlayer p = serverRef.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    if (hasMurdererLeft && leftMurdererUuid != null && !murdererConfirmed) {
                        int secondsLeft = (int) Math.ceil(murdererAutoPickTicksLeft / 20.0);
                        if (secondsLeft > 0) {
                            p.connection.send(new ClientboundSetActionBarTextPacket(
                                Component.literal("⏱ Auto-pick Murderer dalam ")
                                    .withStyle(ChatFormatting.RED)
                                    .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW))
                            ));
                            continue;
                        }
                    }
                    if (murdererWindowOpen && !murdererConfirmed) {
                        p.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.literal("Menunggu murderer memilih item").withStyle(ChatFormatting.RED)
                        ));
                    } else {
                        p.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
                    }
                }
            }
        }
    }

    // Method baru untuk sinkronisasi blindfold
    // Hitung ulang dari NOL apakah player ini seharusnya lagi tutup mata
    // detik ini juga, murni dari state night sekarang (bukan dari set
    // nightBlindfolded/nightPendingBlindfold yang bisa "kelewat" update
    // kalo playernya offline pas event penting kejadian).
    private boolean computeShouldBeBlindfolded(UUID uuid, Role role) {
        if (state != State.NIGHT) return false;
        if (role == Role.forensic_scientist) return false;
        if (nightTicksElapsed < NIGHT_PRECLOSE_DELAY) return false;
        if (nightWakeAllAt != Integer.MAX_VALUE && nightTicksElapsed >= nightWakeAllAt) return false;

        boolean isKiller = role == Role.murderer || role == Role.accomplice;
        if (isKiller) {
            // lagi jendela milih item -> mata kebuka, selain itu tutup
            return !(murdererWindowOpen && !murdererConfirmed);
        }

        boolean isRevealedWitness = role == Role.witness && uuid.equals(nightWitnessUuid);
        if (isRevealedWitness) {
            // lagi jendela liat killer nyala -> mata kebuka, selain itu tutup
            return !(witnessWindowOpen && !witnessConfirmed);
        }

        // role lain (investigator dll) -- tutup terus dari awal night sampe nightWakeAllAt
        return true;
    }

    private void syncBlindfoldState(ServerPlayer player, UUID uuid, Role role) {
        // udah ditangani manual di sini, gak perlu nunggu loop awal night lagi
        nightPendingBlindfold.remove(uuid);

        boolean shouldBeBlindfolded = computeShouldBeBlindfolded(uuid, role);

        if (shouldBeBlindfolded) {
            nightBlindfolded.add(uuid);
            if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() != Items.NOTE_BLOCK) {
                player.setItemSlot(EquipmentSlot.HEAD, createBlindfoldItem());
            }
            // instan "snap" ke tertutup penuh -- JANGAN pake sendBlindfoldState(true),
            // itu bakal muter ulang animasi nutup dari awal tiap kali rejoin.
            com.deception.network.ModNetworking.sendSnapShutBlindfold(player);
        } else {
            nightBlindfolded.remove(uuid);
            if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.NOTE_BLOCK) {
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
            // instan "snap" ke kebuka penuh, gak usah animasi buka juga
            com.deception.network.ModNetworking.sendForceCloseBlindfold(player);
        }
    }

    // Method baru untuk sinkronisasi actionbar
    // Di dalam syncActionBar, tambahkan pengecekan countdown:
    // Actionbar CUSTOM (overlay animasi titik-titik client-side, lihat
    // NightTitleClientState) cuma dipake buat 2 pesan ini: "Menunggu
    // murderer memilih item" & "Menunggu witness melihat". Semua pesan
    // actionbar lain (countdown auto-pick/skip, diskusi, game start, dst)
    // pake actionbar BAWAAN Minecraft (ClientboundSetActionBarTextPacket
    // langsung).
    private void syncActionBar(ServerPlayer player) {
        // Clear overlay custom-nya duluan di awal, apapun state-nya sekarang.
        // Overlay custom itu static state di CLIENT, gak otomatis ke-reset
        // pas reconnect ke server yang sama (kalo player gak restart game-nya)
        // -- jadi kalo dia disconnect pas lagi "Menunggu witness melihat" terus
        // rejoin pas udah DISCUSS, tanpa clear ini teks lama itu nyangkut
        // terus di layar dia walau gak pernah ada yang ngirim clear ke dia.
        com.deception.network.ModNetworking.sendNightActionBarTo(player, Component.empty());

        if (state == State.NIGHT) {
            // Prioritaskan countdown jika ada
            if (murdererAutoPickAt != Integer.MAX_VALUE && leftMurdererUuid != null && !murdererConfirmed) {
                int secondsLeft = (int) Math.ceil(murdererAutoPickTicksLeft / 20.0);
                if (secondsLeft > 0) {
                    Role role = roleAssignments.get(player.getUUID());
                    if (role == Role.forensic_scientist || role == Role.accomplice) {
                        Component actionBar = Component.literal("⏱ Auto-pick Murderer dalam ")
                                .withStyle(ChatFormatting.RED)
                                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW));
                        player.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
                        return;
                    }
                }
            }

            if (witnessAutoSkipAt != Integer.MAX_VALUE && leftWitnessUuid != null && !witnessConfirmed) {
                int secondsLeft = (int) Math.ceil(witnessAutoSkipTicksLeft / 20.0);
                if (secondsLeft > 0) {
                    Role role = roleAssignments.get(player.getUUID());
                    if (role == Role.forensic_scientist) {
                        Component actionBar = Component.literal("⏱ Auto-skip Witness dalam ")
                                .withStyle(ChatFormatting.AQUA)
                                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW));
                        player.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
                        return;
                    }
                }
            }

            // Jika tidak ada countdown, tampilkan actionbar normal
            if (murdererWindowOpen && !murdererConfirmed) {
                Component actionBar = Component.literal("Menunggu murderer memilih item").withStyle(ChatFormatting.RED);
                com.deception.network.ModNetworking.sendNightActionBarTo(player, actionBar);
            } else if (witnessWindowOpen && !witnessConfirmed && nightWitnessUuid != null) {
                if (player.getUUID().equals(nightWitnessUuid)) {
                    Component actionBar = Component.literal("Kamu adalah Witness - Lihat siapa yang bersinar!").withStyle(ChatFormatting.AQUA);
                    player.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
                } else {
                    Component actionBar = Component.literal("Menunggu witness melihat").withStyle(ChatFormatting.AQUA);
                    com.deception.network.ModNetworking.sendNightActionBarTo(player, actionBar);
                }
            }
            // Gak ada fase murderer/witness yang lagi berlangsung -- overlay
            // custom-nya udah di-clear di awal method, gak perlu ngapa-ngapain lagi.
        } else if (state == State.DISCUSS) {
            int minutes = discussTicksLeft / 1200;
            int seconds = (discussTicksLeft % 1200) / 20;
            Component actionBar = Component.literal(String.format("⏱ Diskusi: %02d:%02d", minutes, seconds))
                    .withStyle(ChatFormatting.GOLD);
            player.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
        } else if (state == State.COUNTDOWN) {
            int secondsLeft = (int) Math.ceil(countdownTicks / 20.0);
            if (secondsLeft > 0) {
                Component actionBar = Component.literal("Game dimulai dalam " + secondsLeft + " detik")
                        .withStyle(ChatFormatting.GOLD);
                player.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
            }
        }
        // State lain (IDLE, SHUFFLE, dst) -- overlay custom-nya udah di-clear di awal.
    }

    // Method baru untuk sinkronisasi title
    private void syncTitle(ServerPlayer player, UUID uuid, Role role) {
        if (state == State.COUNTDOWN) {
            int secondsLeft = (int) Math.ceil(countdownTicks / 20.0);
            if (secondsLeft > 0) {
                Component title = Component.literal("Game dimulai dalam").withStyle(ChatFormatting.GOLD);
                Component subtitle = Component.literal(String.valueOf(secondsLeft)).withStyle(ChatFormatting.YELLOW);
                sendTitle(player, title, subtitle, 0, 20, 5);
            }
        } else if (state == State.SHUFFLE) {
            // Tampilkan animasi shuffle
            sendTitle(player, 
                Component.literal("Mengocok Peran").withStyle(ChatFormatting.GOLD),
                Component.empty(), 0, SHUFFLE_DURATION_TICKS, 0);
        } else if (state == State.NIGHT) {
            // Sinkronkan title berdasarkan fase night
            if (nightTicksElapsed < nightKillersWakeAt) {
                // Fase "Semua tutup mata"
                Component title = Component.literal("Semua orang tutup mata").withStyle(ChatFormatting.RED);
                sendTitle(player, title, Component.empty(), 10, 40, 10);
            } else if (murdererWindowOpen && !murdererConfirmed) {
                // Fase murderer/accomplice buka mata
                String killerLabel = nightKillers.size() > 1 ? "Murderer & Accomplice" : "Murderer";
                Component title = Component.literal(killerLabel + " buka mata").withStyle(ChatFormatting.RED);
                sendTitle(player, title, Component.empty(), 10, 40, 10);
            } else if (witnessWindowOpen && !witnessConfirmed && nightWitnessUuid != null) {
                // Fase witness buka mata
                Component title = Component.literal("Witness buka mata").withStyle(ChatFormatting.AQUA);
                sendTitle(player, title, Component.empty(), 10, 40, 10);
            } else if (nightWakeAllAt != Integer.MAX_VALUE && nightTicksElapsed >= nightWakeAllAt) {
                // Fase semua buka mata
                Component title = Component.literal("Semua orang buka mata").withStyle(ChatFormatting.GOLD);
                sendTitle(player, title, Component.empty(), 10, 20, 10);
            }
        }
    }

    // Method baru untuk sinkronisasi glow effect
    private void syncGlowEffect(ServerPlayer player, UUID uuid, Role role) {
        if (state == State.NIGHT && witnessWindowOpen && !witnessConfirmed) {
            // Jika witness sedang melihat, killer harus glowing
            boolean isKiller = role == Role.murderer || role == Role.accomplice;
            boolean hasGlow = player.hasEffect(MobEffects.GLOWING);
            
            if (isKiller && !hasGlow) {
                applyGlow(player);
            } else if (!isKiller && hasGlow) {
                removeGlow(player);
            }
        } else {
            // State lain, pastikan tidak ada glow
            if (player.hasEffect(MobEffects.GLOWING)) {
                removeGlow(player);
            }
        }
    }

    // Method baru untuk mengirim ulang pesan konfirmasi
    private void resendConfirmMessage(ServerPlayer player, Role role) {
        if (state != State.NIGHT) return;
        
        // Untuk Murderer: jika masih dalam fase memilih dan belum konfirmasi
        if (role == Role.murderer && !murdererConfirmed && murdererWindowOpen) {
            // Cek apakah sudah memilih kedua item
            boolean meansSelected = murdererSelectedMeansBacking != null;
            boolean clueSelected = murdererSelectedClueBacking != null;
            
            if (meansSelected && clueSelected) {
                // Keduanya sudah dipilih, kirim tombol konfirmasi
                player.sendSystemMessage(Component.literal("Keduanya sudah dipilih! ").withStyle(ChatFormatting.GOLD)
                        .append(confirmButton()));
            } else {
                // Belum lengkap, kirim pesan item yang kurang
                String next = !meansSelected ? "means" : "clue";
                player.sendSystemMessage(Component.literal("Pilih " + next + " untuk konfirmasi.").withStyle(ChatFormatting.YELLOW));
            }
        }
        
        // Untuk Witness: jika masih dalam fase melihat dan belum konfirmasi
        if (role == Role.witness && !witnessConfirmed && witnessWindowOpen && nightWitnessUuid != null 
                && nightWitnessUuid.equals(player.getUUID())) {
            // witnessConfirmReadyAt itu tick sekali-tembak: begitu tercapai,
            // langsung direset ke Integer.MAX_VALUE (lihat tickNightSequence)
            // TERLEPAS witness-nya online apa nggak. Jadi MAX_VALUE di sini
            // berarti "udah lewat masa tunggunya", BUKAN "belum pernah mulai" --
            // kalo disamain ke situ, hasil pengurangannya jadi angka ngaco
            // (mendekati Integer.MAX_VALUE) pas witness rejoin abis grace period-nya lewat.
            if (witnessConfirmReadyAt == Integer.MAX_VALUE || nightTicksElapsed >= witnessConfirmReadyAt) {
                player.sendSystemMessage(Component.literal("Selesai melihat? ").withStyle(ChatFormatting.AQUA)
                        .append(confirmButton()));
            } else {
                // Belum 3 detik, kasih tau sisa waktu
                int ticksLeft = witnessConfirmReadyAt - nightTicksElapsed;
                int secondsLeft = (int) Math.ceil(ticksLeft / 20.0);
                player.sendSystemMessage(Component.literal("Tunggu " + secondsLeft + " detik lagi sebelum konfirmasi.")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    // Method untuk mengirim ulang skip button ke Forensic Scientist
    private void resendSkipButtonToForensicScientist(ServerPlayer player) {
        if (state != State.NIGHT) return;
        if (!murdererWindowOpen && !witnessWindowOpen) return;
        
        List<Component> messages = new ArrayList<>();
        
        // Cek apakah masih ada Murderer yang left
        if (hasMurdererLeft && leftMurdererUuid != null && !murdererConfirmed) {
            // Cek apakah Murderer masih offline
            if (serverRef.getPlayerList().getPlayer(leftMurdererUuid) == null) {
                int secondsLeft = (int) Math.ceil(murdererAutoPickTicksLeft / 20.0);
                Component msg = Component.literal(leftMurdererName + " (Murderer) masih offline. Auto-pick dalam ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(".").withStyle(ChatFormatting.RED))
                        .append(skipRevealPrompt());
                messages.add(msg);
            } else {
                // Murderer sudah online, reset status
                hasMurdererLeft = false;
                leftMurdererUuid = null;
                leftMurdererName = "";
                murdererAutoPickAt = Integer.MAX_VALUE;
                murdererAutoPickTicksLeft = 0;
                resetCountdownActionBars();
            }
        }
        
        // Cek apakah masih ada Witness yang left
        if (hasWitnessLeft && leftWitnessUuid != null && !witnessConfirmed) {
            // Cek apakah Witness masih offline
            if (serverRef.getPlayerList().getPlayer(leftWitnessUuid) == null) {
                int secondsLeft = (int) Math.ceil(witnessAutoSkipTicksLeft / 20.0);
                Component msg = Component.literal(leftWitnessName + " (Witness) masih offline. Auto-skip dalam ")
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(".").withStyle(ChatFormatting.AQUA))
                        .append(skipRevealPrompt());
                messages.add(msg);
            } else {
                // Witness sudah online, reset status
                hasWitnessLeft = false;
                leftWitnessUuid = null;
                leftWitnessName = "";
                witnessAutoSkipAt = Integer.MAX_VALUE;
                witnessAutoSkipTicksLeft = 0;
                resetCountdownActionBars();
            }
        }
        
        // Kirim semua pesan ke Forensic Scientist
        for (Component msg : messages) {
            player.sendSystemMessage(msg);
        }
    }

    private void notifyRole(Role role, Component msg) {
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() != role) continue;
            ServerPlayer p = serverRef.getPlayerList().getPlayer(e.getKey());
            if (p != null) p.sendSystemMessage(msg);
        }
    }

    private UUID findRoleUuid(Role role) {
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == role) return e.getKey();
        }
        return null;
    }

    /**
     * Dipanggil pas "murderer buka mata" (nightKillersWakeAt). Kalo
     * murderer/accomplice ternyata udah offline dari sebelum night ini
     * mulai (SHUFFLE/REVEAL_DELAY/awal NIGHT pas masih tutup mata) --
     * onPlayerLeft gak sempet nangkep karena jendelanya belum kebuka --
     * langsung mulai timer auto-pick (kalo yang offline murderer) &
     * notify, TERLEPAS kapan dia sebenernya left.
     */
    private void checkMurdererWakeUpOffline() {
        if (murdererConfirmed) return;

        UUID murdererUuid = findRoleUuid(Role.murderer);
        UUID accompliceUuid = findRoleUuid(Role.accomplice);

        boolean murdererOffline = murdererUuid != null
                && serverRef.getPlayerList().getPlayer(murdererUuid) == null;
        boolean accompliceOffline = accompliceUuid != null
                && serverRef.getPlayerList().getPlayer(accompliceUuid) == null;

        if (!murdererOffline && !accompliceOffline) return;

        if (murdererOffline && murdererAutoPickAt == Integer.MAX_VALUE) {
            String murdererName = getPlayerName(murdererUuid);
            leftMurdererUuid = murdererUuid;
            leftMurdererName = murdererName;
            hasMurdererLeft = true;

            murdererAutoPickAt = nightTicksElapsed + LEAVE_TIMEOUT_TICKS;
            murdererAutoPickTicksLeft = LEAVE_TIMEOUT_TICKS;

            Component fsMsg = Component.literal(murdererName + " (Murderer) sudah offline. Auto-pick dalam ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(getOfflineRevealSeconds() + " detik").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(".").withStyle(ChatFormatting.RED))
                    .append(skipRevealPrompt());
            notifyRole(Role.forensic_scientist, fsMsg);

            if (!accompliceOffline) {
                notifyRole(Role.accomplice, Component.literal(murdererName + " (Murderer) sudah offline. Item bakal dipilih otomatis dalam " + getOfflineRevealSeconds() + " detik.")
                        .withStyle(ChatFormatting.RED));
            }

            updateAutoPickCountdown();
        } else if (accompliceOffline) {
            // Cuma accomplice yang offline, murderer masih ada -- gaada
            // timer yang perlu dimulai, notify FS & murderer aja.
            String accompliceName = getPlayerName(accompliceUuid);
            Component msg = Component.literal(accompliceName + " (Accomplice) sudah offline.").withStyle(ChatFormatting.RED);
            notifyRole(Role.forensic_scientist, msg);
            notifyRole(Role.murderer, msg);
        }

        // Witness masih tutup mata di fase ini (baru "bangun" belakangan,
        // giliran dia abis murderer selesai) -- jadi jangan notify dia di
        // sini. Kalo murderer/accomplice masih offline pas witness beneran
        // bangun nanti, checkWitnessWakeUpOffline() yang bakal ngasih tau
        // (generik, gak nyebut role spesifik).
    }

    /**
     * Dipanggil pas "witness buka mata" (openWitnessWindow). Kalo witness
     * udah offline dari sebelum giliran dia, langsung mulai timer
     * auto-skip. Sekaligus ngecek murderer/accomplice -- kalo masih
     * offline di titik ini, witness DIKASIH TAU (baru sekarang boleh,
     * karena ini emang giliran dia liat) lewat notifyWitnessKillerLeft(),
     * plus FS dikasih info role spesifik yang offline.
     */
    private void checkWitnessWakeUpOffline() {
        if (witnessConfirmed || nightWitnessUuid == null) return;

        boolean witnessOffline = serverRef.getPlayerList().getPlayer(nightWitnessUuid) == null;

        if (witnessOffline && witnessAutoSkipAt == Integer.MAX_VALUE) {
            String witnessName = getPlayerName(nightWitnessUuid);
            leftWitnessUuid = nightWitnessUuid;
            leftWitnessName = witnessName;
            hasWitnessLeft = true;

            witnessAutoSkipAt = nightTicksElapsed + LEAVE_TIMEOUT_TICKS;
            witnessAutoSkipTicksLeft = LEAVE_TIMEOUT_TICKS;

            Component fsMsg = Component.literal(witnessName + " (Witness) sudah offline. Auto-skip dalam ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(getOfflineRevealSeconds() + " detik").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(".").withStyle(ChatFormatting.AQUA))
                    .append(skipRevealPrompt());
            notifyRole(Role.forensic_scientist, fsMsg);

            updateWitnessSkipCountdown();
        }

        UUID murdererUuid = findRoleUuid(Role.murderer);
        UUID accompliceUuid = findRoleUuid(Role.accomplice);
        boolean murdererStillOffline = murdererUuid != null
                && serverRef.getPlayerList().getPlayer(murdererUuid) == null;
        boolean accompliceStillOffline = accompliceUuid != null
                && serverRef.getPlayerList().getPlayer(accompliceUuid) == null;

        if (murdererStillOffline) {
            notifyRole(Role.forensic_scientist, Component.literal(getPlayerName(murdererUuid) + " (Murderer) sudah offline.")
                    .withStyle(ChatFormatting.RED));
        }
        if (accompliceStillOffline) {
            notifyRole(Role.forensic_scientist, Component.literal(getPlayerName(accompliceUuid) + " (Accomplice) sudah offline.")
                    .withStyle(ChatFormatting.RED));
        }

        // Witness baru "bangun" sekarang, jadi baru di titik inilah dia
        // boleh dikasih tau kalo salah satu orang jahat gak ada -- satu
        // pesan per orang, format sama kaya pas dia left real-time di
        // tengah jendela witness (lihat notifyWitnessKillerLeft), tetep
        // gak nyebut role spesifik biar gak bocorin identitas.
        if (murdererStillOffline) {
            notifyWitnessKillerLeft(getPlayerName(murdererUuid));
        }
        if (accompliceStillOffline) {
            notifyWitnessKillerLeft(getPlayerName(accompliceUuid));
        }
    }

    /**
     * /deception skipreveal -- khusus OP / Forensic Scientist. Cuma
     * majuin SATU fase reveal yang lagi berlangsung, bukan langsung
     * lompat ke diskusi. Urutannya:
     *   1. Murderer masih milih -> di-random-in & confirm, lanjut ke
     *      fase witness (atau langsung wake kalo gaada witness).
     *   2. Lagi nunggu jeda sebelum witness buka mata -> buka sekarang.
     *   3. Witness masih liat -> confirm, lanjut ke fase wake-all.
     *   4. Lagi nunggu jeda sebelum semua orang buka mata -> buka sekarang.
     * Dipanggil lagi abis salah satu di atas bakal majuin fase
     * berikutnya, gak ngulang yang udah lewat.
     */
    public boolean skipReveal() {
        if (state != State.NIGHT) return false;

        // 1) Fase murderer lagi berlangsung.
        if (murdererWindowOpen && !murdererConfirmed) {
            murdererAutoPickAt = Integer.MAX_VALUE;
            if (!autoPickAndConfirmMurderer()) return false;
            closeKillersEyesAndScheduleNext();
            return true;
        }

        // 2) Killer udah confirm & tutup mata, masih nunggu jeda sebelum
        //    witness dibangunin -> buka sekarang juga.
        if (murdererConfirmed && !witnessConfirmed && !witnessWindowOpen
                && nightWitnessRevealAt != Integer.MAX_VALUE) {
            openWitnessWindow();
            return true;
        }

        // 3) Fase witness lagi berlangsung.
        if (witnessWindowOpen && !witnessConfirmed) {
            witnessAutoSkipAt = Integer.MAX_VALUE;
            finalizeWitnessConfirm();
            return true;
        }

        // 4) Udah lewat semua reveal, tinggal nunggu jeda "semua orang
        //    buka mata" sebelum ke diskusi -> majuin sekarang.
        if (nightWakeAllAt != Integer.MAX_VALUE || nightDoneAt != Integer.MAX_VALUE) {
            for (UUID uuid : new ArrayList<>(nightBlindfolded)) {
                ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
                if (p != null) removeBlindfold(p);
            }
            nightBlindfolded.clear();
            com.deception.network.ModNetworking.broadcastNightActionBar(Component.empty());
            com.deception.network.ModNetworking.broadcastNightTitle(
                    Component.literal("Semua orang buka mata").withStyle(ChatFormatting.GOLD),
                    Component.empty(), 10, NIGHT_WAKE_HOLD, 10);

            UUID fsUuid = null;
            for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
                if (e.getValue() == Role.forensic_scientist) { fsUuid = e.getKey(); break; }
            }
            giveForensicScientistPapers(serverRef, fsUuid);
            broadcastForensicWaitActionBar();

            nightWakeAllAt = Integer.MAX_VALUE;
            nightDoneAt = nightTicksElapsed + NIGHT_WAKE_HOLD;
            return true;
        }

        // Belum ada fase yang lagi berjalan (misal masih fase "semua
        // tutup mata" di awal night, sebelum murderer dibangunin).
        return false;
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

    // ---------- Investigation paper (Forensic Scientist) ----------

    // Kalo FS lagi offline pas giveForensicScientistPapers dipanggil,
    // kategori yang UDAH DIACAK disimpen di sini (bukan di-random ulang)
    // biar pas dia rejoin (onPlayerRejoined) langsung dikasih yang sama.
    private UUID pendingForensicScientistUuid = null;
    private List<ForensicPaperData.Category> pendingForensicPaperCategories = null;
    // Guard biar gak dobel dikasih -- "semua orang buka mata" bisa kepicu
    // dari tick normal ATAU dari /deception skipreveal, keduanya manggil
    // giveForensicScientistPapers.
    private boolean forensicPapersGivenThisRound = false;

    /**
     * Kasih 6 investigation paper kosong ke Forensic Scientist pas reveal
     * kelar (dipanggil dari tickNightSequence pas "Semua orang buka mata"):
     * 2 kategori tetap (penyebab kematian, lokasi kejadian) + 4 kategori
     * acak dari scene tiles. Tiap stack ditandain NBT "ForensicCategory"
     * (dibaca InvestigationPaperItem buat buka ForensicPaperScreen) +
     * "CanPlaceOn" black_wool (soalnya semua player gamemode adventure pas
     * main, adventure mode gak bisa naro block kecuali item-nya punya tag
     * ini). Kalo FS-nya lagi offline, disimpen dulu -- lihat
     * pendingForensicScientistUuid & onPlayerRejoined.
     */
    private void giveForensicScientistPapers(MinecraftServer server, UUID fsUuid) {
        if (fsUuid == null || forensicPapersGivenThisRound) return;
        forensicPapersGivenThisRound = true;

        List<ForensicPaperData.Category> categories = new ArrayList<>();
        categories.add(ForensicPaperData.CAUSE_OF_DEATH);
        categories.add(ForensicPaperData.LOCATION_OF_CRIME);

        List<ForensicPaperData.Category> sceneTilePool = new ArrayList<>(ForensicPaperData.SCENE_TILES);
        Collections.shuffle(sceneTilePool, shuffleRandom);
        categories.addAll(sceneTilePool.subList(0, 4));
        PresentationManager.get().recordGivenCategories(sceneTilePool.subList(0, 4));

        ServerPlayer fsPlayer = server.getPlayerList().getPlayer(fsUuid);
        if (fsPlayer == null) {
            pendingForensicScientistUuid = fsUuid;
            pendingForensicPaperCategories = categories;
            return;
        }
        deliverForensicPapers(fsPlayer, categories);
    }

    private void deliverForensicPapers(ServerPlayer fsPlayer, List<ForensicPaperData.Category> categories) {
        for (ForensicPaperData.Category category : categories) {
            ItemStack stack = new ItemStack(ModItems.INVESTIGATION_PAPER.get());
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString("ForensicCategory", category.displayName());
            ListTag canPlaceOn = new ListTag();
            canPlaceOn.add(StringTag.valueOf("minecraft:black_wool"));
            tag.put("CanPlaceOn", canPlaceOn);
            stack.setHoverName(Component.literal(category.displayName()));

            fsPlayer.getInventory().add(stack);
        }
    }

    /** Tag entitas display sama kayak spawnOwnerHead, biar restoreArena() otomatis bersihin. */
    public void spawnInvestigationPaperText(ServerLevel level, BlockPos pos, Direction facing, String text) {
        // Nempel di permukaan kertasnya (deket tepi luar block, bukan di
        // tengah) -- 0.5 = tepat di tepi, dikurangin dikit biar gak z-fight
        // sama mesh kertasnya sendiri.
        double offset = 0.47;
        // TextDisplay nge-anchor teksnya dari BAWAH terus naik ke atas
        // (posisi Y = tepi bawah teks, bukan tengah) -- makanya dikurangin
        // biar keliatan di tengah kertas, bukan ngambang di atas block-nya.
        double textY = pos.getY() + 0.4;
        double textX = pos.getX() + -0.45;
        double textZ = pos.getZ() + 0.5;

        switch (facing) {
            case NORTH -> textZ -= offset;
            case SOUTH -> textZ += offset;
            case WEST -> textX -= offset;
            case EAST -> textX += offset;
            default -> {}
        }

        // Jawaban lebih dari 1 kata -> pecah per kata jadi baris baru biar
        // muat di kertas 1 block yang kecil.
        String displayText = text.contains(" ") ? text.replace(" ", "\n") : text;

        Display.TextDisplay display = (Display.TextDisplay) EntityType.TEXT_DISPLAY.create(level);
        if (display == null) return;

        CompoundTag tag = new CompoundTag();
        tag.putString("text", Component.Serializer.toJson(
                Component.literal(displayText).withStyle(ChatFormatting.BLACK)));
        tag.putString("billboard", "fixed");
        tag.putInt("background", 0);
        tag.putInt("line_width", 90);
        tag.putByte("see_through", (byte) 1);
        tag.putByte("default_background", (byte) 0);

        CompoundTag transformationTag = new CompoundTag();
        transformationTag.put("translation", floatList(0F, 0F, 0F));
        transformationTag.put("left_rotation", floatList(0F, 0F, 0F, 1F));
        transformationTag.put("right_rotation", floatList(0F, 0F, 0F, 1F));
        transformationTag.put("scale", floatList(0.5F, 0.5F, 0.5F));
        tag.put("transformation", transformationTag);

        display.load(tag);
        display.addTag("deception_display");
        display.setYRot(facing.toYRot());
        display.setPos(textX, textY, textZ);

        level.addFreshEntity(display);

        UUID oldId = investigationPaperTextByPos.put(pos.immutable(), display.getUUID());
        if (oldId != null) {
            var old = level.getEntity(oldId);
            if (old != null) old.discard();
        }
    }

    /** Dipanggil dari InvestigationPaperBlock#onRemove pas block-nya beneran ilang (bukan cuma ganti state). */
    public void despawnInvestigationPaperText(ServerLevel level, BlockPos pos) {
        UUID id = investigationPaperTextByPos.remove(pos);
        if (id == null) return;
        var entity = level.getEntity(id);
        if (entity != null) entity.discard();
    }

    // ---------- Tick loop ----------

    public void tick() {
        globalTickCounter++;
        if (serverRef != null && state != State.IDLE) {
            PresentationManager.get().tick(serverRef);
        }
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
                state = State.REVEAL_DELAY;
                revealDelayTicks = NIGHT_REVEAL_DELAY;  
            }
        } else if (state == State.REVEAL_DELAY) {
            revealDelayTicks--;
            if (revealDelayTicks <= 0) {
                startNightSequence(serverRef);
            }
        } else if (state == State.NIGHT) {
            tickNightSequence();
        } else if (state == State.DISCUSS) {
            if (PresentationManager.get().isBusy()) {
                // Ada confession yang lagi nunggu keputusan FS atau shootout
                // murderer-witness lagi jalan -- diskusi DIJEDA (timer gak
                // jalan, actionbar-nya PresentationManager.tick() yang urus
                // sendiri, biar gak rebutan sama actionbar diskusi).
            } else if (!PresentationManager.get().isDiscussionStarted()) {
                // Belum ada clue dari FS -- diskusi belum "beneran" mulai,
                // timer belum jalan, tetep tampilin actionbar "menunggu".
                if (globalTickCounter % 10 == 0) {
                    broadcastForensicWaitActionBar();
                }
            } else {
                discussTicksLeft--;
                if (globalTickCounter % 20 == 0) {
                    PresentationManager.get().broadcastDiscussCountdown(discussTicksLeft);
                }
                if (discussTicksLeft <= 0) {
                    state = State.PRESENTASI;
                    PresentationManager.get().startPresentasi(serverRef, computeClockwisePresentationOrder());
                }
            }
        } else if (state == State.PRESENTASI) {
            if (PresentationManager.get().isBusy()) {
                // Sama kayak di atas -- presentasi dijeda selagi confession/shootout jalan.
            } else if (PresentationManager.get().tickPresentasi(serverRef)) {
                if (PresentationManager.get().advanceRound(serverRef)) {
                    discussTicksLeft = discussTimerSeconds * 20;
                    state = State.DISCUSS;
                } else {
                    // Ronde 3 abis tanpa ada yang confess -- pembunuh gak
                    // ketauan sampe akhir, langsung menang.
                    PresentationManager.get().onRoundsExhausted(serverRef);
                }
            }
        }
    }

    /** Dipanggil PresentationManager#voteSkip pas mayoritas player online vote skip diskusi. */
    public void forceEndDiscussion(MinecraftServer server) {
        if (state != State.DISCUSS) return;
        state = State.PRESENTASI;
        PresentationManager.get().startPresentasi(server, computeClockwisePresentationOrder());
    }

    // Logo Discord itu GLYPH dari font custom kita sendiri
    // (assets/deception/font/icons.json -> textures/font/discord.png), bukan
    // emoji/karakter unicode -- font vanilla gak punya logo begini. Sengaja
    // font terpisah, BUKAN nimpa "minecraft:default", biar font vanilla gak
    // keganggu sama sekali.
    private static final net.minecraft.resources.ResourceLocation ICON_FONT =
            new net.minecraft.resources.ResourceLocation(com.deception.DeceptionMod.MOD_ID, "icons");
    // U+E000 (private use area) -- karakter mentah, HARUS sama persis sama
    // "chars" di icons.json. Aman karena compile-nya dipaksa UTF-8 (lihat
    // options.encoding di build.gradle); jangan simpen file ini pake
    // encoding lain, glyph-nya bakal rusak.
    private static final String DISCORD_GLYPH = "";

    /**
     * Warnanya WAJIB di-set putih: glyph bitmap di Minecraft itu dikaliin
     * sama warna teks-nya, jadi kalo kena warna lain (atau kebawa warna
     * komponen induk) logonya ikut berubah warna. Putih = pengali netral,
     * warna asli texture-nya yang keluar.
     */
    private static Component discordIcon() {
        return Component.literal(DISCORD_GLYPH)
                .withStyle(style -> style.withFont(ICON_FONT).withColor(ChatFormatting.WHITE));
    }

    public void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    public State getState() {
        return state;
    }
}