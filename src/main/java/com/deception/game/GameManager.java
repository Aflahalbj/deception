package com.deception.game;

import com.deception.command.RoleDescriptions;
import com.deception.init.ClusterData;
import com.deception.init.ModBlocks;
import com.deception.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
        IDLE, COUNTDOWN, SHUFFLE, REVEAL_DELAY, NIGHT, DISCUSS, PRESENTASI, ALLIES, RUNNING
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
    private Boolean protectiveDetailOverride = null;
    private Boolean labTechnicianOverride = null;
    private Boolean insideManOverride = null;

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
    private int nightProtectiveDetailRevealAt;
    private int nightWakeAllAt;
    private int nightDoneAt;
    private final Set<UUID> nightBlindfolded = new HashSet<>();
    private final List<UUID> nightKillers = new ArrayList<>();
    private UUID nightWitnessUuid;
    private UUID nightProtectiveDetailUuid;
    private boolean murdererConfirmed = false;
    private final Set<BlockPos> processedClicks = new HashSet<>();
    private long lastClickTick = 0;
    private int murdererAutoPickTicksLeft = 0;
    private int witnessAutoSkipTicksLeft = 0;
    private int protectiveDetailAutoSkipTicksLeft = 0;
    private UUID leftMurdererUuid = null;
    private UUID leftWitnessUuid = null;
    private UUID leftProtectiveDetailUuid = null;
    private boolean hasMurdererLeft = false;
    private boolean hasWitnessLeft = false;
    private boolean hasProtectiveDetailLeft = false;
    private String leftMurdererName = "";
    private String leftWitnessName = "";
    private String leftProtectiveDetailName = "";

    // ---------- Murderer pilih item means/clue asli pas night ----------
    // 2 posisi: 1 untuk means, 1 untuk clue
    private BlockPos murdererSelectedMeansBacking = null;
    private BlockPos murdererSelectedClueBacking = null;
    // posisi block clue asli (bukan backing-nya) buat ambil nama item pas kirim pesan
    private BlockPos murdererSelectedMeansPos = null;
    private BlockPos murdererSelectedCluePos = null;

    // ---------- Witness konfirmasi udah liat (kaya murdererConfirmed tapi buat witness) ----------
    private boolean witnessConfirmed = false;

    // ---------- Protective Detail: giliran terakhir fase malam, liat siapa Witness ----------
    private boolean protectiveDetailConfirmed = false;
    private boolean protectiveDetailWindowOpen = false;
    private int protectiveDetailAutoSkipAt = Integer.MAX_VALUE;
    private int protectiveDetailConfirmReadyAt = Integer.MAX_VALUE;

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

    // ---------- Fase Allies (antara ronde 1 & ronde 2) ----------
    // Urutannya persis kayak rulebook Undercover Allies: semua tutup mata ->
    // Lab Technician nanya 1 item ke FS -> LT tutup mata -> Inside Man nunjuk
    // 1 orang buat dicabut badge-nya -> IM tutup mata -> semua buka mata
    // (badge-nya baru beneran dicabut di sini) -> ronde 2 mulai.
    private int alliesTicksElapsed;
    private int alliesLabTechWakeAt;
    private int alliesInsideManWakeAt;
    private int alliesWakeAllAt;
    private int alliesDoneAt;
    private UUID alliesLabTechUuid;
    private UUID alliesInsideManUuid;

    private boolean labTechWindowOpen = false;
    private boolean labTechConfirmed = false;
    /** true selagi pertanyaan LT nunggu jawaban [BENAR]/[SALAH] dari FS. */
    private boolean labTechAwaitingAnswer = false;
    private BlockPos labTechSelectedPos = null;
    private BlockPos labTechSelectedBacking = null;
    private int labTechAutoSkipAt = Integer.MAX_VALUE;
    private int labTechAutoSkipTicksLeft = 0;
    private UUID leftLabTechUuid = null;
    private boolean hasLabTechLeft = false;
    private String leftLabTechName = "";

    private boolean insideManWindowOpen = false;
    private boolean insideManConfirmed = false;
    private UUID insideManTargetUuid = null;
    private int insideManAutoSkipAt = Integer.MAX_VALUE;
    private int insideManAutoSkipTicksLeft = 0;
    private UUID leftInsideManUuid = null;
    private boolean hasInsideManLeft = false;
    private String leftInsideManName = "";

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
        switch (role) {
            case accomplice -> accompliceOverride = enabled;
            case witness -> witnessOverride = enabled;
            case protective_detail -> protectiveDetailOverride = enabled;
            case lab_technician -> labTechnicianOverride = enabled;
            case inside_man -> insideManOverride = enabled;
            default -> { }
        }
    }

    // Lepas override manual balik ke "auto" (ikut tabel RoleComposition) --
    // dipake sama toggle 3-state (Auto/Aktif/Nonaktif) di GUI /deception setting.
    public void clearCustomRoleOverride(Role role) {
        switch (role) {
            case accomplice -> accompliceOverride = null;
            case witness -> witnessOverride = null;
            case protective_detail -> protectiveDetailOverride = null;
            case lab_technician -> labTechnicianOverride = null;
            case inside_man -> insideManOverride = null;
            default -> { }
        }
    }

    public void resetCustomRoleOverrides() {
        accompliceOverride = null;
        witnessOverride = null;
        protectiveDetailOverride = null;
        labTechnicianOverride = null;
        insideManOverride = null;
    }

    public Boolean getAccompliceOverride() {
        return accompliceOverride;
    }

    public Boolean getWitnessOverride() {
        return witnessOverride;
    }

    public Boolean getProtectiveDetailOverride() {
        return protectiveDetailOverride;
    }

    public Boolean getLabTechnicianOverride() {
        return labTechnicianOverride;
    }

    public Boolean getInsideManOverride() {
        return insideManOverride;
    }

    // Nilai yang bakal dipake kalo mode-nya "auto" (belum di-override manual),
    // dihitung dari tabel default RoleComposition sesuai jumlah player teregistrasi.
    public boolean resolveAccompliceDefault() {
        return new RoleComposition(registeredPlayers.size()).isAccompliceEnabled();
    }

    public boolean resolveWitnessDefault() {
        return new RoleComposition(registeredPlayers.size()).isWitnessEnabled();
    }

    public boolean resolveProtectiveDetailDefault() {
        return new RoleComposition(registeredPlayers.size()).isProtectiveDetailEnabled();
    }

    public boolean resolveLabTechnicianDefault() {
        return new RoleComposition(registeredPlayers.size()).isLabTechnicianEnabled();
    }

    public boolean resolveInsideManDefault() {
        return new RoleComposition(registeredPlayers.size()).isInsideManEnabled();
    }

    /**
     * Komposisi yang lagi berlaku: tabel default sesuai jumlah player, ditimpa
     * override manual dari /customrole. Satu tempat, biar computeActiveRolePool
     * & computeRoleAssignments gak pernah beda aturan.
     */
    private RoleComposition currentComposition() {
        RoleComposition composition = new RoleComposition(registeredPlayers.size());
        if (accompliceOverride != null) composition.setAccompliceEnabled(accompliceOverride);
        if (witnessOverride != null) composition.setWitnessEnabled(witnessOverride);
        if (protectiveDetailOverride != null) composition.setProtectiveDetailEnabled(protectiveDetailOverride);
        if (labTechnicianOverride != null) composition.setLabTechnicianEnabled(labTechnicianOverride);
        if (insideManOverride != null) composition.setInsideManEnabled(insideManOverride);
        return composition;
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

        // Tarik semua ke arena DULUAN, sebelum countdown jalan. Bukan cuma
        // biar mereka ngumpul: restoreArena cuma bisa nemu entitas display
        // (kepala pemain, teks paper) yang chunk-nya lagi KE-LOAD -- kalo gak
        // ada satu pun pemain di dimensi arena, sisa entitas game kemarin
        // gak keliatan sama sapuannya dan nyangkut di sana selamanya.
        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) teleportToArenaLobby(server, player);
        }

        // Sapuan pertama. Chunk arena kemungkinan besar BELUM kelar ke-load
        // di tick ini (teleport barusan cuma ngantri-in), jadi yang ini
        // ngurus state di memori (CLUSTERS, PresentationManager, dll); yang
        // beneran nyapu entitas nyusul di akhir countdown -- lihat tick().
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
                // Jangkauan klik ENTITAS ikut dilebarin: Inside Man nunjuk
                // targetnya dengan klik kanan orangnya (fase Allies), dan
                // pas itu semua orang lagi diem di posisi terakhir masing-
                // masing -- bisa jauh dari tempat dia berdiri. Aman: PvP mati
                // & semua gamemode adventure, jadi gak ada efek samping lain.
                AttributeInstance entityReach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
                if (entityReach != null) {
                    entityReach.setBaseValue(30.0D);
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
        // clearTitles=false: abortGame normalnya nyapu title semua orang, dan
        // itu jalan di tick yang SAMA kayak title kemenangan di atas -- jadi
        // kalo dibiarin, "Kamu Menang!"/"Kamu Kalah!" langsung ketimpa layar
        // kosong sebelum sempet keliatan sedetik pun.
        abortGame(server, chatMessage, false);
    }

    /** Inside Man menang bareng Murderer & Accomplice (lihat rulebook Undercover Allies). */
    private boolean isMurdererTeam(Role role) {
        return role == Role.murderer || role == Role.accomplice || role == Role.inside_man;
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
        abortGame(server, reason, true);
    }

    /**
     * @param clearTitles false kalo pemanggilnya BARU AJA naro title yang mau
     *        dibiarin kebaca (lihat endGameWithResult). Actionbar tetep
     *        dibersihin -- yang dipertahankan cuma title/subtitle.
     */
    private void abortGame(MinecraftServer server, Component reason, boolean clearTitles) {
        // Disalin DULUAN: field aslinya di-null-in di bawah (bagian reset
        // state), padahal restore backing-nya baru jalan setelah itu -- kalo
        // baca langsung dari field-nya, yang kebaca selalu null dan
        // restoreBacking gak pernah kepanggil.
        BlockPos selectedMeansBacking = this.murdererSelectedMeansBacking;
        BlockPos selectedClueBacking = this.murdererSelectedClueBacking;
        BlockPos selectedLabTechBacking = this.labTechSelectedBacking;

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

        this.protectiveDetailConfirmed = false;
        this.protectiveDetailWindowOpen = false;
        this.nightProtectiveDetailRevealAt = Integer.MAX_VALUE;
        this.protectiveDetailAutoSkipAt = Integer.MAX_VALUE;
        this.protectiveDetailConfirmReadyAt = Integer.MAX_VALUE;
        this.protectiveDetailAutoSkipTicksLeft = 0;
        this.leftProtectiveDetailUuid = null;
        this.hasProtectiveDetailLeft = false;
        this.leftProtectiveDetailName = "";

        resetAlliesState();

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
            if (selectedLabTechBacking != null) {
                restoreBacking(level, selectedLabTechBacking);
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

                    // Panel pilihan (means/clue, item LT, target IM) juga
                    // state client -- gak boleh nyangkut abis game selesai.
                    com.deception.network.ModNetworking.clearSelectionHud(player);

                    // Role udah dikosongin di atas, jadi ini otomatis ngirim
                    // clear -- HUD role gak boleh nyangkut abis game selesai.
                    sendRoleVisibleHud(player);

                    if (clearTitles) {
                        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 0, 0));
                        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
                        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                    }
                    player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));

                    AttributeInstance blockReach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
                    if (blockReach != null) {
                        blockReach.setBaseValue(blockReach.getAttribute().getDefaultValue());
                    }
                    AttributeInstance entityReach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
                    if (entityReach != null) {
                        entityReach.setBaseValue(entityReach.getAttribute().getDefaultValue());
                    }

                    // Paling belakangan: kumpulin semua di titik kumpul arena,
                    // BUKAN dipulangin ke dunia asal. Abis satu game biasanya
                    // langsung main lagi, jadi lebih enak semua udah ngumpul
                    // di tempat yang sama daripada kepencar balik ke posisi
                    // masing-masing. Yang mau keluar tinggal /deception gotoworld.
                    teleportToArenaLobby(target, player);
                }
            }

            // Clear custom actionbar
            com.deception.network.ModNetworking.broadcastNightActionBar(Component.empty());

            nightBlindfolded.clear();
            nightKillers.clear();
            nightWitnessUuid = null;
            nightProtectiveDetailUuid = null;
            broadcast(target, reason);
        }
    }

    /** Semua state fase Allies balik ke nol -- dipanggil abortGame & startAlliesSequence. */
    private void resetAlliesState() {
        this.alliesTicksElapsed = 0;
        this.alliesLabTechWakeAt = Integer.MAX_VALUE;
        this.alliesInsideManWakeAt = Integer.MAX_VALUE;
        this.alliesWakeAllAt = Integer.MAX_VALUE;
        this.alliesDoneAt = Integer.MAX_VALUE;
        this.alliesLabTechUuid = null;
        this.alliesInsideManUuid = null;

        this.labTechWindowOpen = false;
        this.labTechConfirmed = false;
        this.labTechConfirmPromptSent = false;
        this.insideManConfirmPromptSent = false;
        this.labTechAwaitingAnswer = false;
        this.labTechAnswer = null;
        this.labTechSelectedPos = null;
        this.labTechSelectedBacking = null;
        this.labTechAutoSkipAt = Integer.MAX_VALUE;
        this.labTechAutoSkipTicksLeft = 0;
        this.leftLabTechUuid = null;
        this.hasLabTechLeft = false;
        this.leftLabTechName = "";

        this.insideManWindowOpen = false;
        this.insideManConfirmed = false;
        this.insideManTargetUuid = null;
        this.insideManAutoSkipAt = Integer.MAX_VALUE;
        this.insideManAutoSkipTicksLeft = 0;
        this.leftInsideManUuid = null;
        this.hasInsideManLeft = false;
        this.leftInsideManName = "";
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
        Map<Role, Integer> counts = currentComposition().resolve();
        List<Role> pool = new ArrayList<>();
        for (Map.Entry<Role, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0) {
                pool.add(entry.getKey());
            }
        }
        return pool;
    }

    private void computeRoleAssignments() {
        Map<Role, Integer> counts = currentComposition().resolve();

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
        this.murdererConfirmPromptSent = false;
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
        this.protectiveDetailConfirmed = false;
        this.protectiveDetailWindowOpen = false;
        this.protectiveDetailConfirmReadyAt = Integer.MAX_VALUE;
        this.protectiveDetailAutoSkipAt = Integer.MAX_VALUE;

        UUID fsUuid = null;
        nightKillers.clear();
        nightWitnessUuid = null;
        nightProtectiveDetailUuid = null;
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() == Role.forensic_scientist) fsUuid = e.getKey();
            else if (e.getValue() == Role.murderer || e.getValue() == Role.accomplice) nightKillers.add(e.getKey());
            else if (e.getValue() == Role.witness) nightWitnessUuid = e.getKey();
            else if (e.getValue() == Role.protective_detail) nightProtectiveDetailUuid = e.getKey();
        }

        // Yang diliat Protective Detail cuma Witness. Kalo Witness-nya gak
        // ada (misal role-nya dipaksa manual lewat /deception setrole tanpa
        // witness), gilirannya dicoret aja -- gak ada yang bisa direveal.
        if (nightWitnessUuid == null) {
            nightProtectiveDetailUuid = null;
        }

        nightKillersWakeAt = NIGHT_PRECLOSE_DELAY + NIGHT_CLOSE_HOLD;

        nightWitnessRevealAt = Integer.MAX_VALUE;
        nightProtectiveDetailRevealAt = Integer.MAX_VALUE;
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
            refreshMurdererHud(null);

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

        if (nightTicksElapsed == protectiveDetailAutoSkipAt && protectiveDetailAutoSkipAt != Integer.MAX_VALUE) {
            finalizeProtectiveDetailConfirm();
        }

        if (nightTicksElapsed == nightWitnessRevealAt && nightWitnessRevealAt != Integer.MAX_VALUE) {
            openWitnessWindow();
        }

        if (nightTicksElapsed == nightProtectiveDetailRevealAt && nightProtectiveDetailRevealAt != Integer.MAX_VALUE) {
            openProtectiveDetailWindow();
        }

        if (nightTicksElapsed == protectiveDetailConfirmReadyAt && protectiveDetailConfirmReadyAt != Integer.MAX_VALUE) {
            protectiveDetailConfirmReadyAt = Integer.MAX_VALUE;
            if (protectiveDetailWindowOpen && !protectiveDetailConfirmed && nightProtectiveDetailUuid != null) {
                ServerPlayer pd = serverRef.getPlayerList().getPlayer(nightProtectiveDetailUuid);
                if (pd != null) {
                    pd.sendSystemMessage(Component.literal("Selesai melihat? ").withStyle(ChatFormatting.GREEN)
                            .append(confirmButton()));
                }
            }
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

        // Countdown untuk auto-skip protective detail
        if (protectiveDetailAutoSkipAt != Integer.MAX_VALUE && protectiveDetailWindowOpen && !protectiveDetailConfirmed) {
            protectiveDetailAutoSkipTicksLeft = protectiveDetailAutoSkipAt - nightTicksElapsed;
            if (protectiveDetailAutoSkipTicksLeft > 0 && protectiveDetailAutoSkipTicksLeft % 20 == 0) {
                updateProtectiveDetailSkipCountdown();
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
            refreshMurdererHud(Component.literal("Itu bukan cluster kamu.").withStyle(ChatFormatting.RED));
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
        } else {
            if (murdererSelectedClueBacking != null && !murdererSelectedClueBacking.equals(backingPos)) {
                restoreBacking(level, murdererSelectedClueBacking);
            }
            level.setBlock(backingPos, Blocks.GREEN_CONCRETE.defaultBlockState(), 3);
            murdererSelectedClueBacking = backingPos;
            murdererSelectedCluePos = pos;
        }

        // Umpan baliknya lewat panel HUD yang diganti utuh, bukan baris chat
        // baru -- lihat komentar di atas HUD_HINT_CONFIRM.
        refreshMurdererHud(null);
        maybeSendMurdererConfirmPrompt(player);
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
    // ---------- Panel pilihan di layar (pengganti spam chat) ----------
    // Murderer, Lab Technician & Inside Man itu interaksi yang diulang-ulang:
    // ganti pilihan sepuluh kali = sepuluh baris chat kalau dibales pakai
    // sendSystemMessage. Semua umpan balik "pilihanmu sekarang apa" dipindah ke
    // panel HUD yang DIGANTI utuh tiap update (lihat network/SelectionHudPacket),
    // dan chat cuma kebagian satu tombol [KONFIRMASI] per giliran.

    private static final Component HUD_HINT_CONFIRM =
            Component.literal("Tekan [KONFIRMASI] di chat").withStyle(ChatFormatting.GREEN);

    // Tombol [KONFIRMASI] udah dikirim ke chat giliran ini? Biar gak kekirim
    // ulang tiap kali pilihannya diganti.
    private boolean murdererConfirmPromptSent = false;
    private boolean labTechConfirmPromptSent = false;
    private boolean insideManConfirmPromptSent = false;

    private void sendSelectionHudTo(UUID uuid, List<Component> lines, Component warning) {
        if (serverRef == null || uuid == null) return;
        ServerPlayer player = serverRef.getPlayerList().getPlayer(uuid);
        if (player != null) {
            com.deception.network.ModNetworking.sendSelectionHud(player,
                    new com.deception.network.SelectionHudPacket(lines, warning));
        }
    }

    private void clearSelectionHud(UUID uuid) {
        if (serverRef == null || uuid == null) return;
        ServerPlayer player = serverRef.getPlayerList().getPlayer(uuid);
        if (player != null) com.deception.network.ModNetworking.clearSelectionHud(player);
    }

    /** Satu baris "Label: nilai"; nilai null digambar sebagai "-" yang redup. */
    private Component hudRow(String label, String value) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value == null ? "-" : value)
                        .withStyle(value == null ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE));
    }

    /** {@code warning} null = cuma gambar ulang panelnya tanpa baris penolakan. */
    private void refreshMurdererHud(Component warning) {
        if (serverRef == null) return;
        UUID murdererUuid = findRoleUuid(Role.murderer);
        if (murdererUuid == null) return;

        ServerLevel level = ArenaDimension.level(serverRef);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Pilihan kamu").withStyle(ChatFormatting.GOLD));
        lines.add(hudRow("Means", murdererSelectedMeansPos == null ? null : getItemName(level, murdererSelectedMeansPos).replace("Means: ", "")));
        lines.add(hudRow("Clue", murdererSelectedCluePos == null ? null : getItemName(level, murdererSelectedCluePos).replace("Clue: ", "")));
        lines.add(murdererSelectedMeansBacking != null && murdererSelectedClueBacking != null
                ? HUD_HINT_CONFIRM
                : Component.literal("Klik kanan item di cluster kamu").withStyle(ChatFormatting.GRAY));

        sendSelectionHudTo(murdererUuid, lines, warning);
    }

    private void maybeSendMurdererConfirmPrompt(ServerPlayer player) {
        if (murdererConfirmPromptSent) return;
        if (murdererSelectedMeansBacking == null || murdererSelectedClueBacking == null) return;
        murdererConfirmPromptSent = true;
        player.sendSystemMessage(Component.literal("Means & clue sudah dipilih. ").withStyle(ChatFormatting.GOLD)
                .append(confirmButton()));
    }

    private void refreshLabTechHud(Component warning) {
        if (serverRef == null || alliesLabTechUuid == null) return;

        ServerLevel level = ArenaDimension.level(serverRef);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Item diperiksa").withStyle(ChatFormatting.AQUA));
        lines.add(hudRow("Item", labTechSelectedPos == null ? null : getItemName(level, labTechSelectedPos)));
        lines.add(labTechSelectedPos != null
                ? HUD_HINT_CONFIRM
                : Component.literal("Klik kanan 1 item di TKP").withStyle(ChatFormatting.GRAY));

        sendSelectionHudTo(alliesLabTechUuid, lines, warning);
    }

    private void maybeSendLabTechConfirmPrompt(ServerPlayer player) {
        if (labTechConfirmPromptSent || labTechSelectedPos == null) return;
        labTechConfirmPromptSent = true;
        player.sendSystemMessage(Component.literal("Item sudah dipilih. ").withStyle(ChatFormatting.AQUA)
                .append(confirmButton()));
    }

    private void refreshInsideManHud(Component warning) {
        if (serverRef == null || alliesInsideManUuid == null) return;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Target badge").withStyle(ChatFormatting.RED));
        lines.add(hudRow("Target", insideManTargetUuid == null ? null : getPlayerName(insideManTargetUuid)));
        lines.add(insideManTargetUuid != null
                ? HUD_HINT_CONFIRM
                : Component.literal("Klik kanan orangnya").withStyle(ChatFormatting.GRAY));

        sendSelectionHudTo(alliesInsideManUuid, lines, warning);
    }

    private void maybeSendInsideManConfirmPrompt(ServerPlayer player) {
        if (insideManConfirmPromptSent || insideManTargetUuid == null) return;
        insideManConfirmPromptSent = true;
        player.sendSystemMessage(Component.literal("Target sudah dipilih. ").withStyle(ChatFormatting.RED)
                .append(confirmButton()));
    }

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
        if (state == State.ALLIES) {
            return onAlliesConfirmCommand(player);
        }
        if (state != State.NIGHT) return false;
        UUID uuid = player.getUUID();
        Role role = roleAssignments.get(uuid);
        if (role == null) return false;

        if (role == Role.murderer) {
            if (murdererConfirmed) return false;

            if (murdererSelectedMeansBacking == null || murdererSelectedClueBacking == null) {
                String missing = murdererSelectedMeansBacking == null && murdererSelectedClueBacking == null
                        ? "means & clue" : murdererSelectedClueBacking == null ? "clue" : "means";
                refreshMurdererHud(Component.literal("Pilih " + missing + " dulu.").withStyle(ChatFormatting.RED));
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

        if (role == Role.protective_detail) {
            if (protectiveDetailConfirmed) return false;
            if (!uuid.equals(nightProtectiveDetailUuid)) return false;
            if (!protectiveDetailWindowOpen) {
                player.sendSystemMessage(Component.literal("Belum waktunya konfirmasi.").withStyle(ChatFormatting.RED));
                return true;
            }
            if (protectiveDetailConfirmReadyAt != Integer.MAX_VALUE && nightTicksElapsed < protectiveDetailConfirmReadyAt) {
                player.sendSystemMessage(Component.literal("Tunggu beberapa detik dulu sebelum konfirmasi.").withStyle(ChatFormatting.RED));
                return true;
            }

            player.sendSystemMessage(buildProtectiveDetailResultLine());
            finalizeProtectiveDetailConfirm();

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

        sendActionBarToRole(Role.forensic_scientist, witnessSkipCountdownBar(secondsLeft));
    }

    private void updateProtectiveDetailSkipCountdown() {
        if (serverRef == null) return;
        int secondsLeft = (int) Math.ceil(protectiveDetailAutoSkipTicksLeft / 20.0);
        if (secondsLeft <= 0) return;

        sendActionBarToRole(Role.forensic_scientist, protectiveDetailSkipCountdownBar(secondsLeft));
    }

    private static Component witnessSkipCountdownBar(int secondsLeft) {
        return Component.literal("⏱ Auto-skip Witness dalam ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW));
    }

    private static Component protectiveDetailSkipCountdownBar(int secondsLeft) {
        return Component.literal("⏱ Auto-skip Protective Detail dalam ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW));
    }

    /** Actionbar vanilla ke semua pemegang {@code role} yang lagi online. */
    private void sendActionBarToRole(Role role, Component message) {
        if (serverRef == null) return;
        for (Map.Entry<UUID, Role> e : roleAssignments.entrySet()) {
            if (e.getValue() != role) continue;
            ServerPlayer p = serverRef.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                p.connection.send(new ClientboundSetActionBarTextPacket(message));
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
        clearSelectionHud(findRoleUuid(Role.murderer));

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
            scheduleAfterWitness();
        }
    }

    /**
     * Fase sesudah giliran witness: Protective Detail kalo ada, kalo enggak
     * langsung "semua orang buka mata". Dipanggil dari DUA jalur -- abis
     * witness selesai, dan abis murderer selesai kalo witness-nya gak ada /
     * udah kepegang duluan.
     */
    private void scheduleAfterWitness() {
        boolean hasProtectiveDetail = nightProtectiveDetailUuid != null && !protectiveDetailConfirmed;
        if (hasProtectiveDetail) {
            nightProtectiveDetailRevealAt = nightTicksElapsed + NIGHT_CLOSE_HOLD;
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

        // Giliran terakhir fase malam: Protective Detail (kalo ada).
        scheduleAfterWitness();
    }

    /**
     * Buka mata Protective Detail & nyalain glow di Witness. Kembaran persis
     * openWitnessWindow, cuma yang "nonton" & yang "dinyalain" ditukar.
     */
    private void openProtectiveDetailWindow() {
        nightProtectiveDetailRevealAt = Integer.MAX_VALUE;
        if (nightProtectiveDetailUuid == null) return;
        if (protectiveDetailConfirmed) return; // udah di-auto-skip duluan sebelum sempet direveal

        ServerPlayer pdPlayer = serverRef.getPlayerList().getPlayer(nightProtectiveDetailUuid);
        if (pdPlayer != null) {
            removeBlindfold(pdPlayer);
            nightBlindfolded.remove(nightProtectiveDetailUuid);
        }
        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal("Protective Detail buka mata").withStyle(ChatFormatting.GREEN),
                Component.empty(), 10, 40, 10);

        if (nightWitnessUuid != null) {
            ServerPlayer witnessPlayer = serverRef.getPlayerList().getPlayer(nightWitnessUuid);
            if (witnessPlayer != null) applyGlow(witnessPlayer);
        }
        com.deception.network.ModNetworking.broadcastNightActionBar(
                Component.literal("Menunggu protective detail melihat").withStyle(ChatFormatting.GREEN));
        protectiveDetailWindowOpen = true;
        protectiveDetailConfirmReadyAt = nightTicksElapsed + NIGHT_WITNESS_HOLD;

        checkProtectiveDetailWakeUpOffline();
    }

    /** Protective Detail tutup mata lagi, lalu fase malam masuk ke "semua buka mata". */
    private void finalizeProtectiveDetailConfirm() {
        if (protectiveDetailConfirmed) return;
        protectiveDetailConfirmed = true;
        protectiveDetailWindowOpen = false;
        protectiveDetailAutoSkipAt = Integer.MAX_VALUE;
        protectiveDetailConfirmReadyAt = Integer.MAX_VALUE;

        if (leftProtectiveDetailUuid != null) {
            hasProtectiveDetailLeft = false;
            leftProtectiveDetailUuid = null;
            leftProtectiveDetailName = "";
        }

        UUID fsUuid = findRoleUuid(Role.forensic_scientist);
        if (fsUuid != null) {
            ServerPlayer fsPlayer = serverRef.getPlayerList().getPlayer(fsUuid);
            if (fsPlayer != null) {
                fsPlayer.sendSystemMessage(buildProtectiveDetailFsLine());
            }
        }

        if (nightWitnessUuid != null) {
            ServerPlayer witnessPlayer = serverRef.getPlayerList().getPlayer(nightWitnessUuid);
            if (witnessPlayer != null) removeGlow(witnessPlayer);
        }
        com.deception.network.ModNetworking.broadcastNightActionBar(Component.empty());

        ServerPlayer pdPlayer = serverRef.getPlayerList().getPlayer(nightProtectiveDetailUuid);
        if (pdPlayer != null) {
            putBlindfold(pdPlayer);
            nightBlindfolded.add(nightProtectiveDetailUuid);
        }

        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal("Protective Detail tutup mata").withStyle(ChatFormatting.RED),
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
        // Cuma dilampirin kalo giliran Protective Detail emang udah lewat --
        // di jalur normal method ini dipanggil pas witness baru selesai, dan
        // di titik itu PD belum dapet giliran, jadi belum boleh dibocorin.
        if (protectiveDetailConfirmed && nightProtectiveDetailUuid != null) {
            lines.add(buildProtectiveDetailFsLine());
        }
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

    /** Yang diliat Protective Detail: cuma nama Witness, gak lebih. */
    private Component buildProtectiveDetailResultLine() {
        String witnessName = nightWitnessUuid != null ? getPlayerName(nightWitnessUuid) : "-";
        return Component.literal("  Witness yang harus kamu lindungi: " + witnessName).withStyle(ChatFormatting.AQUA);
    }

    /** FS tau semuanya, termasuk siapa Protective Detail-nya. */
    private Component buildProtectiveDetailFsLine() {
        String pdName = nightProtectiveDetailUuid != null ? getPlayerName(nightProtectiveDetailUuid) : "-";
        return Component.literal("  Protective Detail: " + pdName).withStyle(ChatFormatting.GREEN);
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
     * Titik kumpul di dalam arena -- BEDA dari {@link #ARENA_TELEPORT_POS}
     * (yang itu titik mulai main, di tengah lingkaran cluster). Ini tempat
     * mendarat /deception gotoarena sekaligus tempat semua peserta ditaruh
     * pas game selesai.
     */
    private static final double ARENA_LOBBY_X = 31.5;
    private static final double ARENA_LOBBY_Y = -60;
    private static final double ARENA_LOBBY_Z = 176.5;

    /**
     * Pindahin satu player ke titik kumpul arena.
     *
     * @return false kalo dimensi arenanya gak kedaftar -- caller yang mutusin
     *         mau ngomong apa (command kasih pesan error, abortGame diem aja
     *         biar sisa proses cleanup-nya tetep jalan).
     */
    public static boolean teleportToArenaLobby(MinecraftServer server, ServerPlayer player) {
        ServerLevel arena = server.getLevel(ArenaDimension.ARENA);
        if (arena == null) return false;
        player.teleportTo(arena, ARENA_LOBBY_X, ARENA_LOBBY_Y, ARENA_LOBBY_Z,
                player.getYRot(), player.getXRot());
        return true;
    }

    private void teleportPlayersAndDecorateArena(MinecraftServer server) {
        ServerLevel level = ArenaDimension.level(server);

        List<UUID> allPlayers = new ArrayList<>(registeredPlayers);
        for (UUID uuid : allPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
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

        String name = getPlayerName(uuid);

        if (state == State.ALLIES) {
            onAlliesPlayerLeft(uuid, role, name);
            return;
        }
        if (state != State.NIGHT) return;

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

        if (role == Role.protective_detail) {
            if (!protectiveDetailConfirmed) {
                // Sama kayak witness -- cuma react kalo jendela liatnya UDAH
                // kebuka. Leave sebelum gilirannya ketangkep sama
                // checkProtectiveDetailWakeUpOffline() pas "PD buka mata".
                if (protectiveDetailWindowOpen && protectiveDetailAutoSkipAt == Integer.MAX_VALUE) {
                    protectiveDetailAutoSkipAt = nightTicksElapsed + LEAVE_TIMEOUT_TICKS;
                    protectiveDetailAutoSkipTicksLeft = LEAVE_TIMEOUT_TICKS;
                    leftProtectiveDetailUuid = uuid;
                    leftProtectiveDetailName = name;
                    hasProtectiveDetailLeft = true;

                    notifyRole(Role.forensic_scientist, protectiveDetailOfflineFsMessage(name, "keluar server"));
                    updateProtectiveDetailSkipCountdown();
                }
            } else {
                notifyRole(Role.forensic_scientist, Component.literal(name + " (Protective Detail) keluar server.")
                        .withStyle(ChatFormatting.GREEN));
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
                // Kalo ini kejadian pas giliran Protective Detail lagi jalan,
                // dia yang paling kena: yang harusnya nyala malah ilang.
                // Sengaja gak nyebut role-nya "Witness" ke PD -- dia emang
                // udah tau siapa yang dia liat, tapi formatnya disamain sama
                // pesan lain biar gak keliatan beda perlakuan.
                if (protectiveDetailWindowOpen && !protectiveDetailConfirmed) {
                    notifyRole(Role.protective_detail, Component.literal(name + " orang yang harus kamu lindungi telah keluar server")
                            .withStyle(ChatFormatting.AQUA));
                }
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

        if (role == Role.protective_detail) {
            protectiveDetailAutoSkipAt = Integer.MAX_VALUE;
            protectiveDetailAutoSkipTicksLeft = 0;
            if (leftProtectiveDetailUuid != null && leftProtectiveDetailUuid.equals(uuid)) {
                hasProtectiveDetailLeft = false;
                leftProtectiveDetailUuid = null;
                leftProtectiveDetailName = "";
            }
            resetCountdownActionBars();
        }

        if (role == Role.lab_technician) {
            labTechAutoSkipAt = Integer.MAX_VALUE;
            labTechAutoSkipTicksLeft = 0;
            if (leftLabTechUuid != null && leftLabTechUuid.equals(uuid)) {
                hasLabTechLeft = false;
                leftLabTechUuid = null;
                leftLabTechName = "";
            }
            resetCountdownActionBars();
        }

        if (role == Role.inside_man) {
            insideManAutoSkipAt = Integer.MAX_VALUE;
            insideManAutoSkipTicksLeft = 0;
            if (leftInsideManUuid != null && leftInsideManUuid.equals(uuid)) {
                hasInsideManLeft = false;
                leftInsideManUuid = null;
                leftInsideManName = "";
            }
            resetCountdownActionBars();
        }

        // KIRIM ULANG SKIP BUTTON ke Forensic Scientist jika masih ada player yang left
        if (role == Role.forensic_scientist) {
            resendSkipButtonToForensicScientist(player);

            // Kunci paper fase Allies itu nempel di NBT stack, dan yang lagi
            // offline pas fase-nya mulai/selesai kelewat dua-duanya. Disamain
            // ulang di sini ke kondisi yang berlaku sekarang -- tanpa ini FS
            // yang DC di tengah fase bisa balik bawa paper kekunci selamanya.
            PresentationManager.get().syncAlliesPaperLock(player, state == State.ALLIES);

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
        } else if (role == Role.protective_detail && protectiveDetailConfirmed) {
            player.sendSystemMessage(buildProtectiveDetailResultLine());
        } else if (role == Role.lab_technician && labTechConfirmed && labTechSelectedPos != null) {
            player.sendSystemMessage(buildLabTechResultLine());
        } else if (role == Role.inside_man && insideManConfirmed && insideManTargetUuid != null) {
            player.sendSystemMessage(buildInsideManResultLine());
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

    /**
     * Gambar ulang actionbar countdown ke Forensic Scientist & Accomplice.
     *
     * <p>Yang ditampilin ke FS = countdown auto-pick/auto-skip yang lagi
     * jalan (murderer / witness / protective detail / lab technician /
     * inside man). Kalo gak ada yang lagi dihitung mundur, dia balik ke teks
     * "menunggu ..." fase yang lagi berlangsung. Accomplice cuma dikasih
     * countdown murderer -- fase lain bukan urusan dia.
     */
    private void resetCountdownActionBars() {
        if (serverRef == null) return;

        Component fsBar = pendingCountdownBar();
        if (fsBar == null) fsBar = currentWaitingBar();
        sendActionBarToRole(Role.forensic_scientist, fsBar);

        Component accompliceBar = murdererCountdownBar();
        if (accompliceBar == null) {
            accompliceBar = murdererWindowOpen && !murdererConfirmed
                    ? Component.literal("Menunggu murderer memilih item").withStyle(ChatFormatting.RED)
                    : Component.empty();
        }
        sendActionBarToRole(Role.accomplice, accompliceBar);
    }

    /** Countdown auto-pick murderer yang lagi jalan, atau null kalo gak ada. */
    private Component murdererCountdownBar() {
        if (!hasMurdererLeft || leftMurdererUuid == null || murdererConfirmed) return null;
        int secondsLeft = (int) Math.ceil(murdererAutoPickTicksLeft / 20.0);
        if (secondsLeft <= 0) return null;
        return Component.literal("⏱ Auto-pick Murderer dalam ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW));
    }

    /** Countdown APAPUN yang lagi jalan (buat FS), atau null kalo lagi gak ada. */
    private Component pendingCountdownBar() {
        Component murdererBar = murdererCountdownBar();
        if (murdererBar != null) return murdererBar;

        if (hasWitnessLeft && leftWitnessUuid != null && !witnessConfirmed) {
            int secondsLeft = (int) Math.ceil(witnessAutoSkipTicksLeft / 20.0);
            if (secondsLeft > 0) return witnessSkipCountdownBar(secondsLeft);
        }
        if (hasProtectiveDetailLeft && leftProtectiveDetailUuid != null && !protectiveDetailConfirmed) {
            int secondsLeft = (int) Math.ceil(protectiveDetailAutoSkipTicksLeft / 20.0);
            if (secondsLeft > 0) return protectiveDetailSkipCountdownBar(secondsLeft);
        }
        if (hasLabTechLeft && leftLabTechUuid != null && !labTechConfirmed) {
            int secondsLeft = (int) Math.ceil(labTechAutoSkipTicksLeft / 20.0);
            if (secondsLeft > 0) return labTechSkipCountdownBar(secondsLeft);
        }
        if (hasInsideManLeft && leftInsideManUuid != null && !insideManConfirmed) {
            int secondsLeft = (int) Math.ceil(insideManAutoSkipTicksLeft / 20.0);
            if (secondsLeft > 0) return insideManSkipCountdownBar(secondsLeft);
        }
        return null;
    }

    /** Teks "menunggu ..." sesuai fase yang lagi berlangsung; kosong kalo lagi gak ada giliran. */
    private Component currentWaitingBar() {
        if (state == State.NIGHT) {
            if (murdererWindowOpen && !murdererConfirmed) {
                return Component.literal("Menunggu murderer memilih item").withStyle(ChatFormatting.RED);
            }
            if (witnessWindowOpen && !witnessConfirmed && nightWitnessUuid != null) {
                return Component.literal("Menunggu witness melihat").withStyle(ChatFormatting.AQUA);
            }
            if (protectiveDetailWindowOpen && !protectiveDetailConfirmed && nightProtectiveDetailUuid != null) {
                return Component.literal("Menunggu protective detail melihat").withStyle(ChatFormatting.GREEN);
            }
        } else if (state == State.ALLIES) {
            if (labTechAwaitingAnswer) {
                return Component.literal("Jawab pertanyaan lab technician").withStyle(ChatFormatting.AQUA);
            }
            if (labTechWindowOpen && !labTechConfirmed) {
                return Component.literal("Menunggu lab technician memilih item").withStyle(ChatFormatting.AQUA);
            }
            if (insideManWindowOpen && !insideManConfirmed) {
                return Component.literal("Menunggu inside man memilih target").withStyle(ChatFormatting.DARK_RED);
            }
        }
        return Component.empty();
    }

    // Method baru untuk sinkronisasi blindfold
    // Hitung ulang dari NOL apakah player ini seharusnya lagi tutup mata
    // detik ini juga, murni dari state night sekarang (bukan dari set
    // nightBlindfolded/nightPendingBlindfold yang bisa "kelewat" update
    // kalo playernya offline pas event penting kejadian).
    private boolean computeShouldBeBlindfolded(UUID uuid, Role role) {
        if (role == Role.forensic_scientist) return false;

        if (state == State.ALLIES) {
            if (alliesTicksElapsed < NIGHT_PRECLOSE_DELAY) return false;
            if (alliesWakeAllAt != Integer.MAX_VALUE && alliesTicksElapsed >= alliesWakeAllAt) return false;

            if (role == Role.lab_technician && uuid.equals(alliesLabTechUuid)) {
                return !(labTechWindowOpen && !labTechConfirmed);
            }
            if (role == Role.inside_man && uuid.equals(alliesInsideManUuid)) {
                return !(insideManWindowOpen && !insideManConfirmed);
            }
            return true;
        }

        if (state != State.NIGHT) return false;
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

        boolean isRevealedProtectiveDetail = role == Role.protective_detail && uuid.equals(nightProtectiveDetailUuid);
        if (isRevealedProtectiveDetail) {
            // lagi jendela liat witness nyala -> mata kebuka, selain itu tutup
            return !(protectiveDetailWindowOpen && !protectiveDetailConfirmed);
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
                        player.connection.send(new ClientboundSetActionBarTextPacket(witnessSkipCountdownBar(secondsLeft)));
                        return;
                    }
                }
            }

            if (protectiveDetailAutoSkipAt != Integer.MAX_VALUE && leftProtectiveDetailUuid != null && !protectiveDetailConfirmed) {
                int secondsLeft = (int) Math.ceil(protectiveDetailAutoSkipTicksLeft / 20.0);
                if (secondsLeft > 0) {
                    Role role = roleAssignments.get(player.getUUID());
                    if (role == Role.forensic_scientist) {
                        player.connection.send(new ClientboundSetActionBarTextPacket(protectiveDetailSkipCountdownBar(secondsLeft)));
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
            } else if (protectiveDetailWindowOpen && !protectiveDetailConfirmed && nightProtectiveDetailUuid != null) {
                if (player.getUUID().equals(nightProtectiveDetailUuid)) {
                    Component actionBar = Component.literal("Kamu adalah Protective Detail - Lihat siapa yang bersinar!").withStyle(ChatFormatting.GREEN);
                    player.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
                } else {
                    Component actionBar = Component.literal("Menunggu protective detail melihat").withStyle(ChatFormatting.GREEN);
                    com.deception.network.ModNetworking.sendNightActionBarTo(player, actionBar);
                }
            }
            // Gak ada fase murderer/witness/PD yang lagi berlangsung -- overlay
            // custom-nya udah di-clear di awal method, gak perlu ngapa-ngapain lagi.
        } else if (state == State.ALLIES) {
            syncAlliesActionBar(player);
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
            } else if (protectiveDetailWindowOpen && !protectiveDetailConfirmed && nightProtectiveDetailUuid != null) {
                // Fase protective detail buka mata
                Component title = Component.literal("Protective Detail buka mata").withStyle(ChatFormatting.GREEN);
                sendTitle(player, title, Component.empty(), 10, 40, 10);
            } else if (nightWakeAllAt != Integer.MAX_VALUE && nightTicksElapsed >= nightWakeAllAt) {
                // Fase semua buka mata
                Component title = Component.literal("Semua orang buka mata").withStyle(ChatFormatting.GOLD);
                sendTitle(player, title, Component.empty(), 10, 20, 10);
            }
        } else if (state == State.ALLIES) {
            // Urutannya dari yang PALING spesifik: field *WakeAt yang udah
            // kelewat nilainya balik ke Integer.MAX_VALUE, jadi perbandingan
            // "belum waktunya" gak bisa dipake buat mbedain "belum mulai"
            // dari "udah lewat" -- yang bisa dipercaya cuma flag window-nya.
            if (labTechWindowOpen && !labTechConfirmed) {
                sendTitle(player, Component.literal("Lab Technician buka mata").withStyle(ChatFormatting.AQUA),
                        Component.empty(), 10, 40, 10);
            } else if (insideManWindowOpen && !insideManConfirmed) {
                sendTitle(player, Component.literal("Inside Man buka mata").withStyle(ChatFormatting.DARK_RED),
                        Component.empty(), 10, 40, 10);
            } else if (alliesWakeAllAt != Integer.MAX_VALUE && alliesTicksElapsed >= alliesWakeAllAt) {
                sendTitle(player, Component.literal("Semua orang buka mata").withStyle(ChatFormatting.GOLD),
                        Component.empty(), 10, 20, 10);
            } else {
                sendTitle(player, Component.literal("Semua orang tutup mata").withStyle(ChatFormatting.RED),
                        Component.empty(), 10, 40, 10);
            }
        }
    }

    // Method baru untuk sinkronisasi glow effect
    private void syncGlowEffect(ServerPlayer player, UUID uuid, Role role) {
        // Siapa yang SEHARUSNYA nyala detik ini, dihitung dari fase yang lagi
        // jalan: giliran witness -> murderer & accomplice, giliran protective
        // detail -> witness, giliran inside man -> target yang lagi dia tunjuk.
        boolean shouldGlow;
        if (state == State.NIGHT && witnessWindowOpen && !witnessConfirmed) {
            shouldGlow = role == Role.murderer || role == Role.accomplice;
        } else if (state == State.NIGHT && protectiveDetailWindowOpen && !protectiveDetailConfirmed) {
            shouldGlow = uuid.equals(nightWitnessUuid);
        } else if (state == State.ALLIES && insideManWindowOpen && !insideManConfirmed) {
            shouldGlow = uuid.equals(insideManTargetUuid);
        } else {
            shouldGlow = false;
        }

        boolean hasGlow = player.hasEffect(MobEffects.GLOWING);
        if (shouldGlow && !hasGlow) {
            applyGlow(player);
        } else if (!shouldGlow && hasGlow) {
            removeGlow(player);
        }
    }

    // Method baru untuk mengirim ulang pesan konfirmasi
    private void resendConfirmMessage(ServerPlayer player, Role role) {
        if (state == State.ALLIES) {
            resendAlliesConfirmMessage(player, role);
            return;
        }
        if (state != State.NIGHT) return;

        // Untuk Murderer: jika masih dalam fase memilih dan belum konfirmasi.
        // Panel HUD itu state CLIENT, ilang pas dia disconnect -- jadi dibangun
        // ulang di sini, dan flag prompt-nya direset biar tombol [KONFIRMASI]
        // yang ikut kehapus dari chat-nya boleh dikirim sekali lagi.
        if (role == Role.murderer && !murdererConfirmed && murdererWindowOpen) {
            refreshMurdererHud(null);
            murdererConfirmPromptSent = false;
            maybeSendMurdererConfirmPrompt(player);
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

        // Untuk Protective Detail: pola sama persis kayak Witness di atas.
        if (role == Role.protective_detail && !protectiveDetailConfirmed && protectiveDetailWindowOpen
                && nightProtectiveDetailUuid != null && nightProtectiveDetailUuid.equals(player.getUUID())) {
            if (protectiveDetailConfirmReadyAt == Integer.MAX_VALUE || nightTicksElapsed >= protectiveDetailConfirmReadyAt) {
                player.sendSystemMessage(Component.literal("Selesai melihat? ").withStyle(ChatFormatting.GREEN)
                        .append(confirmButton()));
            } else {
                int ticksLeft = protectiveDetailConfirmReadyAt - nightTicksElapsed;
                int secondsLeft = (int) Math.ceil(ticksLeft / 20.0);
                player.sendSystemMessage(Component.literal("Tunggu " + secondsLeft + " detik lagi sebelum konfirmasi.")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    // Method untuk mengirim ulang skip button ke Forensic Scientist
    private void resendSkipButtonToForensicScientist(ServerPlayer player) {
        boolean anyWindowOpen = state == State.NIGHT
                ? (murdererWindowOpen || witnessWindowOpen || protectiveDetailWindowOpen)
                : state == State.ALLIES && (labTechWindowOpen || insideManWindowOpen);
        if (!anyWindowOpen) return;

        List<Component> messages = new ArrayList<>();

        if (hasMurdererLeft && leftMurdererUuid != null && !murdererConfirmed) {
            if (serverRef.getPlayerList().getPlayer(leftMurdererUuid) == null) {
                int secondsLeft = (int) Math.ceil(murdererAutoPickTicksLeft / 20.0);
                messages.add(Component.literal(leftMurdererName + " (Murderer) masih offline. Auto-pick dalam ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(".").withStyle(ChatFormatting.RED))
                        .append(skipRevealPrompt()));
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

        if (hasWitnessLeft && leftWitnessUuid != null && !witnessConfirmed) {
            if (serverRef.getPlayerList().getPlayer(leftWitnessUuid) == null) {
                messages.add(stillOfflineMessage(leftWitnessName, "Witness", ChatFormatting.AQUA, witnessAutoSkipTicksLeft));
            } else {
                hasWitnessLeft = false;
                leftWitnessUuid = null;
                leftWitnessName = "";
                witnessAutoSkipAt = Integer.MAX_VALUE;
                witnessAutoSkipTicksLeft = 0;
                resetCountdownActionBars();
            }
        }

        if (hasProtectiveDetailLeft && leftProtectiveDetailUuid != null && !protectiveDetailConfirmed) {
            if (serverRef.getPlayerList().getPlayer(leftProtectiveDetailUuid) == null) {
                messages.add(stillOfflineMessage(leftProtectiveDetailName, "Protective Detail", ChatFormatting.GREEN,
                        protectiveDetailAutoSkipTicksLeft));
            } else {
                hasProtectiveDetailLeft = false;
                leftProtectiveDetailUuid = null;
                leftProtectiveDetailName = "";
                protectiveDetailAutoSkipAt = Integer.MAX_VALUE;
                protectiveDetailAutoSkipTicksLeft = 0;
                resetCountdownActionBars();
            }
        }

        if (hasLabTechLeft && leftLabTechUuid != null && !labTechConfirmed) {
            if (serverRef.getPlayerList().getPlayer(leftLabTechUuid) == null) {
                messages.add(stillOfflineMessage(leftLabTechName, "Lab Technician", ChatFormatting.AQUA, labTechAutoSkipTicksLeft));
            } else {
                hasLabTechLeft = false;
                leftLabTechUuid = null;
                leftLabTechName = "";
                labTechAutoSkipAt = Integer.MAX_VALUE;
                labTechAutoSkipTicksLeft = 0;
                resetCountdownActionBars();
            }
        }

        if (hasInsideManLeft && leftInsideManUuid != null && !insideManConfirmed) {
            if (serverRef.getPlayerList().getPlayer(leftInsideManUuid) == null) {
                messages.add(stillOfflineMessage(leftInsideManName, "Inside Man", ChatFormatting.DARK_RED, insideManAutoSkipTicksLeft));
            } else {
                hasInsideManLeft = false;
                leftInsideManUuid = null;
                leftInsideManName = "";
                insideManAutoSkipAt = Integer.MAX_VALUE;
                insideManAutoSkipTicksLeft = 0;
                resetCountdownActionBars();
            }
        }

        // Kirim semua pesan ke Forensic Scientist
        for (Component msg : messages) {
            player.sendSystemMessage(msg);
        }
    }

    private Component stillOfflineMessage(String name, String roleLabel, ChatFormatting color, int ticksLeft) {
        int secondsLeft = (int) Math.ceil(ticksLeft / 20.0);
        return Component.literal(name + " (" + roleLabel + ") masih offline. Auto-skip dalam ")
                .withStyle(color)
                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(".").withStyle(color))
                .append(skipRevealPrompt());
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
     * Dipanggil pas "protective detail buka mata" (openProtectiveDetailWindow).
     * Sama persis polanya kayak checkWitnessWakeUpOffline: kalo PD-nya udah
     * offline dari sebelum gilirannya, timer auto-skip langsung jalan. Kalo
     * WITNESS-nya yang lagi offline, PD dikasih tau (baru sekarang boleh,
     * karena emang giliran dia liat) plus FS dikasih info yang sama.
     */
    private void checkProtectiveDetailWakeUpOffline() {
        if (protectiveDetailConfirmed || nightProtectiveDetailUuid == null) return;

        boolean pdOffline = serverRef.getPlayerList().getPlayer(nightProtectiveDetailUuid) == null;

        if (pdOffline && protectiveDetailAutoSkipAt == Integer.MAX_VALUE) {
            String pdName = getPlayerName(nightProtectiveDetailUuid);
            leftProtectiveDetailUuid = nightProtectiveDetailUuid;
            leftProtectiveDetailName = pdName;
            hasProtectiveDetailLeft = true;

            protectiveDetailAutoSkipAt = nightTicksElapsed + LEAVE_TIMEOUT_TICKS;
            protectiveDetailAutoSkipTicksLeft = LEAVE_TIMEOUT_TICKS;

            notifyRole(Role.forensic_scientist, protectiveDetailOfflineFsMessage(pdName, "sudah offline"));
            updateProtectiveDetailSkipCountdown();
        }

        if (nightWitnessUuid != null && serverRef.getPlayerList().getPlayer(nightWitnessUuid) == null) {
            String witnessName = getPlayerName(nightWitnessUuid);
            notifyRole(Role.forensic_scientist, Component.literal(witnessName + " (Witness) sudah offline.")
                    .withStyle(ChatFormatting.AQUA));
            notifyRole(Role.protective_detail, Component.literal(witnessName + " orang yang harus kamu lindungi sedang offline")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private Component protectiveDetailOfflineFsMessage(String pdName, String reason) {
        return Component.literal(pdName + " (Protective Detail) " + reason + ". Auto-skip dalam ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(getOfflineRevealSeconds() + " detik").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(".").withStyle(ChatFormatting.GREEN))
                .append(skipRevealPrompt());
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
        if (state == State.ALLIES) {
            return skipAlliesPhase();
        }
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

        // 3b) Witness udah tutup mata, masih nunggu jeda sebelum protective
        //     detail dibangunin -> buka sekarang juga.
        if (!protectiveDetailConfirmed && !protectiveDetailWindowOpen
                && nightProtectiveDetailRevealAt != Integer.MAX_VALUE) {
            openProtectiveDetailWindow();
            return true;
        }

        // 3c) Fase protective detail lagi berlangsung.
        if (protectiveDetailWindowOpen && !protectiveDetailConfirmed) {
            protectiveDetailAutoSkipAt = Integer.MAX_VALUE;
            finalizeProtectiveDetailConfirm();
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

    // ============================================================
    //  Fase Allies -- Lab Technician & Inside Man
    // ============================================================
    // Nyempil di antara presentasi ronde 1 dan diskusi ronde 2, persis kayak
    // "Allies Phase" di rulebook Undercover Allies. Mesinnya sengaja dibikin
    // kembaran fase malam (blindfold, title, actionbar, timer offline,
    // sinkron pas rejoin) biar kelakuannya sama persis dan gak ada aturan
    // baru yang perlu diapal pemain.

    /** Jawaban FS buat pertanyaan LT. null = belum dijawab. */
    private Boolean labTechAnswer = null;

    /** Fase Allies cuma dijalanin kalo minimal salah satu role-nya ada. */
    private boolean hasAlliesPhaseRoles() {
        return findRoleUuid(Role.lab_technician) != null || findRoleUuid(Role.inside_man) != null;
    }

    private void startAlliesSequence(MinecraftServer server) {
        resetAlliesState();
        this.state = State.ALLIES;
        this.labTechAnswer = null;

        UUID fsUuid = findRoleUuid(Role.forensic_scientist);
        alliesLabTechUuid = findRoleUuid(Role.lab_technician);
        alliesInsideManUuid = findRoleUuid(Role.inside_man);

        nightBlindfolded.clear();
        nightPendingBlindfold.clear();
        for (UUID uuid : registeredPlayers) {
            if (uuid.equals(fsUuid)) continue;
            nightPendingBlindfold.add(uuid);
        }

        // FS gak ikut tutup mata, jadi tanpa ini dia bisa nempel paper selagi
        // yang lain merem -- lihat PresentationManager#ALLIES_LOCKED_TAG.
        ServerPlayer fsPlayer = PresentationManager.get().findOnlineForensicScientist(server);
        if (fsPlayer != null) {
            PresentationManager.get().syncAlliesPaperLock(fsPlayer, true);
        }

        int firstWakeAt = NIGHT_PRECLOSE_DELAY + NIGHT_CLOSE_HOLD;
        if (alliesLabTechUuid != null) {
            alliesLabTechWakeAt = firstWakeAt;
        } else if (alliesInsideManUuid != null) {
            alliesInsideManWakeAt = firstWakeAt;
        }

        Component title = Component.literal("Semua orang tutup mata").withStyle(ChatFormatting.RED);
        int stay = NIGHT_PRECLOSE_DELAY + NIGHT_CLOSE_HOLD - 20;
        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) sendTitle(player, title, Component.empty(), 10, stay, 10);
        }
    }

    private void tickAlliesSequence() {
        alliesTicksElapsed++;

        for (UUID uuid : nightBlindfolded) {
            ServerPlayer bp = serverRef.getPlayerList().getPlayer(uuid);
            if (bp != null && bp.getItemBySlot(EquipmentSlot.HEAD).getItem() != Items.NOTE_BLOCK) {
                bp.setItemSlot(EquipmentSlot.HEAD, createBlindfoldItem());
            }
        }

        if (alliesTicksElapsed == NIGHT_PRECLOSE_DELAY) {
            for (UUID uuid : nightPendingBlindfold) {
                // /deception skipreveal bisa majuin giliran LT/IM SEBELUM
                // detik ini -- kalo gak dikecualiin, orang yang barusan
                // dibangunin langsung ketutup lagi di sini.
                if (labTechWindowOpen && uuid.equals(alliesLabTechUuid)) continue;
                if (insideManWindowOpen && uuid.equals(alliesInsideManUuid)) continue;

                ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
                if (p != null) {
                    putBlindfold(p);
                    nightBlindfolded.add(uuid);
                }
            }
        }

        if (alliesTicksElapsed == alliesLabTechWakeAt) {
            openLabTechWindow();
        }

        if (alliesTicksElapsed == labTechAutoSkipAt) {
            labTechAutoSkipAt = Integer.MAX_VALUE;
            autoFinishLabTech();
        }

        if (alliesTicksElapsed == alliesInsideManWakeAt) {
            openInsideManWindow();
        }

        if (alliesTicksElapsed == insideManAutoSkipAt) {
            insideManAutoSkipAt = Integer.MAX_VALUE;
            autoFinishInsideMan();
        }

        if (labTechAutoSkipAt != Integer.MAX_VALUE && labTechWindowOpen && !labTechConfirmed) {
            labTechAutoSkipTicksLeft = labTechAutoSkipAt - alliesTicksElapsed;
            if (labTechAutoSkipTicksLeft > 0 && labTechAutoSkipTicksLeft % 20 == 0) {
                sendActionBarToRole(Role.forensic_scientist,
                        labTechSkipCountdownBar((int) Math.ceil(labTechAutoSkipTicksLeft / 20.0)));
            }
        }

        if (insideManAutoSkipAt != Integer.MAX_VALUE && insideManWindowOpen && !insideManConfirmed) {
            insideManAutoSkipTicksLeft = insideManAutoSkipAt - alliesTicksElapsed;
            if (insideManAutoSkipTicksLeft > 0 && insideManAutoSkipTicksLeft % 20 == 0) {
                sendActionBarToRole(Role.forensic_scientist,
                        insideManSkipCountdownBar((int) Math.ceil(insideManAutoSkipTicksLeft / 20.0)));
            }
        }

        if (alliesTicksElapsed == alliesWakeAllAt) {
            wakeEveryoneFromAllies();
        }

        if (alliesTicksElapsed >= alliesDoneAt && alliesDoneAt != Integer.MAX_VALUE) {
            state = State.DISCUSS;
            broadcastForensicWaitActionBar();
        }
    }

    private void wakeEveryoneFromAllies() {
        for (UUID uuid : new ArrayList<>(nightBlindfolded)) {
            ServerPlayer p = serverRef.getPlayerList().getPlayer(uuid);
            if (p != null) removeBlindfold(p);
        }
        nightBlindfolded.clear();
        com.deception.network.ModNetworking.broadcastNightActionBar(Component.empty());
        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal("Semua orang buka mata").withStyle(ChatFormatting.GOLD),
                Component.empty(), 10, NIGHT_WAKE_HOLD, 10);

        // Buka kunci paper sisa ronde 1 DULUAN, baru kasih jatah ronde 2.
        // Urutannya penting: paper ronde 2 dateng bawa PLACEMENT_LOCKED_TAG
        // sendiri (nunggu FS hapus tile lama), dan itu kunci yang beda --
        // gak boleh ikut kelepas di sini.
        ServerPlayer fsPlayer = PresentationManager.get().findOnlineForensicScientist(serverRef);
        if (fsPlayer != null) {
            PresentationManager.get().syncAlliesPaperLock(fsPlayer, false);
        }

        // Scene tile ronde 2 + penghapusnya baru dikasih SEKARANG, bukan pas
        // ronde 1 kelar (lihat PresentationManager#advanceRound) -- biar FS
        // gak nerima item selagi semua orang masih tutup mata.
        PresentationManager.get().giveDeferredRoundPaper(serverRef);

        // Badge-nya baru beneran dicabut SEKARANG, bukan pas Inside Man
        // nunjuk -- ngikutin rulebook: FS baru ngambil token-nya setelah
        // semua orang buka mata, jadi semua orang liat siapa yang kena.
        if (insideManTargetUuid != null) {
            String targetName = getPlayerName(insideManTargetUuid);
            if (PresentationManager.get().revokeBadge(serverRef, insideManTargetUuid)) {
                broadcast(serverRef, Component.literal("Police badge milik " + targetName + " hilang secara misterius.")
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    // ---------- Lab Technician ----------

    private void openLabTechWindow() {
        alliesLabTechWakeAt = Integer.MAX_VALUE;
        if (alliesLabTechUuid == null || labTechConfirmed) {
            scheduleInsideManOrWake();
            return;
        }

        ServerPlayer lt = serverRef.getPlayerList().getPlayer(alliesLabTechUuid);
        if (lt != null) {
            removeBlindfold(lt);
            nightBlindfolded.remove(alliesLabTechUuid);
            lt.sendSystemMessage(Component.literal("Klik kanan 1 item di TKP buat ditanyain ke Forensic Scientist.")
                    .withStyle(ChatFormatting.AQUA));
        }
        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal("Lab Technician buka mata").withStyle(ChatFormatting.AQUA),
                Component.empty(), 10, 40, 10);
        com.deception.network.ModNetworking.broadcastNightActionBar(
                Component.literal("Menunggu lab technician memilih item").withStyle(ChatFormatting.AQUA));
        labTechWindowOpen = true;
        refreshLabTechHud(null);

        checkLabTechWakeUpOffline();
    }

    /**
     * Dipanggil dari event klik kanan block. Beda dari murderer, Lab
     * Technician boleh nunjuk item PUNYA SIAPA AJA -- rulebook-nya bilang
     * "any 1 card in play".
     */
    public boolean onLabTechClickClue(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (state != State.ALLIES || !labTechWindowOpen || labTechConfirmed || labTechAwaitingAnswer) return false;
        if (!player.getUUID().equals(alliesLabTechUuid)) return false;

        long currentTick = level.getGameTime();
        if (currentTick == lastClickTick && processedClicks.contains(pos)) {
            return false;
        }
        if (currentTick != lastClickTick) {
            processedClicks.clear();
            lastClickTick = currentTick;
        }
        processedClicks.add(pos);

        BlockState clickedState = level.getBlockState(pos);
        if (!(clickedState.getBlock() instanceof ClueBlock)) return false;
        if (clickedState.getValue(ClueBlock.FACE) != AttachFace.WALL) return false;

        Direction facing = clickedState.getValue(ClueBlock.FACING);
        BlockPos backingPos = pos.relative(facing.getOpposite());

        if (labTechSelectedBacking != null && !labTechSelectedBacking.equals(backingPos)) {
            restoreBacking(level, labTechSelectedBacking);
        }
        level.setBlock(backingPos, Blocks.GREEN_CONCRETE.defaultBlockState(), 3);
        labTechSelectedBacking = backingPos;
        labTechSelectedPos = pos;

        refreshLabTechHud(null);
        maybeSendLabTechConfirmPrompt(player);
        return true;
    }

    /** LT klik [KONFIRMASI] -- pertanyaannya dilempar ke Forensic Scientist. */
    private void askForensicScientistAboutItem() {
        ServerLevel level = ArenaDimension.level(serverRef);
        if (labTechSelectedBacking != null) restoreBacking(level, labTechSelectedBacking);

        labTechAwaitingAnswer = true;
        labTechAutoSkipAt = Integer.MAX_VALUE;
        String itemName = getItemName(level, labTechSelectedPos);

        UUID fsUuid = findRoleUuid(Role.forensic_scientist);
        ServerPlayer fsPlayer = fsUuid != null ? serverRef.getPlayerList().getPlayer(fsUuid) : null;
        if (fsPlayer == null) {
            // FS-nya lagi gak ada -- fase gak boleh macet nunggu jawaban yang
            // gak bakal dateng. Dijawab otomatis pakai kebenaran aslinya
            // (dicocokin ke means/clue pilihan murderer).
            answerLabTechnician(isPartOfSolution(labTechSelectedPos));
            return;
        }

        fsPlayer.sendSystemMessage(Component.literal(""));
        fsPlayer.sendSystemMessage(Component.literal("Lab Technician menanyakan: " + itemName)
                .withStyle(ChatFormatting.AQUA));
        fsPlayer.sendSystemMessage(Component.literal("Item ini bagian dari solusi? ")
                .append(labTechAnswerButton("[BENAR]", ChatFormatting.GREEN, "/deception true"))
                .append(Component.literal(" "))
                .append(labTechAnswerButton("[SALAH]", ChatFormatting.RED, "/deception false")));

        com.deception.network.ModNetworking.broadcastNightActionBar(
                Component.literal("Menunggu jawaban forensic scientist").withStyle(ChatFormatting.AQUA));
    }

    private Component labTechAnswerButton(String label, ChatFormatting color, String command) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Klik untuk menjawab"))));
    }

    /** Item yang ditunjuk emang salah satu means/clue pilihan murderer? */
    private boolean isPartOfSolution(BlockPos pos) {
        if (pos == null) return false;
        return pos.equals(murdererSelectedMeansPos) || pos.equals(murdererSelectedCluePos);
    }

    public boolean isLabTechAwaitingAnswer() {
        return state == State.ALLIES && labTechAwaitingAnswer;
    }

    /**
     * Jawaban FS ([BENAR]/[SALAH]) buat pertanyaan Lab Technician. Dipanggil
     * /deception true & /deception false -- lihat ModCommands, di sana ini
     * dicoba DULUAN sebelum jalur confession police badge.
     * @return false kalo lagi gak ada pertanyaan yang nunggu dijawab.
     */
    public boolean answerLabTechnician(boolean partOfSolution) {
        if (state != State.ALLIES || !labTechAwaitingAnswer) return false;

        labTechAwaitingAnswer = false;
        labTechAnswer = partOfSolution;

        ServerPlayer lt = alliesLabTechUuid != null ? serverRef.getPlayerList().getPlayer(alliesLabTechUuid) : null;
        if (lt != null) {
            lt.sendSystemMessage(Component.literal(""));
            lt.sendSystemMessage(buildLabTechResultLine());
        }

        closeLabTechEyesAndScheduleNext();
        return true;
    }

    private void closeLabTechEyesAndScheduleNext() {
        labTechConfirmed = true;
        labTechWindowOpen = false;
        labTechAwaitingAnswer = false;
        labTechAutoSkipAt = Integer.MAX_VALUE;
        clearSelectionHud(alliesLabTechUuid);

        if (leftLabTechUuid != null) {
            hasLabTechLeft = false;
            leftLabTechUuid = null;
            leftLabTechName = "";
        }

        ServerPlayer lt = alliesLabTechUuid != null ? serverRef.getPlayerList().getPlayer(alliesLabTechUuid) : null;
        if (lt != null) {
            putBlindfold(lt);
            nightBlindfolded.add(alliesLabTechUuid);
        }

        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal("Lab Technician tutup mata").withStyle(ChatFormatting.RED),
                Component.empty(), 10, 40, 10);
        com.deception.network.ModNetworking.broadcastNightActionBar(Component.empty());

        scheduleInsideManOrWake();
    }

    private void scheduleInsideManOrWake() {
        if (alliesInsideManUuid != null && !insideManConfirmed) {
            alliesInsideManWakeAt = alliesTicksElapsed + NIGHT_CLOSE_HOLD;
        } else {
            alliesWakeAllAt = alliesTicksElapsed + NIGHT_PRE_WAKE_HOLD;
            alliesDoneAt = alliesWakeAllAt + NIGHT_WAKE_HOLD;
        }
    }

    /**
     * LT kelamaan offline / di-skip. Kalo dia UDAH sempet nunjuk item,
     * pertanyaannya tetep diterusin ke FS (jawabannya nyampe pas dia rejoin);
     * kalo belum nunjuk apa-apa, gilirannya hangus.
     */
    private void autoFinishLabTech() {
        if (labTechConfirmed || labTechAwaitingAnswer) return;
        if (labTechSelectedPos != null) {
            askForensicScientistAboutItem();
            return;
        }
        notifyRole(Role.forensic_scientist,
                Component.literal("Lab Technician tidak sempat memeriksa item apa pun.").withStyle(ChatFormatting.GRAY));
        closeLabTechEyesAndScheduleNext();
    }

    private void checkLabTechWakeUpOffline() {
        if (labTechConfirmed || alliesLabTechUuid == null) return;
        if (serverRef.getPlayerList().getPlayer(alliesLabTechUuid) != null) return;
        if (labTechAutoSkipAt != Integer.MAX_VALUE) return;

        String name = getPlayerName(alliesLabTechUuid);
        leftLabTechUuid = alliesLabTechUuid;
        leftLabTechName = name;
        hasLabTechLeft = true;
        labTechAutoSkipAt = alliesTicksElapsed + LEAVE_TIMEOUT_TICKS;
        labTechAutoSkipTicksLeft = LEAVE_TIMEOUT_TICKS;

        notifyRole(Role.forensic_scientist, Component.literal(name + " (Lab Technician) sudah offline. Auto-skip dalam ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(getOfflineRevealSeconds() + " detik").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(".").withStyle(ChatFormatting.AQUA))
                .append(skipRevealPrompt()));
        sendActionBarToRole(Role.forensic_scientist, labTechSkipCountdownBar(getOfflineRevealSeconds()));
    }

    private static Component labTechSkipCountdownBar(int secondsLeft) {
        return Component.literal("⏱ Auto-skip Lab Technician dalam ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW));
    }

    private Component buildLabTechResultLine() {
        ServerLevel level = ArenaDimension.level(serverRef);
        String itemName = labTechSelectedPos != null ? getItemName(level, labTechSelectedPos) : "?";
        if (labTechAnswer == null) {
            return Component.literal("  " + itemName + ": belum ada jawaban.").withStyle(ChatFormatting.GRAY);
        }
        return labTechAnswer
                ? Component.literal("  " + itemName + " BENAR dipakai di TKP.").withStyle(ChatFormatting.GREEN)
                : Component.literal("  " + itemName + " TIDAK dipakai di TKP.").withStyle(ChatFormatting.RED);
    }

    // ---------- Inside Man ----------

    private void openInsideManWindow() {
        alliesInsideManWakeAt = Integer.MAX_VALUE;
        if (alliesInsideManUuid == null || insideManConfirmed) {
            alliesWakeAllAt = alliesTicksElapsed + NIGHT_PRE_WAKE_HOLD;
            alliesDoneAt = alliesWakeAllAt + NIGHT_WAKE_HOLD;
            return;
        }

        ServerPlayer im = serverRef.getPlayerList().getPlayer(alliesInsideManUuid);
        if (im != null) {
            removeBlindfold(im);
            nightBlindfolded.remove(alliesInsideManUuid);
            im.sendSystemMessage(Component.literal("Klik kanan orang yang badge-nya mau kamu cabut.")
                    .withStyle(ChatFormatting.RED));
            sendInsideManTargetList(im);
        }
        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal("Inside Man buka mata").withStyle(ChatFormatting.DARK_RED),
                Component.empty(), 10, 40, 10);
        com.deception.network.ModNetworking.broadcastNightActionBar(
                Component.literal("Menunggu inside man memilih target").withStyle(ChatFormatting.DARK_RED));
        insideManWindowOpen = true;
        refreshInsideManHud(null);

        checkInsideManWakeUpOffline();
    }

    /**
     * Daftar nama yang bisa diklik langsung -- cadangan buat kasus orangnya
     * lagi gak keliatan dari tempat Inside Man berdiri (semua orang lagi
     * tutup mata di posisi terakhir mereka, bukan dibariskan rapi).
     */
    private void sendInsideManTargetList(ServerPlayer insideMan) {
        insideMan.sendSystemMessage(Component.literal("Atau pilih dari daftar:").withStyle(ChatFormatting.GRAY));
        for (UUID uuid : registeredPlayers) {
            if (!PresentationManager.get().hasBadge(uuid)) continue;
            String name = getPlayerName(uuid);
            insideMan.sendSystemMessage(Component.literal("  ")
                    .append(Component.literal("[" + name + "]").withStyle(style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/deception target " + name))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Cabut badge " + name))))));
        }
    }

    /** Dipanggil dari event klik kanan entitas (lihat DeceptionMod#onEntityInteract). */
    public boolean onInsideManClickPlayer(ServerPlayer player, ServerPlayer target) {
        if (state != State.ALLIES || !insideManWindowOpen || insideManConfirmed) return false;
        if (!player.getUUID().equals(alliesInsideManUuid)) return false;
        selectInsideManTarget(player, target.getUUID());
        return true;
    }

    /** Dipanggil /deception target <playername>. @return false kalo bukan gilirannya. */
    public boolean onInsideManPickByName(ServerPlayer player, String targetName) {
        if (state != State.ALLIES || !insideManWindowOpen || insideManConfirmed) return false;
        if (!player.getUUID().equals(alliesInsideManUuid)) return false;

        for (UUID uuid : registeredPlayers) {
            if (getPlayerName(uuid).equalsIgnoreCase(targetName)) {
                selectInsideManTarget(player, uuid);
                return true;
            }
        }
        player.sendSystemMessage(Component.literal(targetName + " tidak terdaftar di game ini.").withStyle(ChatFormatting.RED));
        return true;
    }

    private void selectInsideManTarget(ServerPlayer insideMan, UUID targetUuid) {
        if (!registeredPlayers.contains(targetUuid)) {
            refreshInsideManHud(Component.literal("Dia bukan peserta game ini.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!PresentationManager.get().hasBadge(targetUuid)) {
            refreshInsideManHud(Component.literal(getPlayerName(targetUuid) + " sudah tidak punya badge.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        // Lepas tanda di target sebelumnya kalo dia ganti pilihan.
        if (insideManTargetUuid != null && !insideManTargetUuid.equals(targetUuid)) {
            ServerPlayer previous = serverRef.getPlayerList().getPlayer(insideManTargetUuid);
            if (previous != null) removeGlow(previous);
        }
        insideManTargetUuid = targetUuid;

        ServerPlayer target = serverRef.getPlayerList().getPlayer(targetUuid);
        if (target != null) applyGlow(target);

        refreshInsideManHud(null);
        maybeSendInsideManConfirmPrompt(insideMan);
    }

    private void closeInsideManEyesAndScheduleWake() {
        insideManConfirmed = true;
        insideManWindowOpen = false;
        insideManAutoSkipAt = Integer.MAX_VALUE;
        clearSelectionHud(alliesInsideManUuid);

        if (leftInsideManUuid != null) {
            hasInsideManLeft = false;
            leftInsideManUuid = null;
            leftInsideManName = "";
        }

        if (insideManTargetUuid != null) {
            ServerPlayer target = serverRef.getPlayerList().getPlayer(insideManTargetUuid);
            if (target != null) removeGlow(target);
        }

        ServerPlayer im = alliesInsideManUuid != null ? serverRef.getPlayerList().getPlayer(alliesInsideManUuid) : null;
        if (im != null) {
            putBlindfold(im);
            nightBlindfolded.add(alliesInsideManUuid);
        }

        com.deception.network.ModNetworking.broadcastNightTitle(
                Component.literal("Inside Man tutup mata").withStyle(ChatFormatting.RED),
                Component.empty(), 10, 40, 10);
        com.deception.network.ModNetworking.broadcastNightActionBar(Component.empty());

        alliesWakeAllAt = alliesTicksElapsed + NIGHT_PRE_WAKE_HOLD;
        alliesDoneAt = alliesWakeAllAt + NIGHT_WAKE_HOLD;
    }

    /**
     * IM kelamaan offline / di-skip. SENGAJA gak ngerandom target: nyabut
     * badge orang secara acak itu hukuman beneran buat pemain yang gak salah
     * apa-apa. Kalo dia udah sempet nunjuk, pilihannya dipake; kalo belum,
     * gilirannya hangus tanpa ada yang kehilangan badge.
     */
    private void autoFinishInsideMan() {
        if (insideManConfirmed) return;
        if (insideManTargetUuid == null) {
            notifyRole(Role.forensic_scientist,
                    Component.literal("Inside Man tidak sempat memilih target.").withStyle(ChatFormatting.GRAY));
        }
        closeInsideManEyesAndScheduleWake();
    }

    private void checkInsideManWakeUpOffline() {
        if (insideManConfirmed || alliesInsideManUuid == null) return;
        if (serverRef.getPlayerList().getPlayer(alliesInsideManUuid) != null) return;
        if (insideManAutoSkipAt != Integer.MAX_VALUE) return;

        String name = getPlayerName(alliesInsideManUuid);
        leftInsideManUuid = alliesInsideManUuid;
        leftInsideManName = name;
        hasInsideManLeft = true;
        insideManAutoSkipAt = alliesTicksElapsed + LEAVE_TIMEOUT_TICKS;
        insideManAutoSkipTicksLeft = LEAVE_TIMEOUT_TICKS;

        notifyRole(Role.forensic_scientist, Component.literal(name + " (Inside Man) sudah offline. Auto-skip dalam ")
                .withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal(getOfflineRevealSeconds() + " detik").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(".").withStyle(ChatFormatting.DARK_RED))
                .append(skipRevealPrompt()));
        sendActionBarToRole(Role.forensic_scientist, insideManSkipCountdownBar(getOfflineRevealSeconds()));
    }

    private static Component insideManSkipCountdownBar(int secondsLeft) {
        return Component.literal("⏱ Auto-skip Inside Man dalam ")
                .withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.YELLOW));
    }

    private Component buildInsideManResultLine() {
        String targetName = insideManTargetUuid != null ? getPlayerName(insideManTargetUuid) : "-";
        return Component.literal("  Badge yang kamu cabut: " + targetName).withStyle(ChatFormatting.DARK_RED);
    }

    // ---------- Konfirmasi, sinkron & skip fase Allies ----------

    private boolean onAlliesConfirmCommand(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Role role = roleAssignments.get(uuid);
        if (role == null) return false;

        if (role == Role.lab_technician && uuid.equals(alliesLabTechUuid)) {
            if (labTechConfirmed || labTechAwaitingAnswer) return false;
            if (!labTechWindowOpen) {
                player.sendSystemMessage(Component.literal("Belum waktunya konfirmasi.").withStyle(ChatFormatting.RED));
                return true;
            }
            if (labTechSelectedPos == null) {
                refreshLabTechHud(Component.literal("Pilih 1 item dulu.").withStyle(ChatFormatting.RED));
                return true;
            }
            askForensicScientistAboutItem();
            player.sendSystemMessage(Component.literal("Pertanyaan dikirim, tunggu jawaban Forensic Scientist.")
                    .withStyle(ChatFormatting.GREEN));
            return true;
        }

        if (role == Role.inside_man && uuid.equals(alliesInsideManUuid)) {
            if (insideManConfirmed) return false;
            if (!insideManWindowOpen) {
                player.sendSystemMessage(Component.literal("Belum waktunya konfirmasi.").withStyle(ChatFormatting.RED));
                return true;
            }
            if (insideManTargetUuid == null) {
                refreshInsideManHud(Component.literal("Pilih 1 orang dulu.").withStyle(ChatFormatting.RED));
                return true;
            }
            player.sendSystemMessage(buildInsideManResultLine());
            closeInsideManEyesAndScheduleWake();
            player.sendSystemMessage(Component.literal("Pilihan dikonfirmasi!").withStyle(ChatFormatting.GREEN));
            return true;
        }

        player.sendSystemMessage(Component.literal("Tidak ada yang perlu dikonfirmasi sekarang.").withStyle(ChatFormatting.RED));
        return true;
    }

    private void syncAlliesActionBar(ServerPlayer player) {
        Role role = roleAssignments.get(player.getUUID());

        if (role == Role.forensic_scientist) {
            Component bar = pendingCountdownBar();
            if (bar != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(bar));
                return;
            }
        }

        if (labTechAwaitingAnswer) {
            com.deception.network.ModNetworking.sendNightActionBarTo(player,
                    Component.literal("Menunggu jawaban forensic scientist").withStyle(ChatFormatting.AQUA));
        } else if (labTechWindowOpen && !labTechConfirmed) {
            com.deception.network.ModNetworking.sendNightActionBarTo(player,
                    Component.literal("Menunggu lab technician memilih item").withStyle(ChatFormatting.AQUA));
        } else if (insideManWindowOpen && !insideManConfirmed) {
            com.deception.network.ModNetworking.sendNightActionBarTo(player,
                    Component.literal("Menunggu inside man memilih target").withStyle(ChatFormatting.DARK_RED));
        }
    }

    private void resendAlliesConfirmMessage(ServerPlayer player, Role role) {
        UUID uuid = player.getUUID();

        if (role == Role.lab_technician && uuid.equals(alliesLabTechUuid)
                && labTechWindowOpen && !labTechConfirmed && !labTechAwaitingAnswer) {
            refreshLabTechHud(null);
            if (labTechSelectedPos == null) {
                player.sendSystemMessage(Component.literal("Klik kanan 1 item di TKP buat ditanyain ke Forensic Scientist.")
                        .withStyle(ChatFormatting.AQUA));
            } else {
                labTechConfirmPromptSent = false;
                maybeSendLabTechConfirmPrompt(player);
            }
        }

        if (role == Role.lab_technician && labTechAwaitingAnswer) {
            player.sendSystemMessage(Component.literal("Pertanyaanmu masih ditunggu jawabannya oleh Forensic Scientist.")
                    .withStyle(ChatFormatting.AQUA));
        }

        if (role == Role.inside_man && uuid.equals(alliesInsideManUuid)
                && insideManWindowOpen && !insideManConfirmed) {
            refreshInsideManHud(null);
            if (insideManTargetUuid == null) {
                player.sendSystemMessage(Component.literal("Klik kanan orang yang badge-nya mau kamu cabut.")
                        .withStyle(ChatFormatting.RED));
                sendInsideManTargetList(player);
            } else {
                insideManConfirmPromptSent = false;
                maybeSendInsideManConfirmPrompt(player);
            }
        }

        // FS balik pas pertanyaan LT masih ngegantung -- kirim ulang tombolnya.
        if (role == Role.forensic_scientist && labTechAwaitingAnswer && labTechSelectedPos != null) {
            ServerLevel level = ArenaDimension.level(serverRef);
            player.sendSystemMessage(Component.literal("Lab Technician menanyakan: "
                    + getItemName(level, labTechSelectedPos)).withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.literal("Item ini bagian dari solusi? ")
                    .append(labTechAnswerButton("[BENAR]", ChatFormatting.GREEN, "/deception true"))
                    .append(Component.literal(" "))
                    .append(labTechAnswerButton("[SALAH]", ChatFormatting.RED, "/deception false")));
        }
    }

    /** Pasangan skipReveal buat fase Allies -- majuin SATU tahap yang lagi jalan. */
    private boolean skipAlliesPhase() {
        if (labTechAwaitingAnswer) {
            answerLabTechnician(isPartOfSolution(labTechSelectedPos));
            return true;
        }
        if (labTechWindowOpen && !labTechConfirmed) {
            labTechAutoSkipAt = Integer.MAX_VALUE;
            autoFinishLabTech();
            return true;
        }
        if (alliesLabTechWakeAt != Integer.MAX_VALUE) {
            openLabTechWindow();
            return true;
        }
        if (insideManWindowOpen && !insideManConfirmed) {
            insideManAutoSkipAt = Integer.MAX_VALUE;
            autoFinishInsideMan();
            return true;
        }
        if (alliesInsideManWakeAt != Integer.MAX_VALUE) {
            openInsideManWindow();
            return true;
        }
        if (alliesWakeAllAt != Integer.MAX_VALUE && alliesTicksElapsed < alliesWakeAllAt) {
            wakeEveryoneFromAllies();
            alliesWakeAllAt = Integer.MAX_VALUE;
            alliesDoneAt = alliesTicksElapsed + NIGHT_WAKE_HOLD;
            return true;
        }
        return false;
    }

    /** Player left di tengah fase Allies. */
    private void onAlliesPlayerLeft(UUID uuid, Role role, String name) {
        if (role == Role.forensic_scientist && labTechAwaitingAnswer) {
            // Gak ada yang bisa jawab lagi -- dijawab otomatis pakai
            // kebenaran aslinya biar fase-nya gak macet selamanya.
            answerLabTechnician(isPartOfSolution(labTechSelectedPos));
            return;
        }

        if (role == Role.lab_technician && !labTechConfirmed && !labTechAwaitingAnswer
                && labTechWindowOpen && labTechAutoSkipAt == Integer.MAX_VALUE) {
            labTechAutoSkipAt = alliesTicksElapsed + LEAVE_TIMEOUT_TICKS;
            labTechAutoSkipTicksLeft = LEAVE_TIMEOUT_TICKS;
            leftLabTechUuid = uuid;
            leftLabTechName = name;
            hasLabTechLeft = true;

            notifyRole(Role.forensic_scientist, Component.literal(name + " (Lab Technician) keluar server. Auto-skip dalam ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(getOfflineRevealSeconds() + " detik").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(".").withStyle(ChatFormatting.AQUA))
                    .append(skipRevealPrompt()));
            sendActionBarToRole(Role.forensic_scientist, labTechSkipCountdownBar(getOfflineRevealSeconds()));
        }

        if (role == Role.inside_man && !insideManConfirmed
                && insideManWindowOpen && insideManAutoSkipAt == Integer.MAX_VALUE) {
            insideManAutoSkipAt = alliesTicksElapsed + LEAVE_TIMEOUT_TICKS;
            insideManAutoSkipTicksLeft = LEAVE_TIMEOUT_TICKS;
            leftInsideManUuid = uuid;
            leftInsideManName = name;
            hasInsideManLeft = true;

            notifyRole(Role.forensic_scientist, Component.literal(name + " (Inside Man) keluar server. Auto-skip dalam ")
                    .withStyle(ChatFormatting.DARK_RED)
                    .append(Component.literal(getOfflineRevealSeconds() + " detik").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(".").withStyle(ChatFormatting.DARK_RED))
                    .append(skipRevealPrompt()));
            sendActionBarToRole(Role.forensic_scientist, insideManSkipCountdownBar(getOfflineRevealSeconds()));
        }
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
                // Sapuan kedua, dan ini yang beneran ngerjain: pemain udah
                // 5 detik di dimensi arena, jadi chunk-nya dijamin ke-load
                // dan entitas display sisa game kemarin baru sekarang
                // keliatan buat dibuang. Dipanggil PERSIS sebelum arena
                // disusun ulang biar gak ada celah di antaranya.
                restoreArena(serverRef);

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
        } else if (state == State.ALLIES) {
            tickAlliesSequence();
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
                // Fase Allies cuma sekali seumur game: pas mau masuk ronde 2,
                // persis kayak rulebook Undercover Allies. Dicek SEBELUM
                // advanceRound (yang naikin nomor rondenya) soalnya jatah
                // scene tile ronde baru ikut ditunda kalo fase itu jalan.
                boolean alliesNext = PresentationManager.get().getRound() == 1 && hasAlliesPhaseRoles();

                if (PresentationManager.get().advanceRound(serverRef, !alliesNext)) {
                    discussTicksLeft = discussTimerSeconds * 20;
                    if (alliesNext) {
                        startAlliesSequence(serverRef);
                    } else {
                        state = State.DISCUSS;
                    }
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