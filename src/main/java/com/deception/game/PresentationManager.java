package com.deception.game;

import com.deception.block.InvestigationPaperBlockEntity;
import com.deception.init.ModBlocks;
import com.deception.init.ModItems;
import com.deception.menu.WitnessGuessMenu;
import com.deception.network.ModNetworking;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkHooks;

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

    // Tebakan penyelidik BENAR tapi ada witness -- murderer dikasih 1
    // kesempatan terakhir "membungkam" witness pake panah sebelum kemenangan
    // penyelidik final (lihat startWitnessShootout).
    private boolean shootoutActive = false;
    private UUID shootoutMurdererUuid = null;
    private UUID shootoutWitnessUuid = null;
    private int shootoutDotTicks = 0;

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

    public void reset() {
        discussionStarted = false;
        // Beritahu client badge-nya udah dicabut (lihat javadoc
        // PoliceBadgeClientState) -- kalo cuma di-clear() di sini doang,
        // icon di sebelah nametag nyangkut keliatan terus soalnya client
        // gak pernah dikasih tau balik ke false.
        for (UUID uuid : badgeHolders) {
            ModNetworking.broadcastPoliceBadgeHolder(uuid, false);
        }
        badgeHolders.clear();
        skipVotes.clear();
        givenCategories.clear();
        round = 1;
        roundTileRemoved = true;
        presentasiOrder = List.of();
        presentasiIndex = 0;
        presentasiTicksLeft = 0;
        pendingConfessorUuid = null;
        confessionTitleDelayTicks = 0;
        shootoutActive = false;
        shootoutMurdererUuid = null;
        shootoutWitnessUuid = null;
        shootoutDotTicks = 0;
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
        for (UUID uuid : GameManager.get().getRegisteredPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            player.connection.send(new ClientboundSetTitleTextPacket(title));

            if (GameManager.get().getRoleAssignments().get(uuid) != Role.forensic_scientist) {
                giveAndLockBadge(player);
            }
        }
    }

    // ---------- Vote skip diskusi ----------

    /** @return false kalo dipanggil di luar fase diskusi yang lagi berjalan (command bakal kasih tau gagal). */
    public boolean voteSkip(ServerPlayer player, MinecraftServer server) {
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

        // Selagi ada confession yang lagi nunggu keputusan FS ATAU shootout
        // lagi jalan, diskusi/presentasi DI-PAUSE (lihat GameManager#tick,
        // dicek lewat isBusy()) -- actionbar-nya jadi tanggung jawab kita
        // sepenuhnya di sini, biar gak rebutan sama actionbar diskusi/
        // presentasi yang jalan bareng.
        if (isBusy()) {
            shootoutDotTicks++;
            if (shootoutDotTicks % 10 == 0) {
                int dotCount = (int) ((shootoutDotTicks / 10) % 4);
                String base = shootoutActive ? "Menunggu murderer menebak witness" : "Menunggu keputusan forensic scientist";
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
        if (pendingConfessorUuid != null) {
            player.sendSystemMessage(Component.literal("Masih ada yang lagi nunggu keputusan FS, tunggu dulu.").withStyle(ChatFormatting.RED));
            return true;
        }

        pendingConfessorUuid = player.getUUID();
        confessionTitleDelayTicks = CONFESSION_TITLE_DELAY_TICKS;

        // Full animasi pop totem vanilla asli (kilat layar + partikel +
        // suara + popup ikon aktivasi) -- item-nya beneran Totem of Undying
        // (di-retexture jadi badge), jadi popup-nya JUGA ikut nunjukin
        // texture badge, bukan totem vanilla.
        ((ServerLevel) player.level()).broadcastEntityEvent(player, (byte) 35);
        return true;
    }

    private void announceConfession(MinecraftServer server) {
        if (pendingConfessorUuid == null) return;
        String name = GameManager.get().getPlayerName(pendingConfessorUuid);

        Component title = Component.literal(name + " ingin menyelesaikan kasus").withStyle(ChatFormatting.GOLD);
        for (UUID uuid : GameManager.get().getRegisteredPlayers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
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
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Klik buat jawab"))));
    }

    /** Dipanggil /deception true (correct=true) atau /deception false (correct=false). @return false kalo gak ada confession yang pending. */
    public boolean resolveConfession(boolean correct, MinecraftServer server) {
        if (pendingConfessorUuid == null) return false;

        UUID confessorUuid = pendingConfessorUuid;
        pendingConfessorUuid = null;
        ServerPlayer confessor = server.getPlayerList().getPlayer(confessorUuid);
        if (confessor != null) {
            clearBadge(confessor);
        } else {
            badgeHolders.remove(confessorUuid);
            ModNetworking.broadcastPoliceBadgeHolder(confessorUuid, false);
        }

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
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("SALAH").withStyle(ChatFormatting.DARK_RED)));
            }
        }

        if (badgeHolders.isEmpty()) {
            queueEndGame(Component.literal("PENJAHAT MENANG!").withStyle(ChatFormatting.DARK_RED),
                    Component.literal("Semua police badge udah abis dipake, gak ada yang berhasil nebak. Penjahat menang.").withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    // ---------- Shootout murderer vs witness (tebak lewat UI chest) ----------

    private static final String WITNESS_GUESS_BOW_TAG = "DeceptionWitnessGuessBow";

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
                    Component.literal("Murderer offline, gak bisa nebak. Tim penyelidik menang.").withStyle(ChatFormatting.GREEN), false);
            return;
        }

        shootoutActive = true;
        shootoutMurdererUuid = murdererUuid;
        shootoutWitnessUuid = witnessUuid;
        shootoutDotTicks = 0;

        // Bow TANPA panah -- gak bisa dipake nembak beneran (vanilla nolak
        // narik busur kalo gak ada panah), cuma dipake sebagai "pemicu"
        // buat buka UI tebak witness pas diklik kanan (lihat
        // DeceptionMod#onRightClickItem & tryOpenWitnessGuessMenu).
        ItemStack bow = new ItemStack(Items.BOW);
        bow.getOrCreateTag().putBoolean(WITNESS_GUESS_BOW_TAG, true);
        bow.setHoverName(Component.literal("Tebak Witness").withStyle(ChatFormatting.DARK_RED));
        murderer.getInventory().add(bow);
    }

    public boolean isWitnessGuessBow(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.BOW
                && stack.getTag() != null && stack.getTag().getBoolean(WITNESS_GUESS_BOW_TAG);
    }

    /** Dipanggil DeceptionMod pas murderer klik kanan bow "tebak witness"-nya. */
    public boolean tryOpenWitnessGuessMenu(ServerPlayer player, MinecraftServer server) {
        if (!shootoutActive || !player.getUUID().equals(shootoutMurdererUuid)) return false;

        List<UUID> slotOrder = new ArrayList<>();
        SimpleContainer container = new SimpleContainer(18);
        int slot = 0;
        for (UUID uuid : GameManager.get().getRegisteredPlayers()) {
            if (GameManager.get().getRoleAssignments().get(uuid) == Role.forensic_scientist) continue;
            if (slot >= container.getContainerSize()) break;

            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            GameProfile profile = online != null ? online.getGameProfile() : new GameProfile(uuid, GameManager.get().getPlayerName(uuid));
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.getOrCreateTag().put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), profile));
            head.setHoverName(Component.literal(GameManager.get().getPlayerName(uuid)));

            container.setItem(slot, head);
            slotOrder.add(uuid);
            slot++;
        }

        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new WitnessGuessMenu(windowId, inv, container, slotOrder),
                Component.literal("Tebak Witness")));
        return true;
    }

    /** Dipanggil WitnessGuessMenu pas murderer klik salah satu head. */
    public void onWitnessGuessClicked(ServerPlayer murderer, UUID guessedUuid) {
        if (!shootoutActive || !murderer.getUUID().equals(shootoutMurdererUuid)) return;

        shootoutActive = false;
        murderer.closeContainer();
        boolean guessedWitness = guessedUuid.equals(shootoutWitnessUuid);

        if (guessedWitness) {
            queueEndGame(Component.literal("PENJAHAT MENANG!").withStyle(ChatFormatting.DARK_RED),
                    Component.literal("Murderer berhasil nebak witness. Penjahat menang.").withStyle(ChatFormatting.RED), true);
        } else {
            queueEndGame(Component.literal("TIM PENYELIDIK MENANG!").withStyle(ChatFormatting.GREEN),
                    Component.literal("Murderer salah nebak witness. Tim penyelidik menang.").withStyle(ChatFormatting.GREEN), false);
        }
    }

    /** Fallback: ronde 3 abis tanpa ada confession yang berhasil -- penjahat langsung menang. */
    public void onRoundsExhausted(MinecraftServer server) {
        queueEndGame(Component.literal("PENJAHAT MENANG!").withStyle(ChatFormatting.DARK_RED),
                Component.literal("Ronde 3 berakhir tanpa ada yang berhasil menyelesaikan kasus. Penjahat menang.").withStyle(ChatFormatting.RED), true);
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
                .withStyle(ChatFormatting.AQUA);
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
            player.sendSystemMessage(Component.literal("Gak ada scene tile yang perlu dihapus sekarang.").withStyle(ChatFormatting.RED));
            return true;
        }

        if (!level.getBlockState(pos).is(ModBlocks.INVESTIGATION_PAPER.get())
                || !(level.getBlockEntity(pos) instanceof InvestigationPaperBlockEntity paperEntity)
                || paperEntity.getCategory().isEmpty()) {
            player.sendSystemMessage(Component.literal("Cuma bisa dipake ke scene tile yang udah ditempel.").withStyle(ChatFormatting.RED));
            return true;
        }

        String category = paperEntity.getCategory();
        if (category.equals(ForensicPaperData.CAUSE_OF_DEATH.displayName())
                || category.equals(ForensicPaperData.LOCATION_OF_CRIME.displayName())) {
            player.sendSystemMessage(Component.literal("Penyebab kematian & lokasi kejadian gak bisa dihapus.").withStyle(ChatFormatting.RED));
            return true;
        }

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        heldItem.shrink(1);
        roundTileRemoved = true;
        unlockPendingPaper(player);
        player.sendSystemMessage(Component.literal("Scene tile \"" + category + "\" dihapus. Sekarang naro clue baru kamu.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    /** Cabut NBT PLACEMENT_LOCKED_TAG dari paper ronde ini yang masih di inventory FS, biar bisa ditempel. */
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
