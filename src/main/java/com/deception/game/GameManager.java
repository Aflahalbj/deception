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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.registries.RegistryObject;
import com.deception.block.ClueBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;

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
    private final List<UUID> spawnedHeadEntities = new ArrayList<>();

    // ---------- Teleport + pasang clue/means random pas countdown selesai ----------
    private static final BlockPos ARENA_TELEPORT_POS = new BlockPos(119, -59, 179);

    // tiap dinding: sumbu yang jalan sepanjang dinding, koordinat tetap
    // (fixed axis), titik tengah, panjang, dan arah hadap block (ke dalam
    // ruangan) pas ditempel.
    private static final int MEANS_Y = -57; // baris atas
    private static final int CLUE_Y = -58;  // baris bawah
    private static final int HEAD_Y = MEANS_Y + 1; // baris kepala player, nempel di atas means

    // maksimal cluster per dinding kiri/kanan (dinding belakang cuma kepake
    // kalau kiri+kanan udah penuh). Total maksimal 4+4+4 = 12, nyamain max
    // player deception (4-12). tiap cluster lebar 4 block (4 means berjejer
    // di atas, 4 clue berjejer di bawah)
    private static final int MAX_CLUSTERS_PER_SIDE_WALL = 4;
    private static final int CLUSTER_WIDTH = 4;

    // jarak "maju" dikit dari permukaan dinding biar kepala gak z-fighting
    // sama block dinding solid di belakangnya
    private static final float HEAD_SURFACE_INSET = 0.06F;

    private record WallSpec(String label, boolean alongX, int fixedCoord, int center, int length, Direction facing) {}

    private static final WallSpec[] ARENA_WALLS = new WallSpec[]{
            // kiri: sepanjang X, z tetap 189, tengah x=119, panjang 21, hadap ke dalam (utara)
            new WallSpec("kiri", true, 189, 120, 21, Direction.NORTH),
            // kanan: sepanjang X, z tetap 169, tengah x=119, panjang 21, hadap ke dalam (selatan)
            new WallSpec("kanan", true, 169, 120, 21, Direction.SOUTH),
            // belakang: sepanjang Z, x tetap 130, tengah z=179, panjang 21, hadap ke dalam (barat)
            new WallSpec("belakang", false, 130, 179, 21, Direction.WEST),
    };

    /**
     * Susun clusterCount cluster selebar CLUSTER_WIDTH block, dikasih gap
     * eksplisit (minimal 2 block) antar cluster yang berdampingan, terus
     * seluruh barisan (cluster + gap) dimentokin ke ujung BELAKANG dinding
     * (offset positif, sisi deket dinding belakang) -- bukan di-center.
     * Hasil: array of int[], tiap elemen isinya CLUSTER_WIDTH offset kolom
     * yang berdampingan buat 1 cluster.
     */
    private static int[][] clusterColumnGroups(int length, int clusterCount) {
        if (clusterCount <= 0) return new int[0][];

        // gap dihitung eksplisit dari sisa ruang dibagi rata ke celah
        // antar cluster -- minimal 2 block, biar keliatan jelas ada jarak
        // (dulu bisa jatuh ke 1 block doang, keliatan dempet)
        int gap = clusterCount > 1
                ? 1 : 0;
        int totalWidth = clusterCount * CLUSTER_WIDTH + gap * (clusterCount - 1);

        // mentokin ke ujung belakang (offset positif), bukan center
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

    /**
     * Berapa cluster yang kepake di tiap dinding (kiri, kanan, belakang)
     * berdasarkan JUMLAH PLAYER — bukan fixed 4/dinding lagi. Kiri & kanan
     * diisi rata dulu (maks MAX_CLUSTERS_PER_SIDE_WALL / dinding), sisanya
     * baru dilempar ke belakang. Kalau ganjil, kiri kebagian 1 lebih banyak.
     * Contoh: 4 player -> [2, 2, 0]. 9 player -> [4, 4, 1].
     */
    private static int[] computeClusterCountsPerWall(int totalPlayers) {
        int kiri = Math.min(MAX_CLUSTERS_PER_SIDE_WALL, (int) Math.ceil(totalPlayers / 2.0));
        int kanan = Math.min(MAX_CLUSTERS_PER_SIDE_WALL, totalPlayers - kiri);
        int belakang = totalPlayers - kiri - kanan;
        return new int[]{kiri, kanan, belakang}; // urutannya harus ngikutin ARENA_WALLS
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

    public void stopGame(MinecraftServer server) {
        this.state = State.IDLE;
        this.roleAssignments.clear();
        this.countdownTicks = 0;
        this.lastCountdownSecondShown = -1;
        this.shuffleTicksLeft = 0;
        this.discussTicksLeft = 0;

        MinecraftServer target = server != null ? server : serverRef;
        if (target != null) {
            restoreArena(target);
            broadcast(target, Component.literal("Game dihentikan.").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Balikin arena ke kondisi kosong: hapus semua block means/clue +
     * backing merah/biru di 3 dinding (diganti deepslate_tiles), dan
     * despawn semua item display kepala player yang pernah dipasang.
     */
    private void restoreArena(MinecraftServer server) {
        ServerLevel level = server.overworld();

        for (UUID headId : spawnedHeadEntities) {
            var entity = level.getEntity(headId);
            if (entity != null) {
                entity.discard();
            }
        }
        spawnedHeadEntities.clear();

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
     * cluster means (baris atas, y=-57) + clue (baris bawah, y=-58) --
     * TAPI jumlah cluster yang dipasang sekarang ngikutin JUMLAH PLAYER,
     * bukan fixed 4/dinding. Kiri & kanan diisi duluan sampai penuh (maks
     * 4 cluster/dinding), baru sisanya ke belakang. Tiap cluster = 1
     * player: dikasih backing blue concrete di belakang clue, red concrete
     * di belakang means, plus kepala (skin) player itu ditaro di atas
     * means-nya.
     */
    private void teleportPlayersAndDecorateArena(MinecraftServer server) {
        ServerLevel level = server.overworld();

        List<UUID> players = new ArrayList<>(registeredPlayers);
        for (UUID uuid : players) {
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
        int playerIndex = 0;

        int[] clusterCounts = computeClusterCountsPerWall(players.size()); // {kiri, kanan, belakang}

        for (int wallIdx = 0; wallIdx < ARENA_WALLS.length; wallIdx++) {
            WallSpec wall = ARENA_WALLS[wallIdx];

            for (int[] columns : clusterColumnGroups(wall.length(), clusterCounts[wallIdx])) {
                if (playerIndex >= players.size()) break;
                UUID ownerUuid = players.get(playerIndex++);
                ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);

                for (int offset : columns) {
                    if (meansIndex >= meansPool.size() || clueIndex >= cluePool.size()) break;

                    int x = wall.alongX() ? wall.center() + offset : wall.fixedCoord();
                    int z = wall.alongX() ? wall.fixedCoord() : wall.center() + offset;

                    BlockPos meansPos = new BlockPos(x, MEANS_Y, z);
                    BlockPos cluePos = new BlockPos(x, CLUE_Y, z);
                    placeOnWall(level, meansPool.get(meansIndex++), meansPos, wall.facing());
                    placeOnWall(level, cluePool.get(clueIndex++), cluePos, wall.facing());

                    // backing dinding: merah di belakang means, biru di belakang clue
                    Direction backingDir = wall.facing().getOpposite();
                    level.setBlock(meansPos.relative(backingDir), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                    level.setBlock(cluePos.relative(backingDir), Blocks.BLUE_CONCRETE.defaultBlockState(), 3);
                }

                if (owner != null) {
                    spawnOwnerHead(level, owner, wall, columns);
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

    /**
     * Taro kepala (skin) player pemilik cluster di atas baris means-nya
     * (HEAD_Y). Karena CLUSTER_WIDTH = 4 (genap), gak ada 1 block yang pas
     * di tengah cluster -- jadi kepalanya diapungkan (pakai item display
     * entity, BUKAN block grid) tepat di garis batas antara 2 block
     * tengah, kaya di foto referensi. Kalau nanti CLUSTER_WIDTH diubah jadi
     * ganjil, rumus ini otomatis jatuh pas di tengah 1 block juga.
     */
    private void spawnOwnerHead(ServerLevel level, ServerPlayer owner, WallSpec wall, int[] columns) {
        // titik tengah geometris cluster (dalam koordinat dunia, relatif ke wall.center())
        double centerOffset = columns[0] + CLUSTER_WIDTH / 2.0;

        // dorong dikit dari sisi yang NEMPEL ke dinding solid (backingDir)
        // ke arah dalam ruangan -- boundary-nya ditentuin dari backingStep
        // (sisi cluster yang bersentuhan sama dinding solid di belakangnya),
        // terus digeser dikit (HEAD_SURFACE_INSET) ke arah facing biar gak
        // z-fighting. Formula lama pake offset gede (hampir 1 block) yang
        // ngebuat kepala di kanan overshoot nembus ke sisi LUAR dinding
        // solid (makanya nongol di luar arena) -- sekarang offsetnya kecil
        // aja jadi selalu berhenti di dalam cell means/clue-nya sendiri.
        Direction backingDir = wall.facing().getOpposite();
        int backingStep = wall.alongX() ? backingDir.getStepZ() : backingDir.getStepX();
        int facingStep = -backingStep;
        double fixedCoordFlush = wall.fixedCoord() + Math.max(backingStep, 0) + facingStep * HEAD_SURFACE_INSET;

        double headX = wall.alongX() ? wall.center() + centerOffset : fixedCoordFlush;
        double headZ = wall.alongX() ? fixedCoordFlush : wall.center() + centerOffset;
        double headY = HEAD_Y + 0.5;

        ItemStack headStack = new ItemStack(Items.PLAYER_HEAD);
        // pake GameProfile asli si player (udah bawa texture properties
        // dari sesi login), bukan bikin profile kosong isinya cuma
        // Id+Name -- kalo cuma Id+Name doang, client kudu resolve texture
        // secara async dulu (kadang belum kebentuk pas dipasang -> muncul
        // kepala Steve generic)
        headStack.getOrCreateTag().put("SkullOwner",
                NbtUtils.writeGameProfile(new CompoundTag(), owner.getGameProfile()));

        // angle-nya kebalik dari sebelumnya (yaw - 180, bukan 180 - yaw).
        // Kebetulan buat NORTH(180) & SOUTH(0) hasilnya sama aja (180°
        // muter kemanapun arahnya tetep balik ke posisi yang sama), jadi
        // dinding kiri & kanan keliatan "kebetulan" bener -- tapi buat
        // WEST(90) kayak dinding belakang, arahnya kebalik 180° (ngadep
        // EAST bukan WEST), makanya kepalanya ga ngadep ke tengah ruangan.
        float yaw = wall.facing().toYRot();
        Quaternionf leftRotation = new Quaternionf();

        // Semua field Display entity (item, item_display, billboard,
        // transformation) di-set lewat NBT + Entity#load(), BUKAN lewat
        // setter Java langsung -- di 1.20.1 setter2 itu package-private di
        // mapping vanilla (gak public), sedangkan format NBT di bawah ini
        // 100% sama kayak yang dipake command "/summon item_display" (jadi
        // stabil lintas versi & gak nyentuh kelas client-only sama sekali).
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

        display.setNoGravity(true);
        display.setYRot(yaw);
        display.setPos(headX, headY, headZ);

        level.addFreshEntity(display);
        spawnedHeadEntities.add(display.getUUID());
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
            tag.putString("text",
            Component.Serializer.toJson(
                Component.literal(owner.getGameProfile().getName())
            ));
            
            tag.putString("billboard", "fixed");
            tag.putInt("background", 0);      // transparan
            tag.putInt("line_width", 200);
            tag.putByte("see_through", (byte)1);
            tag.putByte("default_background", (byte)0);
            
            text.load(tag);
            text.setYRot(yaw);
            text.setPos(textX, textY, textZ);

            level.addFreshEntity(text);
            spawnedHeadEntities.add(text.getUUID());
        }
    }

    private static ListTag floatList(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
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