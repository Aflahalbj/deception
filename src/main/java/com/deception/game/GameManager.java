package com.deception.game;

import com.deception.command.RoleDescriptions;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.registries.RegistryObject;
import com.deception.block.ClueBlock;

import java.util.*;

public class GameManager {

    public enum State {
        IDLE, COUNTDOWN, SHUFFLE, DISCUSS, RUNNING
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

    // ---------- Countdown (title/subtitle only, gak ada spam chat) ----------
    private static final int COUNTDOWN_SECONDS = 5;
    private int countdownTicks = 0;
    private int lastCountdownSecondShown = -1;

    // ---------- Shuffle role (animasi "ngocok" peran) ----------
    private static final int SHUFFLE_DURATION_TICKS = 60; // 3 detik
    private int shuffleTicksLeft = 0;
    private List<Role> activeRolePool = new ArrayList<>();

    private int discussTicksLeft = 0;

    private MinecraftServer serverRef;
    private final Random shuffleRandom = new Random();

    // ---------- Teleport + pasang clue/means random pas countdown selesai ----------
    private static final BlockPos ARENA_TELEPORT_POS = new BlockPos(119, -59, 179);

    // tiap dinding: sumbu yang jalan sepanjang dinding, koordinat tetap
    // (fixed axis), titik tengah, panjang, dan arah hadap block (ke dalam
    // ruangan) pas ditempel.
    private static final int MEANS_Y = -57; // baris atas
    private static final int CLUE_Y = -58;  // baris bawah

    // 4 cluster per dinding x 3 dinding = 12, nyamain max player deception (4-12).
    // tiap cluster lebar 4 block (4 means berjejer di atas, 4 clue berjejer di bawah)
    private static final int CLUSTERS_PER_WALL = 4;
    private static final int CLUSTER_WIDTH = 4;

    private record WallSpec(String label, boolean alongX, int fixedCoord, int center, int length, Direction facing) {}

    private static final WallSpec[] ARENA_WALLS = new WallSpec[]{
            // kiri: sepanjang X, z tetap 189, tengah x=119, panjang 23, hadap ke dalam (utara)
            new WallSpec("kiri", true, 189, 119, 23, Direction.NORTH),
            // kanan: sepanjang X, z tetap 169, tengah x=119, panjang 23, hadap ke dalam (selatan)
            new WallSpec("kanan", true, 169, 119, 23, Direction.SOUTH),
            // belakang: sepanjang Z, x tetap 130, tengah z=179, panjang 21, hadap ke dalam (barat)
            new WallSpec("belakang", false, 130, 179, 21, Direction.WEST),
    };

    /**
     * Bagi panjang dinding jadi CLUSTERS_PER_WALL segmen sama besar, tiap
     * segmen dikasih 1 cluster selebar CLUSTER_WIDTH block yang dipusatkan
     * di tengah segmennya -> otomatis ada jarak/space alami antar cluster.
     * Hasil: array of int[], tiap elemen isinya CLUSTER_WIDTH offset kolom
     * yang berdampingan buat 1 cluster.
     */
    private static int[][] clusterColumnGroups(int length) {
        int half = length / 2;
        int[][] groups = new int[CLUSTERS_PER_WALL][CLUSTER_WIDTH];
        for (int i = 0; i < CLUSTERS_PER_WALL; i++) {
            double segCenter = -half + (i + 0.5) * (length / (double) CLUSTERS_PER_WALL);
            int colStart = (int) Math.round(segCenter) - CLUSTER_WIDTH / 2;
            for (int j = 0; j < CLUSTER_WIDTH; j++) {
                groups[i][j] = colStart + j;
            }
        }
        return groups;
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
        this.countdownTicks = COUNTDOWN_SECONDS * 20;
        this.lastCountdownSecondShown = -1;
        return true;
    }

    public void stopGame() {
        this.state = State.IDLE;
        this.roleAssignments.clear();
        this.countdownTicks = 0;
        this.lastCountdownSecondShown = -1;
        this.shuffleTicksLeft = 0;
        this.discussTicksLeft = 0;
        if (serverRef != null) {
            broadcast(serverRef, Component.literal("Game dihentikan.").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Hitung role apa aja yang bakal aktif di game ini (berdasarkan jumlah
     * player teregistrasi + override /customrole), dipakai buat animasi
     * kocokan biar cuma nge-flash role yang beneran ada, bukan semua enum.
     */
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

        // Reveal final: title nama role, lalu di chat "Peran kamu adalah" + penjelasan singkat
        for (Map.Entry<UUID, Role> entry : roleAssignments.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                Component title = Component.literal(entry.getValue().getDisplayName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                Component subtitle = Component.literal("Peran kamu!").withStyle(ChatFormatting.YELLOW);
                sendTitle(player, title, subtitle, 5, 50, 15);
                playSoundTo(player, SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1.0F, 1.0F);

                player.sendSystemMessage(Component.literal("Peran kamu adalah: " + entry.getValue().getDisplayName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                player.sendSystemMessage(Component.literal(RoleDescriptions.get(entry.getValue()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /**
     * Title countdown sebelum peran dibagikan. Cuma title + subtitle, gak ada
     * spam chat. Dipanggil sekali tiap detik berubah (bukan tiap tick) biar
     * subtitle-nya keganti angka dengan rapi, plus sound tiap detik.
     */
    private void showCountdownTitle(int secondsLeft) {
        Component title = Component.literal("Game dimulai dalam").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        Component subtitle = Component.literal(String.valueOf(secondsLeft)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = serverRef.getPlayerList().getPlayer(uuid);
            if (player != null) {
                sendTitle(player, title, subtitle, 0, 20, 5);
                playSoundTo(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1.0F, 1.0F);
            }
        }
    }

    /**
     * Animasi "ngocok" role — dipanggil tiap tick pas SHUFFLE, nge-flash nama
     * role random (dari role yang beneran aktif di game ini) ke subtitle,
     * title-nya tetep "Mengocok Peran". Ada sound tiap flash biar berasa
     * kayak dadu diputer.
     */
    private void spinRoleTitle() {
        if (activeRolePool.isEmpty() || shuffleTicksLeft % 3 != 0) {
            return;
        }
        Role randomRole = activeRolePool.get(shuffleRandom.nextInt(activeRolePool.size()));
        Component title = Component.literal("Mengocok Peran").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        Component subtitle = Component.literal(randomRole.getDisplayName()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = serverRef.getPlayerList().getPlayer(uuid);
            if (player != null) {
                sendTitle(player, title, subtitle, 0, 4, 0);
                float pitch = 0.8F + shuffleRandom.nextFloat() * 0.7F; // makin acak biar kerasa "ngocok"
                playSoundTo(player, SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), SoundSource.MASTER, 0.6F, pitch);
            }
        }
    }

    private void sendTitle(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /**
     * Kirim sound ke 1 player spesifik di posisi dia sendiri (pakai null
     * sebagai "excluded player" biar semua yang deket denger, termasuk dia).
     */
    private void playSoundTo(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, SoundSource source, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, source, volume, pitch);
    }

    /**
     * Teleport semua player teregistrasi ke titik arena, lalu tempel
     * cluster means (baris atas, y=-57) + clue (baris bawah, y=-58) di
     * dinding kiri, kanan, belakang -- 4 cluster tiap dinding, spasi rata
     * ngikutin panjang dinding, dan gak ada ID yang dobel sama sekali.
     */
    private void teleportPlayersAndDecorateArena(MinecraftServer server) {
        ServerLevel level = server.overworld();

        for (UUID uuid : registeredPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.teleportTo(ARENA_TELEPORT_POS.getX() + 0.5, ARENA_TELEPORT_POS.getY(),
                        ARENA_TELEPORT_POS.getZ() + 0.5);
            }
        }

        // pisahin pool means & clue, masing-masing diacak sekali biar ID
        // yang kepake antar cluster/dinding gak pernah dobel
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

        for (WallSpec wall : ARENA_WALLS) {
            for (int[] columns : clusterColumnGroups(wall.length())) {
                for (int offset : columns) {
                    if (meansIndex >= meansPool.size() || clueIndex >= cluePool.size()) break;

                    int x = wall.alongX() ? wall.center() + offset : wall.fixedCoord();
                    int z = wall.alongX() ? wall.fixedCoord() : wall.center() + offset;

                    placeOnWall(level, meansPool.get(meansIndex++), new BlockPos(x, MEANS_Y, z), wall.facing());
                    placeOnWall(level, cluePool.get(clueIndex++), new BlockPos(x, CLUE_Y, z), wall.facing());
                }
            }
        }
    }

    private void placeOnWall(ServerLevel level, String id, BlockPos pos, Direction facing) {
        RegistryObject<Block> holder = ModBlocks.CLUE_BLOCKS.get(id);
        if (holder == null) return;
        BlockState state = holder.get().defaultBlockState()
                .setValue(ClueBlock.FACE, AttachFace.WALL)
                .setValue(ClueBlock.FACING, facing);
        level.setBlock(pos, state, 3);
    }

    // ---------- Tick loop, dipanggil dari server tick event ----------

    public void tick() {
        if (state == State.COUNTDOWN) {
            countdownTicks--;
            int secondsLeft = (int) Math.ceil(countdownTicks / 20.0);
            if (secondsLeft != lastCountdownSecondShown && secondsLeft > 0) {
                lastCountdownSecondShown = secondsLeft;
                showCountdownTitle(secondsLeft);
            }
            if (countdownTicks <= 0) {
                teleportPlayersAndDecorateArena(serverRef);
                state = State.SHUFFLE;
                shuffleTicksLeft = SHUFFLE_DURATION_TICKS;
                activeRolePool = computeActiveRolePool();
            }
        } else if (state == State.SHUFFLE) {
            shuffleTicksLeft--;
            spinRoleTitle();
            if (shuffleTicksLeft <= 0) {
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