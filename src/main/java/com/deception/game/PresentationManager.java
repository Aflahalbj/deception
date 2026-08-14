package com.deception.game;

import com.deception.block.InvestigationPaperBlockEntity;
import com.deception.init.ModBlocks;
import com.deception.init.ModItems;
import com.deception.network.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Nampung fitur "setelah reveal" yang gak dimasukin ke GameManager biar gak
 * makin bengkak: gate mulainya diskusi (nunggu FS naro clue pertama), kasih +
 * kunci police_badge, sampe fase PRESENTASI (giliran bicara). GameManager
 * cuma manggil method di sini dari tick()-nya, semua state & logic detailnya
 * di class ini.
 */
public class PresentationManager {

    private static final PresentationManager INSTANCE = new PresentationManager();

    public static PresentationManager get() {
        return INSTANCE;
    }

    private static final int MAX_ROUNDS = 3;
    private static final String TILE_REMOVER_TAG = "DeceptionTileRemover";
    // Sama persis kayak dipake InvestigationPaperItem#place() buat baca gate
    // "belum boleh ditempel" -- HARUS string yang sama di kedua tempat.
    public static final String PLACEMENT_LOCKED_TAG = "ForensicPlacementLocked";
    // Nandain totem_of_undying MANA yang beneran "police badge" kita (biar
    // gak ke-detect asal ada totem beneran di tangan siapapun).
    private static final String BADGE_TAG = "DeceptionPoliceBadge";
    private static final int CONFESSION_TITLE_DELAY_TICKS = 60; // 3 detik

    private boolean discussionStarted = false;
    private final Set<UUID> badgeHolders = new HashSet<>();
    // Police badge itu jatah SEKALI SEUMUR GAME, bukan per-ronde. Set ini
    // nyimpen siapa yang udah kepake badge-nya (lihat resolveConfession) biar
    // pas ronde baru mulai (onForensicPaperPlaced dipanggil lagi) dia GAK
    // dikasih badge baru.
    private final Set<UUID> usedBadges = new HashSet<>();
    // Badge udah pernah dibagi di game ini. Dipake onPlayerRejoined buat
    // mutusin apakah yang baru balik ini harusnya udah punya badge --
    // badgeHolders gak bisa dipake, soalnya yang offline pas pembagian
    // emang gak pernah masuk ke situ.
    private boolean badgesGiven = false;
    private final Set<UUID> skipVotes = new HashSet<>();
    private final Set<String> givenCategories = new HashSet<>();

    private int round = 1;
    private boolean roundTileRemoved = true; // ronde 1 gak butuh hapus tile dulu

    private List<UUID> presentasiOrder = List.of();
    private int presentasiIndex = 0;
    private int presentasiTicksLeft = 0;
    public static final int DEFAULT_PRESENTASI_TURN_SECONDS = 30;
    // Bisa di-override /deception settimer presentation -- SENGAJA gak
    // direset di reset(), konsisten sama GameManager#discussTimerSeconds
    // yang emang persist antar-game.
    private int presentasiTurnTicks = DEFAULT_PRESENTASI_TURN_SECONDS * 20;

    // Player yang lagi nunggu keputusan FS ([BENAR]/[SALAH]) abis klik kanan
    // badge-nya. Null = gak ada confession yang lagi berlangsung.
    private UUID pendingConfessorUuid = null;
    private int confessionTitleDelayTicks = 0;
    // Animasi titik jalan buat actionbar "menunggu ..." -- dipake bareng
    // sama fase confession & shootout (cuma salah satu yang aktif).
    private int waitDotTicks = 0;

    // Tebakan penyelidik BENAR tapi ada witness -- murderer dikasih 1
    // kesempatan terakhir "membungkam" witness pake 1 peluru shotgun sebelum
    // kemenangan penyelidik final (lihat startWitnessShootout).
    private boolean shootoutActive = false;
    private UUID shootoutMurdererUuid = null;
    private UUID shootoutWitnessUuid = null;
    // Pelatuk udah ditarik, lagi nunggu kepastian pelurunya nyangkut di
    // seseorang apa kagak (lihat SHOT_RESOLVE_GRACE_TICKS).
    private boolean shotFired = false;
    private int shotGraceTicks = 0;

    // Hasil "game selesai" yang lagi nunggu di-proses di tick() berikutnya
    // (lihat queueEndGame) -- gak boleh manggil GameManager#endGameWithResult
    // LANGSUNG dari event yang jalan di TENGAH-TENGAH tick entitas (misal
    // ProjectileImpactEvent, dipicu dari dalem AbstractArrow#tick()) karena
    // itu discard/reset entitas & block pas Minecraft masih looping list
    // entitas yang sama -> NPE/crash. Nunda ke tick() berikutnya (yang
    // dipanggil dari TickEvent.ServerTickEvent, bukan dari tengah tick
    // entitas) itu aman.
    private Component pendingEndTitle = null;
    private Component pendingEndMessage = null;
    private boolean pendingEndMurdererWon = false;

    public boolean isDiscussionStarted() {
        return discussionStarted;
    }

    public void setPresentasiTurnSeconds(int seconds) {
        this.presentasiTurnTicks = seconds * 20;
    }

    public int getPresentasiTurnSeconds() {
        return presentasiTurnTicks / 20;
    }

    public void resetPresentasiTurnToDefault() {
        this.presentasiTurnTicks = DEFAULT_PRESENTASI_TURN_SECONDS * 20;
    }

    public void reset(MinecraftServer server) {
        // Jaga-jaga kalo game di-stop paksa pas shootout masih jalan --
        // jangan sampe ada player yang kebekuan selamanya.
        if (server != null) PlayerFreeze.unfreezeAll(server);
        discussionStarted = false;
        // Beritahu client badge-nya udah dicabut (lihat javadoc
        // PoliceBadgeClientState) -- kalo cuma di-clear() di sini doang,
        // icon di sebelah nametag nyangkut keliatan terus soalnya client
        // gak pernah dikasih tau balik ke false.
        for (UUID uuid : badgeHolders) {
            ModNetworking.broadcastPoliceBadgeHolder(uuid, false);
        }
        badgeHolders.clear();
        usedBadges.clear();
        badgesGiven = false;
        skipVotes.clear();
        givenCategories.clear();
        round = 1;
        roundTileRemoved = true;
        presentasiOrder = List.of();
        presentasiIndex = 0;
        presentasiTicksLeft = 0;
        pendingConfessorUuid = null;
        confessionTitleDelayTicks = 0;
        waitDotTicks = 0;
        shootoutActive = false;
        shootoutMurdererUuid = null;
        shootoutWitnessUuid = null;
        shotFired = false;
        shotGraceTicks = 0;
        pendingEndTitle = null;
        pendingEndMessage = null;
    }

    /** Lihat javadoc field pendingEndTitle -- jangan panggil endGameWithResult langsung dari dalem event entitas. */
    private void queueEndGame(Component title, Component message, boolean murdererTeamWon) {
        pendingEndTitle = title;
        pendingEndMessage = message;
        pendingEndMurdererWon = murdererTeamWon;
    }

    /**
     * Dipanggil InvestigationPaperItem#place() tiap kali investigation
     * paper (yang udah ada jawabannya) berhasil ditempel. Cuma paper
     * PERTAMA di ronde ini yang efeknya kepake (guard discussionStarted).
     */
    public void onForensicPaperPlaced(ServerPlayer placer, MinecraftServer server) {
        if (discussionStarted) return;
        if (GameManager.get().getRoleAssignments().get(placer.getUUID()) != Role.forensic_scientist) return;
        discussionStarted = true;

        Component title = Component.literal("Diskusi Ronde " + round + " dimulai").withStyle(ChatFormatting.GREEN);
        GameManager.get().broadcast(server, Component.literal(""));
        GameManager.get().broadcast(server, Component.literal("  DISKUSI DIMULAI").withStyle(ChatFormatting.GREEN));
        GameManager.get().broadcast(server, Component.literal("").withStyle(ChatFormatting.WHITE));
        GameManager.get().broadcast(server, Component.literal("  Perhatikan petunjuk yang diberikan forensic scientist, \n  cari means & clue yang sesuai dengan petunjuk.").withStyle(ChatFormatting.WHITE));
        for (UUID uuid : GameManager.get().getRegisteredPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            player.connection.send(new ClientboundSetTitleTextPacket(title));

            // Badge cuma dikasih ke yang belum pernah pake (lihat usedBadges)
            // -- ronde baru bukan berarti dapet jatah badge baru.
            if (GameManager.get().getRoleAssignments().get(uuid) != Role.forensic_scientist
                    && !usedBadges.contains(uuid)) {
                giveAndLockBadge(player);
                badgesGiven = true;
            }
        }
    }

    // ---------- Skip (diskusi & presentasi) ----------

    /** Hasil /deception skip -- command yang nerjemahin jadi pesan ke pemanggil. */
    public enum SkipResult {
        /** Vote skip diskusi ke-toggle. Pesannya udah dibroadcast dari dalem. */
        DISCUSS_VOTED,
        /** Giliran presentasi yang lagi jalan dilewatin. */
        PRESENTASI_SKIPPED,
        /** Lagi presentasi, tapi yang manggil bukan si pembicara/FS/OP. */
        NO_PERMISSION,
        /** Lagi gak di fase yang bisa di-skip. */
        NOTHING_TO_SKIP
    }

    /** Pintu masuk /deception skip -- nentuin sendiri ini skip diskusi apa presentasi. */
    public SkipResult skip(ServerPlayer player, MinecraftServer server) {
        GameManager.State state = GameManager.get().getState();
        if (state == GameManager.State.DISCUSS) {
            return voteSkip(player, server) ? SkipResult.DISCUSS_VOTED : SkipResult.NOTHING_TO_SKIP;
        }
        if (state == GameManager.State.PRESENTASI) {
            return skipPresentasi(player, server);
        }
        return SkipResult.NOTHING_TO_SKIP;
    }

    /**
     * Skip giliran presentasi yang lagi jalan. Beda sama vote skip diskusi:
     * ini GAK pake voting -- yang lagi dapet giliran berhak nyudahin sendiri,
     * FS & OP berhak maksa lanjut.
     */
    private SkipResult skipPresentasi(ServerPlayer player, MinecraftServer server) {
        UUID speaker = getCurrentSpeaker();
        if (speaker == null) return SkipResult.NOTHING_TO_SKIP;

        UUID uuid = player.getUUID();
        boolean isSpeaker = uuid.equals(speaker);
        boolean isForensicScientist =
                GameManager.get().getRoleAssignments().get(uuid) == Role.forensic_scientist;
        if (!isSpeaker && !isForensicScientist && !player.hasPermissions(2)) {
            return SkipResult.NO_PERMISSION;
        }

        String speakerName = GameManager.get().getPlayerName(speaker);
        GameManager.get().broadcast(server, Component.literal(isSpeaker
                        ? speakerName + " menyelesaikan gilirannya lebih awal."
                        : "Giliran " + speakerName + " dilewati oleh " + GameManager.get().getPlayerName(uuid) + ".")
                .withStyle(ChatFormatting.YELLOW));

        // Cukup abisin sisa waktunya, biar pindah giliran (termasuk kalo ini
        // giliran TERAKHIR -> lanjut ronde/RUNNING) tetep lewat jalur yang
        // sama persis kayak waktu abis normal, lihat tickPresentasi.
        presentasiTicksLeft = 0;
        return SkipResult.PRESENTASI_SKIPPED;
    }

    /** @return false kalo dipanggil di luar fase diskusi yang lagi berjalan (command bakal kasih tau gagal). */
    private boolean voteSkip(ServerPlayer player, MinecraftServer server) {
        if (GameManager.get().getState() != GameManager.State.DISCUSS || !discussionStarted) return false;

        UUID uuid = player.getUUID();
        boolean nowVoting = skipVotes.add(uuid);
        if (!nowVoting) {
            skipVotes.remove(uuid);
        }

        int onlineCount = 0;
        int votedCount = 0;
        for (UUID u : GameManager.get().getRegisteredPlayers()) {
            if (server.getPlayerList().getPlayer(u) == null) continue;
            onlineCount++;
            if (skipVotes.contains(u)) votedCount++;
        }

        Component msg = Component.literal(GameManager.get().getPlayerName(uuid)
                        + (nowVoting ? " vote SKIP diskusi" : " membatalkan vote skip")
                        + " (" + votedCount + "/" + onlineCount + ")")
                .withStyle(ChatFormatting.YELLOW);
        for (UUID u : GameManager.get().getRegisteredPlayers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(msg);
        }

        if (onlineCount > 0 && votedCount > onlineCount / 2.0) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("Voting skip berhasil! Diskusi dilewati.").withStyle(ChatFormatting.GREEN), false);
            GameManager.get().forceEndDiscussion(server);
        }
        return true;
    }

    /** Actionbar countdown diskusi vanilla, dipanggil GameManager tiap tick selama fase DISCUSS udah started. */
    public void broadcastDiscussCountdown(int discussTicksLeft) {
        int minutes = discussTicksLeft / 1200;
        int seconds = (discussTicksLeft % 1200) / 20;
        Component actionBar = Component.literal(String.format("⏱ Diskusi: %02d:%02d", minutes, seconds))
                .withStyle(ChatFormatting.GOLD);
        GameManager.get().broadcastActionBarToAll(actionBar);
    }

    // ---------- Police badge ----------
    // "police_badge" = Totem of Undying ASLI (ditandain NBT BADGE_TAG) yang
    // TEXTURE-nya di-override ke badge (assets/minecraft/models/item/
    // totem_of_undying.json) -- bukan item custom. Ini biar animasi/efek
    // pas dipake ("Confession", lihat di bawah) OTOMATIS jalan sempurna
    // (partikel+kilat+suara vanilla asli), gak perlu hack rendering yang
    // gak bisa dipastikan jalan.

    /**
     * Dipanggil GameManager#onPlayerRejoined. Badge itu dibagi sekali pas
     * diskusi mulai, dan yang lagi offline waktu itu kelewat -- jadi
     * jatahnya dikasih di sini pas dia balik. Sekalian nyamain ulang icon
     * badge di nametag, soalnya PoliceBadgeClientState di client cuma
     * di-update lewat broadcast dan yang kekirim selama dia offline
     * ke-lewat semua.
     */
    public void onPlayerRejoined(ServerPlayer player, MinecraftServer server) {
        UUID uuid = player.getUUID();

        if (badgesGiven && !usedBadges.contains(uuid)
                && GameManager.get().getRoleAssignments().get(uuid) != Role.forensic_scientist) {
            giveAndLockBadge(player);
        } else if (usedBadges.contains(uuid)) {
            // Jatahnya udah kepake pas dia masih online. Bersihin sisa item
            // di inventory-nya, jaga-jaga dia left persis di sela-sela
            // pencabutan badge.
            clearBadge(player);
        }

        for (UUID other : GameManager.get().getRegisteredPlayers()) {
            ModNetworking.sendPoliceBadgeHolder(player, other, badgeHolders.contains(other));
        }
    }

    private void giveAndLockBadge(ServerPlayer player) {
        badgeHolders.add(player.getUUID());
        player.getInventory().setItem(4, createBadgeStack());
        ModNetworking.broadcastPoliceBadgeHolder(player.getUUID(), true);
    }

    private void clearBadge(ServerPlayer player) {
        badgeHolders.remove(player.getUUID());
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (isLockedItem(inventory.items.get(i))) {
                inventory.items.set(i, ItemStack.EMPTY);
            }
        }
        if (isLockedItem(inventory.offhand.get(0))) {
            inventory.offhand.set(0, ItemStack.EMPTY);
        }
        ModNetworking.broadcastPoliceBadgeHolder(player.getUUID(), false);
    }

    private ItemStack createBadgeStack() {
        ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING);
        stack.getOrCreateTag().putBoolean(BADGE_TAG, true);
        stack.setHoverName(Component.literal("Police Badge").withStyle(ChatFormatting.GOLD));
        return stack;
    }

    public boolean isLockedItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING
                && stack.getTag() != null && stack.getTag().getBoolean(BADGE_TAG);
    }

    /**
     * Safety-net badge (pola sama kayak force-reequip blindfold di
     * GameManager) + countdown title confession. Dipanggil GameManager.tick()
     * tiap tick tanpa syarat state.
     */
    public void tick(MinecraftServer server) {
        if (pendingEndTitle != null) {
            Component title = pendingEndTitle;
            Component message = pendingEndMessage;
            boolean murdererWon = pendingEndMurdererWon;
            pendingEndTitle = null;
            pendingEndMessage = null;
            GameManager.get().endGameWithResult(server, title, message, murdererWon);
            return;
        }

        for (UUID uuid : badgeHolders) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) enforceBadgeSlot(player);
        }

        if (confessionTitleDelayTicks > 0) {
            confessionTitleDelayTicks--;
            if (confessionTitleDelayTicks == 0) {
                announceConfession(server);
            }
        }

        PlayerFreeze.tick(server);
        if (shootoutActive) {
            tickShootout(server);
        }

        // Selagi ada confession yang lagi nunggu keputusan FS ATAU shootout
        // lagi jalan, diskusi/presentasi DI-PAUSE (lihat GameManager#tick,
        // dicek lewat isBusy()) -- actionbar-nya jadi tanggung jawab kita
        // sepenuhnya di sini, biar gak rebutan sama actionbar diskusi/
        // presentasi yang jalan bareng. Dua-duanya SENGAJA gak pake hitung
        // mundur: nunggu sampe orangnya beneran mutusin/nembak, bukan
        // sampe waktunya abis.
        if (isBusy()) {
            waitDotTicks++;
            if (waitDotTicks % 10 == 0) {
                int dotCount = (int) ((waitDotTicks / 10) % 4);
                String base = shootoutActive ? "Menunggu murderer menembak" : "Menunggu keputusan forensic scientist";
                Component actionBar = Component.literal(base + ".".repeat(dotCount)).withStyle(ChatFormatting.GOLD);
                GameManager.get().broadcastActionBarToAll(actionBar);
            }
        }
    }

    /** GameManager#tick pause timer diskusi/presentasi selagi ini true (lihat javadoc di atas). */
    public boolean isBusy() {
        return pendingConfessorUuid != null || shootoutActive;
    }

    private void enforceBadgeSlot(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        boolean sawBadgeInSlot = false;

        for (int i = 0; i < inventory.items.size(); i++) {
            if (isLockedItem(inventory.items.get(i))) {
                if (i == 4) {
                    sawBadgeInSlot = true;
                } else {
                    inventory.items.set(i, ItemStack.EMPTY);
                }
            }
        }
        if (isLockedItem(inventory.offhand.get(0))) {
            inventory.offhand.set(0, ItemStack.EMPTY);
        }
        if (!sawBadgeInSlot) {
            inventory.items.set(4, createBadgeStack());
        }
    }

    // ---------- Confession ("menyelesaikan kasus") ----------

    /**
     * Dipanggil DeceptionMod pas player klik kanan badge-nya (item kosong,
     * lihat PlayerInteractEvent.RightClickItem). @return true kalo diproses
     * (caller gak perlu ngapa-ngapain lagi).
     */
    public boolean tryStartConfession(ServerPlayer player, MinecraftServer server) {
        ItemStack held = player.getMainHandItem();
        if (!isLockedItem(held) && !isLockedItem(player.getOffhandItem())) return false;
        if (shootoutActive) {
            // Fase shootout udah jalan -- kasusnya udah kelar ditebak, tinggal
            // nunggu tembakan murderer. Badge gak ada gunanya lagi di sini.
            return true;
        }
        if (pendingConfessorUuid != null) {
            player.sendSystemMessage(Component.literal("Masih ada tuduhan yang berlangsung, tunggu keputusan FS.").withStyle(ChatFormatting.RED));
            return true;
        }

        pendingConfessorUuid = player.getUUID();
        confessionTitleDelayTicks = CONFESSION_TITLE_DELAY_TICKS;

        // Badge dicabut SEKARANG, bukan nunggu FS jawab. Kalo nunggu, player
        // yang left di tengah-tengah bakal kesimpen sama badge-nya di
        // inventory offline-nya (resolveConfession cuma bisa ngebersihin
        // player yang lagi online), terus balik lagi bawa badge yang
        // harusnya udah kepake -- bisa dipake confess dua kali.
        usedBadges.add(player.getUUID());
        clearBadge(player);

        // Animasi pop totem (partikel + suara + popup ikon aktivasi) di SEMUA
        // layar. Sengaja pake packet sendiri, BUKAN broadcastEntityEvent(35)
        // vanilla, soalnya popup-nya di vanilla cuma keliatan sama yang make
        // -- lihat javadoc PoliceBadgeUsePacket. Item-nya tetep Totem of
        // Undying asli (di-retexture jadi badge), jadi popup-nya ikut
        // nunjukin texture badge, bukan totem vanilla.
        ModNetworking.broadcastPoliceBadgeUse(player.getUUID());
        return true;
    }

    private void announceConfession(MinecraftServer server) {
        if (pendingConfessorUuid == null) return;
        String name = GameManager.get().getPlayerName(pendingConfessorUuid);

        Component title = Component.literal(name).withStyle(ChatFormatting.GOLD);
        Component subtitle = Component.literal("Ingin menyelesaikan kasus").withStyle(ChatFormatting.YELLOW);
        for (UUID uuid : GameManager.get().getRegisteredPlayers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
                p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
        }

        UUID fsUuid = null;
        for (var e : GameManager.get().getRoleAssignments().entrySet()) {
            if (e.getValue() == Role.forensic_scientist) {
                fsUuid = e.getKey();
                break;
            }
        }
        ServerPlayer fsPlayer = fsUuid != null ? server.getPlayerList().getPlayer(fsUuid) : null;
        if (fsPlayer != null) {
            fsPlayer.sendSystemMessage(Component.literal("Apakah tebakannya benar? ")
                    .append(confessionButton("[BENAR]", ChatFormatting.GREEN, "/deception true"))
                    .append(Component.literal(" "))
                    .append(confessionButton("[SALAH]", ChatFormatting.RED, "/deception false")));
        }
    }

    private Component confessionButton(String label, ChatFormatting color, String command) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Klik untuk menjawab"))));
    }

    /** Dipanggil /deception true (correct=true) atau /deception false (correct=false). @return false kalo gak ada confession yang pending. */
    public boolean resolveConfession(boolean correct, MinecraftServer server) {
        if (pendingConfessorUuid == null) return false;

        UUID confessorUuid = pendingConfessorUuid;
        pendingConfessorUuid = null;
        // Badge-nya udah dicabut + dicatet di usedBadges pas dia klik kanan
        // (lihat tryStartConfession), jadi di sini gak ada yang perlu
        // dibersihin lagi -- termasuk kalo dia keburu left.

        if (correct) {
            String name = GameManager.get().getPlayerName(confessorUuid);
            if (GameManager.get().getRoleAssignments().containsValue(Role.witness)) {
                // Ada witness -- murderer masih dapet 1 kesempatan terakhir
                // "membungkam" witness (panah) sebelum kemenangan penyelidik
                // final. Lihat startWitnessShootout.
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal(name + " berhasil menebak! Tapi murderer masih punya 1 kesempatan terakhir...")
                                .withStyle(ChatFormatting.YELLOW), false);
                startWitnessShootout(server);
                return true;
            }

            Component msg = Component.literal(name + " berhasil menyelesaikan kasus! Tim penyelidik menang.")
                    .withStyle(ChatFormatting.GREEN);
            queueEndGame(Component.literal("TIM PENYELIDIK MENANG!").withStyle(ChatFormatting.GREEN), msg, false);
            return true;
        }

        GameManager.get().broadcastActionBarToAll(Component.empty());
        for (UUID uuid : GameManager.get().getRegisteredPlayers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("TEBAKAN SALAH").withStyle(ChatFormatting.DARK_RED)));
            }
        }

        if (badgeHolders.isEmpty()) {
            queueEndGame(Component.literal("PEMBUNUH MENANG!").withStyle(ChatFormatting.DARK_RED),
                    Component.literal("Semua police badge sudah habis dipakai, tidak ada yang berhasil menebak. Pembunuh menang.").withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    // ---------- Shootout murderer vs witness (nembak beneran) ----------
    // Murderer dikasih shotgun CGM isi 1 peluru (lihat ShootoutGun), semua
    // calon target dijejerin di depan cluster masing-masing dalam keadaan
    // GAK BISA GERAK (lihat PlayerFreeze) -- jadi yang nentuin bukan
    // refleks/aim, tapi murni dia nebak yang mana witness-nya. Kena witness
    // = penjahat menang; salah orang atau pelurunya meleset = penyelidik
    // menang.

    /**
     * Jeda setelah pelatuk ditarik sebelum divonis "meleset". Peluru CGM
     * butuh beberapa tick buat nyampe sasaran, jadi gak bisa langsung
     * divonis pas AmmoCount turun jadi 0.
     */
    private static final int SHOT_RESOLVE_GRACE_TICKS = 30;
    /** Slot hotbar tempat shotgun dikunci (slot 1 di layar). */
    private static final int SHOTGUN_SLOT = 0;

    private void startWitnessShootout(MinecraftServer server) {
        UUID murdererUuid = null;
        UUID witnessUuid = null;
        for (var e : GameManager.get().getRoleAssignments().entrySet()) {
            if (e.getValue() == Role.murderer) murdererUuid = e.getKey();
            else if (e.getValue() == Role.witness) witnessUuid = e.getKey();
        }
        if (murdererUuid == null || witnessUuid == null) {
            // Gak mungkin ke sini (containsValue(Role.witness) udah dicek
            // sebelum manggil method ini) -- tapi jaga-jaga aja biar gak macet.
            queueEndGame(Component.literal("TIM PENYELIDIK MENANG!").withStyle(ChatFormatting.GREEN),
                    Component.literal("Tim penyelidik menang.").withStyle(ChatFormatting.GREEN), false);
            return;
        }

        ServerPlayer murderer = server.getPlayerList().getPlayer(murdererUuid);
        if (murderer == null) {
            queueEndGame(Component.literal("TIM PENYELIDIK MENANG!").withStyle(ChatFormatting.GREEN),
                    Component.literal("Murderer offline, tidak bisa menembak. Tim penyelidik menang.").withStyle(ChatFormatting.GREEN), false);
            return;
        }

        ItemStack shotgun = ShootoutGun.createLoadedShotgun();
        if (shotgun.isEmpty()) {
            // CGM didaftar mandatory di mods.toml jadi normalnya mustahil --
            // tapi kalo somehow kejadian, mending bilang terus terang
            // daripada ngasih murderer senjata kosong yang gak bisa dipake.
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("Tidak dapat menemukan shotgun (mod CGM), fase tembakan dilewati.").withStyle(ChatFormatting.RED), false);
            queueEndGame(Component.literal("TIM PENYELIDIK MENANG!").withStyle(ChatFormatting.GREEN),
                    Component.literal("Tim penyelidik menang.").withStyle(ChatFormatting.GREEN), false);
            return;
        }

        shootoutActive = true;
        shootoutMurdererUuid = murdererUuid;
        shootoutWitnessUuid = witnessUuid;
        shotFired = false;
        shotGraceTicks = 0;

        // PvP dimatiin pas game mulai (GameManager#startGame). Peluru CGM
        // nyerang lewat Entity#hurt biasa, dan ServerPlayer#canHarmPlayer
        // nolak MENTAH-MENTAH semua serangan antar-player selama PvP mati --
        // jadi tanpa ini pelurunya nembus tanpa efek apa-apa dan
        // onShootoutHit gak akan pernah kepanggil. Dibalikin ke semula
        // otomatis di GameManager#abortGame pas game selesai.
        server.setPvpAllowed(true);

        lineUpTargets(server, murdererUuid);

        murderer.getInventory().setItem(SHOTGUN_SLOT, shotgun);
        // Pindah slot aktif ke shotgun-nya. `selected` itu state server, dan
        // vanilla gak pernah ngirim balik ke client sendiri (normalnya client
        // yang ngasih tau server) -- jadi harus dikabarin manual, kalo gak
        // client-nya masih megang slot lama dan gun-nya keliatan gak kepegang.
        murderer.getInventory().selected = SHOTGUN_SLOT;
        murderer.connection.send(new ClientboundSetCarriedItemPacket(SHOTGUN_SLOT));

        Component title = Component.literal("TEMBAKAN TERAKHIR").withStyle(ChatFormatting.DARK_RED);
        Component subtitle = Component.literal("Murderer punya 1 peluru untuk membunuh witness")
                .withStyle(ChatFormatting.RED);
        for (UUID uuid : GameManager.get().getRegisteredPlayers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null) continue;
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
        murderer.sendSystemMessage(Component.literal("Tembak orang yang kamu yakin witness. Hanya ada 1 peluru, jika salah orang atau meleset, kamu akan ditahan.")
                .withStyle(ChatFormatting.RED));
    }

    /**
     * Jejerin semua calon target di depan cluster punya mereka SENDIRI terus
     * dibekuin. Murderer & accomplice sengaja dilewat (mereka satu tim, gak
     * ikut jadi sasaran dan tetep bebas gerak); FS dipisah ke depan board
     * biar gak nyempil di garis tembak.
     */
    private void lineUpTargets(MinecraftServer server, UUID murdererUuid) {
        for (UUID uuid : GameManager.get().getRegisteredPlayers()) {
            Role role = GameManager.get().getRoleAssignments().get(uuid);
            if (uuid.equals(murdererUuid) || role == Role.accomplice) continue;

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            GameManager.LineupSpot spot = role == Role.forensic_scientist
                    ? GameManager.get().getForensicBoardSpot()
                    : GameManager.get().getClusterLineupSpot(uuid);
            if (spot == null) {
                // Gak punya cluster (harusnya cuma FS, dan FS udah dihandle
                // di atas) -- tetep dibekuin di tempat dia berdiri sekarang
                // biar gak ada yang bisa lari-lari selagi yang lain diem.
                PlayerFreeze.freezeAt(player, player.getX(), player.getY(), player.getZ(), player.getYRot());
                continue;
            }
            PlayerFreeze.freezeAt(player, spot.x(), spot.y(), spot.z(), spot.yaw());
        }
    }

    private void tickShootout(MinecraftServer server) {
        ServerPlayer murderer = server.getPlayerList().getPlayer(shootoutMurdererUuid);
        if (murderer != null) enforceShotgunSlot(murderer);

        if (shotFired) {
            // Pelurunya udah melayang -- kalo sampe grace-nya abis gak ada
            // yang kena (onShootoutHit gak kepanggil), berarti meleset.
            shotGraceTicks--;
            if (shotGraceTicks <= 0) {
                resolveShootout(server, false, "Tembakan murderer meleset. Tim penyelidik menang.");
            }
            return;
        }

        if (murderer != null && hasFiredShot(murderer)) {
            shotFired = true;
            shotGraceTicks = SHOT_RESOLVE_GRACE_TICKS;
        }
    }

    /**
     * Paku shotgun di slot 1 hotbar -- pola sama persis kayak
     * enforceBadgeSlot: drop-nya dicegat ItemTossEvent, sisanya (swap ke
     * offhand pake F, geser lewat GUI inventory) dibenerin di sini tiap tick.
     */
    private void enforceShotgunSlot(ServerPlayer murderer) {
        Inventory inventory = murderer.getInventory();
        if (ShootoutGun.isShootoutGun(inventory.items.get(SHOTGUN_SLOT))) return;

        // Stack lagi "diangkat" pake kursor di GUI inventory. Pas lagi
        // diangkat, dia GAK ADA di Inventory manapun (nangkring di menu),
        // jadi ini HARUS dicek duluan -- kalo kelewat, di bawah bakal
        // dikira ilang terus dibikinin baru, dan pas kursornya dilepas
        // jadi dobel.
        AbstractContainerMenu menu = murderer.containerMenu;
        if (ShootoutGun.isShootoutGun(menu.getCarried())) {
            inventory.items.set(SHOTGUN_SLOT, menu.getCarried());
            menu.setCarried(ItemStack.EMPTY);
            // Kosongin kursor di layar client juga -- slot -1 di window -1
            // itu cara vanilla nunjuk "item yang lagi digenggam kursor".
            murderer.connection.send(new ClientboundContainerSetSlotPacket(
                    -1, menu.incrementStateId(), -1, ItemStack.EMPTY));
            return;
        }

        // Sapu SEMUA slot GUI inventory (kantong, hotbar, offhand, armor,
        // sampe kotak crafting 2x2 -- yang terakhir ini gak keliatan dari
        // Inventory.items). PINDAHIN stack yang ketemu, jangan bikin baru:
        // sisa peluru nempel di NBT stack-nya, bikin baru = peluru gratis.
        for (Slot slot : murderer.inventoryMenu.slots) {
            if (ShootoutGun.isShootoutGun(slot.getItem())) {
                ItemStack gun = slot.getItem();
                slot.set(ItemStack.EMPTY);
                inventory.items.set(SHOTGUN_SLOT, gun);
                murderer.inventoryMenu.broadcastChanges();
                return;
            }
        }

        // Bener-bener gak ada di manapun. Cuma dibikinin baru kalo dia
        // belom sempet nembak -- kalo udah, jangan, itu bakal ngasih
        // kesempatan kedua yang gak boleh ada.
        if (!shotFired) {
            inventory.items.set(SHOTGUN_SLOT, ShootoutGun.createLoadedShotgun());
        }
    }

    /** Pelatuk ke-tarik ke-detect dari sisa peluru shotgun-nya yang abis (lihat ShootoutGun). */
    private boolean hasFiredShot(ServerPlayer murderer) {
        ItemStack gun = murderer.getInventory().items.get(SHOTGUN_SLOT);
        // enforceShotgunSlot udah mastiin posisinya di sini tiap tick.
        return ShootoutGun.isShootoutGun(gun) && ShootoutGun.getAmmoCount(gun) <= 0;
    }

    /**
     * Dipanggil DeceptionMod#onLivingAttack tiap ada player kena serangan
     * selagi shootout jalan. @return true kalo serangannya harus di-CANCEL.
     *
     * Selama fase ini GAK ADA player yang boleh luka -- siapapun korbannya
     * (peserta maupun yang cuma nonton) dan siapapun penyerangnya. Yang
     * NGITUNG sebagai tebakan cuma peluru dari murderer. Bedainnya dari
     * direct entity damage source-nya: kalo yang nyentuh korban itu si
     * penyerang SENDIRI, berarti dia mukul/nyerang langsung -- bukan
     * nembak. Peluru bikin direct entity-nya jadi entitas peluru, beda dari
     * si penembak.
     */
    public boolean onShootoutHit(ServerPlayer victim, DamageSource source) {
        if (!shootoutActive) return false;

        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        boolean shotByMurderer = attacker != null
                && attacker.getUUID().equals(shootoutMurdererUuid)
                && direct != null && direct != attacker;
        if (!shotByMurderer) {
            // Pukulan, jatuh, apapun -- gak ngitung, dan gak ngelukain juga.
            return true;
        }

        if (!GameManager.get().getRegisteredPlayers().contains(victim.getUUID())) {
            // Pelurunya nyasar ke orang yang gak ikut main -- gak bisa
            // dianggap tebakan. Pelurunya tetep abis, jadi biar grace-nya
            // yang vonis meleset (lihat tickShootout).
            return true;
        }

        MinecraftServer server = victim.getServer();
        if (server == null) return true;

        boolean hitWitness = victim.getUUID().equals(shootoutWitnessUuid);
        String victimName = GameManager.get().getPlayerName(victim.getUUID());
        resolveShootout(server, hitWitness, hitWitness
                ? "Murderer berhasil membunuh " + victimName + " (witness). Pembunuh menang."
                : "Murderer menembak " + victimName + ", bukan witness. Tim penyelidik menang.");
        return true;
    }

    /**
     * Satu-satunya jalan keluar fase shootout: lepasin bekuan + senjata dulu
     * (aman dipanggil dari tengah event entitas, cuma ngurusin inventory &
     * packet), baru antri-in hasil game-nya lewat queueEndGame.
     */
    private void resolveShootout(MinecraftServer server, boolean murdererWon, String message) {
        shootoutActive = false;
        shotFired = false;
        shotGraceTicks = 0;

        PlayerFreeze.unfreezeAll(server);

        ServerPlayer murderer = server.getPlayerList().getPlayer(shootoutMurdererUuid);
        if (murderer != null) {
            Inventory inventory = murderer.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) {
                if (ShootoutGun.isShootoutGun(inventory.items.get(i))) {
                    inventory.items.set(i, ItemStack.EMPTY);
                }
            }
            if (ShootoutGun.isShootoutGun(inventory.offhand.get(0))) {
                inventory.offhand.set(0, ItemStack.EMPTY);
            }
        }

        GameManager.get().broadcastActionBarToAll(Component.empty());
        queueEndGame(
                murdererWon
                        ? Component.literal("PEMBUNUH MENANG!").withStyle(ChatFormatting.DARK_RED)
                        : Component.literal("TIM PENYELIDIK MENANG!").withStyle(ChatFormatting.GREEN),
                Component.literal(message).withStyle(murdererWon ? ChatFormatting.RED : ChatFormatting.GREEN),
                murdererWon);
    }

    /** Fallback: ronde 3 abis tanpa ada confession yang berhasil -- penjahat langsung menang. */
    public void onRoundsExhausted(MinecraftServer server) {
        queueEndGame(Component.literal("PEMBUNUH MENANG!").withStyle(ChatFormatting.DARK_RED),
                Component.literal("Ronde 3 berakhir tanpa ada yang berhasil menyelesaikan kasus. Pembunuh menang.").withStyle(ChatFormatting.RED), true);
    }

    /** /deception skipfs -- testing, biar gak perlu naro paper beneran buat mulai diskusi. */
    public boolean forceStartDiscussion(MinecraftServer server) {
        if (GameManager.get().getState() != GameManager.State.DISCUSS || discussionStarted) return false;
        UUID fsUuid = null;
        for (var e : GameManager.get().getRoleAssignments().entrySet()) {
            if (e.getValue() == Role.forensic_scientist) {
                fsUuid = e.getKey();
                break;
            }
        }
        if (fsUuid == null) return false;
        ServerPlayer fsPlayer = server.getPlayerList().getPlayer(fsUuid);
        if (fsPlayer == null) return false;
        onForensicPaperPlaced(fsPlayer, server);
        return true;
    }

    // ---------- Fase PRESENTASI ----------

    public void startPresentasi(MinecraftServer server, List<UUID> order) {
        presentasiOrder = order;
        presentasiIndex = 0;
        presentasiTicksLeft = presentasiTurnTicks;

        Component title = Component.literal("Presentasi Ronde " + round + " dimulai").withStyle(ChatFormatting.GREEN);
        GameManager.get().broadcast(server, Component.literal(""));
        GameManager.get().broadcast(server, Component.literal("  PRESENTASI DIMULAI").withStyle(ChatFormatting.GREEN));
        GameManager.get().broadcast(server, Component.literal("").withStyle(ChatFormatting.WHITE));
        GameManager.get().broadcast(server, Component.literal("  Jelaskan apa yang kamu dapat dari diskusi sebelumnya, \n  selama presentasi tidak ada yang boleh menyela.").withStyle(ChatFormatting.WHITE));
        for (UUID uuid : GameManager.get().getRegisteredPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }

        if (!presentasiOrder.isEmpty()) {
            broadcastSpeakerActionBar();
        }
    }

    /** @return true kalo semua giliran udah kelar (GameManager lanjut ke ronde berikutnya atau RUNNING). */
    public boolean tickPresentasi(MinecraftServer server) {
        if (presentasiOrder.isEmpty()) return true;

        presentasiTicksLeft--;
        if (presentasiTicksLeft % 20 == 0) {
            broadcastSpeakerActionBar();
        }
        if (presentasiTicksLeft <= 0) {
            presentasiIndex++;
            if (presentasiIndex >= presentasiOrder.size()) {
                return true;
            }
            presentasiTicksLeft = presentasiTurnTicks;
            broadcastSpeakerActionBar();
        }
        return false;
    }

    private void broadcastSpeakerActionBar() {
        UUID speaker = getCurrentSpeaker();
        if (speaker == null) return;
        String name = GameManager.get().getPlayerName(speaker);
        int secondsLeft = (int) Math.ceil(presentasiTicksLeft / 20.0);
        Component actionBar = Component.literal("Giliran " + name + " berbicara | " + secondsLeft + "s")
                .withStyle(ChatFormatting.YELLOW);
        GameManager.get().broadcastActionBarToAll(actionBar);
    }

    /** Dibaca DeceptionVoicechatPlugin buat nentuin siapa yang bolang ngomong. Null = gak lagi PRESENTASI/gak ada giliran. */
    public UUID getCurrentSpeaker() {
        if (presentasiOrder.isEmpty() || presentasiIndex >= presentasiOrder.size()) return null;
        return presentasiOrder.get(presentasiIndex);
    }

    // ---------- Ronde 2 & 3 (clue tambahan + hapus tile) ----------

    /** Dipanggil GameManager#giveForensicScientistPapers abis milih 4 kategori random ronde 1. */
    public void recordGivenCategories(List<ForensicPaperData.Category> categories) {
        for (ForensicPaperData.Category category : categories) {
            givenCategories.add(category.displayName());
        }
    }

    /**
     * Dipanggil GameManager pas presentasi 1 ronde kelar. @return true kalo
     * masih ada ronde berikutnya (GameManager balikin state ke DISCUSS),
     * false kalo udah ronde terakhir (GameManager lanjut ke RUNNING).
     */
    public boolean advanceRound(MinecraftServer server) {
        round++;
        if (round > MAX_ROUNDS) return false;

        discussionStarted = false;
        skipVotes.clear();
        roundTileRemoved = false;
        giveRoundPaperAndRemover(server);
        return true;
    }

    private void giveRoundPaperAndRemover(MinecraftServer server) {
        UUID fsUuid = null;
        for (var e : GameManager.get().getRoleAssignments().entrySet()) {
            if (e.getValue() == Role.forensic_scientist) {
                fsUuid = e.getKey();
                break;
            }
        }
        if (fsUuid == null) return;
        ServerPlayer fsPlayer = server.getPlayerList().getPlayer(fsUuid);
        if (fsPlayer == null) return;

        List<ForensicPaperData.Category> pool = new ArrayList<>(ForensicPaperData.SCENE_TILES);
        pool.removeIf(c -> givenCategories.contains(c.displayName()));
        if (pool.isEmpty()) return;
        Collections.shuffle(pool);
        ForensicPaperData.Category chosen = pool.get(0);
        givenCategories.add(chosen.displayName());

        ItemStack paper = new ItemStack(ModItems.INVESTIGATION_PAPER.get());
        CompoundTag paperTag = paper.getOrCreateTag();
        paperTag.putString("ForensicCategory", chosen.displayName());
        // Paper ronde 2/3 gak boleh ditempel sebelum FS hapus 1 tile lama --
        // ini DISIMPEN DI NBT STACK-NYA (bukan di field PresentationManager)
        // soalnya InvestigationPaperItem#place() jalan di CLIENT DAN SERVER
        // buat prediksi; field server-only bakal beda nilai di client (selalu
        // dianggap "gak lagi ke-lock"), bikin client salah predict berhasil
        // taro padahal server nolak -- itemnya keliatan ilang dari hotbar tapi
        // gak balik lagi. NBT ikut ke-sync ke client jadi konsisten.
        paperTag.putBoolean(PLACEMENT_LOCKED_TAG, true);
        ListTag canPlaceOn = new ListTag();
        canPlaceOn.add(StringTag.valueOf("minecraft:black_wool"));
        paperTag.put("CanPlaceOn", canPlaceOn);
        paper.setHoverName(Component.literal(chosen.displayName()));
        fsPlayer.getInventory().add(paper);

        ItemStack remover = new ItemStack(Items.FLINT_AND_STEEL);
        remover.getOrCreateTag().putBoolean(TILE_REMOVER_TAG, true);
        remover.setHoverName(Component.literal("Penghapus Scene Tile").withStyle(ChatFormatting.RED));
        fsPlayer.getInventory().add(remover);
    }

    /**
     * Perkakas jatah Forensic Scientist yang gak boleh lepas dari tangannya:
     * investigation paper & penghapus scene tile. Dipake DeceptionMod#onItemToss
     * buat nolak drop -- kalo kebuang, ronde itu macet total soalnya gak ada
     * cara lain buat ngasih paper/penghapus pengganti.
     *
     * <p>Penghapusnya dicek lewat NBT, BUKAN cuma tipe item -- flint & steel
     * biasa punya siapa pun harus tetep bisa dibuang.
     */
    public boolean isForensicTool(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() == ModItems.INVESTIGATION_PAPER.get() || isTileRemover(stack));
    }

    private boolean isTileRemover(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.FLINT_AND_STEEL
                && stack.getTag() != null && stack.getTag().getBoolean(TILE_REMOVER_TAG);
    }

    /**
     * Dipanggil DeceptionMod#onRightClickBlock. @return true kalo item di
     * tangan ke-detect sebagai tile-remover ini (caller HARUS cancel event-nya
     * apapun hasilnya, biar gak pernah bisa dipake nyalain api beneran) --
     * false kalo item lain, biarin event jalan normal.
     */
    public boolean tryRemoveTile(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack heldItem) {
        if (!isTileRemover(heldItem)) return false;

        if (round <= 1 || roundTileRemoved) {
            player.sendSystemMessage(Component.literal("Tidak ada scene tile yang perlu dihapus sekarang.").withStyle(ChatFormatting.RED));
            return true;
        }

        if (!level.getBlockState(pos).is(ModBlocks.INVESTIGATION_PAPER.get())
                || !(level.getBlockEntity(pos) instanceof InvestigationPaperBlockEntity paperEntity)
                || paperEntity.getCategory().isEmpty()) {
            player.sendSystemMessage(Component.literal("Hanya bisa dipakai ke scene tile yang sudah ditempel.").withStyle(ChatFormatting.RED));
            return true;
        }

        String category = paperEntity.getCategory();
        if (category.equals(ForensicPaperData.CAUSE_OF_DEATH.displayName())
                || category.equals(ForensicPaperData.LOCATION_OF_CRIME.displayName())) {
            player.sendSystemMessage(Component.literal("Penyebab kematian & lokasi kejadian tidak bisa dihapus.").withStyle(ChatFormatting.RED));
            return true;
        }

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        heldItem.shrink(1);
        roundTileRemoved = true;
        unlockPendingPaper(player);
        player.sendSystemMessage(Component.literal("Scene tile \"" + category + "\" telah dihapus. Sekarang taruh petunjuk baru untuk investigator.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    /**
     * Cabut NBT PLACEMENT_LOCKED_TAG dari paper ronde ini yang masih di
     * inventory FS, biar bisa ditempel.
     *
     * <p>Yang dikunci CUMA paper jatah ronde ini (lihat
     * giveRoundPaperAndRemover) -- sisa paper ronde 1 yang gak jadi ditempel
     * sengaja dibiarin bebas. Kalo semuanya ikut dikunci, FS bisa mentok
     * total: penyebab kematian & lokasi kejadian gak boleh dihapus (lihat
     * tryRemoveTile), jadi kalo cuma itu yang kepasang, gak ada tile yang
     * bisa dihapus buat mbuka kuncinya.
     */
    private void unlockPendingPaper(ServerPlayer fsPlayer) {
        for (ItemStack stack : fsPlayer.getInventory().items) {
            if (stack.getItem() == ModItems.INVESTIGATION_PAPER.get()
                    && stack.getTag() != null && stack.getTag().getBoolean(PLACEMENT_LOCKED_TAG)) {
                stack.getTag().remove(PLACEMENT_LOCKED_TAG);
                break;
            }
        }
    }
}
