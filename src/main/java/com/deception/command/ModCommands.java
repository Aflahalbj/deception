package com.deception.command;

import com.deception.game.ArenaDimension;
import com.deception.game.GameManager;
import com.deception.game.PresentationManager;
import com.deception.game.Role;
import com.deception.game.VoiceDebugState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Semua command mod ini didaftarin di bawah 1 root: /deception <sub...>
 * Brigadier otomatis kasih tab-completion buat tiap literal/argument node,
 * jadi ketik "/deception " lalu TAB bakal nampilin semua subcommand di bawah.
 */
public class ModCommands {

    // suggestion provider: nama semua player teregistrasi (buat tab /deception unregis)
    private static final SuggestionProvider<CommandSourceStack> REGISTERED_PLAYERS = (ctx, builder) -> {
        Set<String> names = GameManager.get().getRegisteredPlayers().stream()
                .map(u -> GameManager.get().getPlayerName(u))
                .collect(Collectors.toSet());
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> ROLE_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Role.values()).map(Role::name), builder);

    private static final SuggestionProvider<CommandSourceStack> HELP_COMMANDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CommandDescriptions.names(), builder);

    private static final SuggestionProvider<CommandSourceStack> SETFS_OPTIONS = (ctx, builder) -> {
        List<String> options = new ArrayList<>();
        options.add("random");
        ctx.getSource().getServer().getPlayerList().getPlayers()
                .forEach(p -> options.add(p.getGameProfile().getName()));
        return SharedSuggestionProvider.suggest(options, builder);
    };

    // OP (permission level 2) ATAU player yang lagi jadi Forensic Scientist di game yang berjalan
    private static boolean isOpOrForensicScientist(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return false;
        return GameManager.get().getRoleAssignments().get(player.getUUID()) == Role.forensic_scientist;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(literal("deception")

                .then(literal("regis")
                        .requires(src -> src.hasPermission(2))
                        .then(argument("playername", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "playername");
                                    boolean ok = GameManager.get().registerPlayer(ctx.getSource().getServer(), name);
                                    if (ok) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(name + " berhasil di-regis.").withStyle(ChatFormatting.GREEN), true);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("Player " + name + " tidak ditemukan."));
                                    }
                                    return 1;
                                })))

                .then(literal("regisall")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            int count = GameManager.get().registerAll(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal(count + " player berhasil di-regis.").withStyle(ChatFormatting.GREEN), true);
                            return count;
                        }))

                .then(literal("unregis")
                        .requires(src -> src.hasPermission(2))
                        .then(argument("playername", StringArgumentType.word())
                                .suggests(REGISTERED_PLAYERS)
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "playername");
                                    boolean ok = GameManager.get().unregisterPlayer(name);
                                    if (ok) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(name + " berhasil di-unregis.").withStyle(ChatFormatting.YELLOW), true);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal(name + " tidak terdaftar."));
                                    }
                                    return 1;
                                })))

                .then(literal("unregisall")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            int count = GameManager.get().unregisterAll();
                            ctx.getSource().sendSuccess(() -> Component.literal(count + " player berhasil di-unregis semua.").withStyle(ChatFormatting.YELLOW), true);
                            return count;
                        }))

                .then(literal("customrole")
                        .requires(src -> src.hasPermission(2))
                        .then(customRoleNode("witness", Role.witness))
                        .then(customRoleNode("accomplice", Role.accomplice))
                        .then(customRoleNode("protective_detail", Role.protective_detail))
                        .then(customRoleNode("lab_technician", Role.lab_technician))
                        .then(customRoleNode("inside_man", Role.inside_man)))

                .then(literal("roleinfo")
                        .then(argument("namarole", StringArgumentType.word())
                                .suggests(ROLE_NAMES)
                                .executes(ctx -> {
                                    String roleName = StringArgumentType.getString(ctx, "namarole");
                                    Role role = Role.fromString(roleName);
                                    if (role == null) {
                                        ctx.getSource().sendFailure(Component.literal("Role tidak dikenal: " + roleName));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("\n" + RoleDescriptions.get(role)).withStyle(ChatFormatting.YELLOW), false);
                                    return 1;
                                })))

                .then(literal("debugrole")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            Map<UUID, Role> roles = GameManager.get().getRoleAssignments();
                            if (roles.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Belum ada role yang dibagikan."), false);
                                return 0;
                            }
                            for (Map.Entry<UUID, Role> e : roles.entrySet()) {
                                String name = GameManager.get().getPlayerName(e.getKey());
                                ctx.getSource().sendSuccess(() -> Component.literal(name + " -> " + e.getValue().getDisplayName()), false);
                            }
                            return roles.size();
                        }))

                .then(literal("setfs")
                        .requires(src -> src.hasPermission(2))
                        .then(argument("target", StringArgumentType.word())
                                .suggests(SETFS_OPTIONS)
                                .executes(ctx -> {
                                    String target = StringArgumentType.getString(ctx, "target");
                                    GameManager.get().setForensicScientistMode(target);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Forensic Scientist diset ke: " + target).withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))

                .then(literal("settimer")
                        .requires(src -> src.hasPermission(2))
                        .then(literal("discuss")
                                .then(argument("menit", IntegerArgumentType.integer(1, 120))
                                        .executes(ctx -> {
                                            int menit = IntegerArgumentType.getInteger(ctx, "menit");
                                            GameManager.get().setDiscussTimerSeconds(menit * 60);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Timer diskusi diset ke " + menit + " menit.").withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal("Timer diskusi default: 10 menit.").withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                }))
                        .then(literal("presentation")
                                .then(argument("detik", IntegerArgumentType.integer(5, 300))
                                        .executes(ctx -> {
                                            int detik = IntegerArgumentType.getInteger(ctx, "detik");
                                            PresentationManager.get().setPresentasiTurnSeconds(detik);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Timer presentasi diset ke " + detik + " detik per giliran.").withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal("Timer presentasi default: 30 detik per giliran.").withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))

                // Di dalam register method, setelah literal("settimer") atau di mana aja
                .then(literal("setrole")
                        .requires(src -> src.hasPermission(2))
                        .then(argument("playername", StringArgumentType.word())
                                .suggests(REGISTERED_PLAYERS)
                                .then(argument("role", StringArgumentType.word())
                                        .suggests(ROLE_NAMES)
                                        .executes(ctx -> {
                                            String playerName = StringArgumentType.getString(ctx, "playername");
                                            String roleName = StringArgumentType.getString(ctx, "role");
                                            
                                            Role role = Role.fromString(roleName);
                                            if (role == null) {
                                                ctx.getSource().sendFailure(Component.literal("Role tidak dikenal: " + roleName));
                                                return 0;
                                            }
                                            
                                            boolean ok = GameManager.get().setPlayerRole(playerName, role);
                                            if (ok) {
                                                ctx.getSource().sendSuccess(() -> 
                                                    Component.literal(playerName + " di-set menjadi " + role.getDisplayName())
                                                    .withStyle(ChatFormatting.GREEN), true);
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal("Player " + playerName + " tidak terdaftar."));
                                            }
                                            return 1;
                                        }))))

                // GUI info role -- SENGAJA tanpa .requires(), semua player boleh
                // buka. Isinya penjelasan statis, gak nunjukin role siapa pun.
                .then(literal("roleinfo")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.literal("Command ini hanya bisa dijalankan oleh player."));
                                return 0;
                            }
                            com.deception.network.ModNetworking.sendOpenRoleInfo(player);
                            return 1;
                        }))

                // GUI setting -- semua isinya juga tetap bisa lewat subcommand
                // (settimer/customrole/setrole), GUI cuma pembungkusnya.
                .then(literal("setting")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.literal("Command ini hanya bisa dijalankan oleh player."));
                                return 0;
                            }
                            com.deception.network.ModNetworking.sendOpenSetting(player);
                            return 1;
                        }))

                .then(literal("startgame")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            GameManager.StartResult result = GameManager.get().startGame(ctx.getSource().getServer());
                            switch (result) {
                                case OK:
                                    return 1;
                                case ALREADY_RUNNING:
                                    ctx.getSource().sendFailure(Component.literal("Gagal start: game sedang berjalan."));
                                    return 0;
                                case INVALID_PLAYER_COUNT:
                                    int count = GameManager.get().getRegisteredPlayers().size();
                                    ctx.getSource().sendFailure(Component.literal(
                                            "Gagal start: jumlah player harus 4-12 (sekarang " + count + ")."));
                                    return 0;
                                case PLAYER_OFFLINE:
                                    List<String> offline = GameManager.get().getOfflineRegisteredPlayerNames(ctx.getSource().getServer());
                                    ctx.getSource().sendFailure(Component.literal(
                                            "Gagal start: ada player teregistrasi yang offline: " + String.join(", ", offline)));
                                    return 0;
                                default:
                                    return 0;
                            }
                        }))

                .then(literal("listplayer")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            List<String> lines = GameManager.get().getRegisteredPlayerStatusLines(ctx.getSource().getServer());
                            if (lines.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Belum ada player yang di-regis."), false);
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.GOLD), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("===== Player teregistrasi (" + lines.size() + ") =====").withStyle(ChatFormatting.GOLD), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.GOLD), false);
                            for (String line : lines) {
                                ctx.getSource().sendSuccess(() -> Component.literal("  - " + line).withStyle(ChatFormatting.WHITE), false);
                            }
                            return lines.size();
                        }))

                .then(literal("stopgame")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            GameManager.get().stopGame(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Game dihentikan.").withStyle(ChatFormatting.RED), true);
                            return 1;
                        }))

                // Khusus OP atau Forensic Scientist yang lagi main -- skip fase
                // reveal malam ini. Kalo murderer belum sempet milih means/clue,
                // itemnya di-random-in dulu.
                .then(literal("skipreveal")
                        .requires(ModCommands::isOpOrForensicScientist)
                        .executes(ctx -> {
                            boolean ok = GameManager.get().skipReveal();
                            if (ok) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Reveal malam ini di-skip.").withStyle(ChatFormatting.GREEN), true);
                                return 1;
                            } else {
                                ctx.getSource().sendFailure(Component.literal("Gagal skip: bukan fase night."));
                                return 0;
                            }
                        }))
                // Skip fase yang lagi jalan -- semua player teregistrasi bisa
                // manggil, gak perlu OP. Aturannya beda per fase (lihat
                // PresentationManager#skip):
                //   DISCUSS    -> toggle vote, dilewatin kalo mayoritas ONLINE setuju
                //   PRESENTASI -> langsung dilewatin, tapi cuma boleh sama yang
                //                 lagi dapet giliran, FS, atau OP
                .then(literal("skip")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.literal("Command ini hanya bisa dijalankan oleh player."));
                                return 0;
                            }
                            PresentationManager.SkipResult result =
                                    PresentationManager.get().skip(player, ctx.getSource().getServer());
                            switch (result) {
                                case DISCUSS_VOTED, PRESENTASI_SKIPPED -> {
                                    return 1;
                                }
                                case NO_PERMISSION -> {
                                    ctx.getSource().sendFailure(Component.literal(
                                            "Hanya yang sedang presentasi, forensic scientist, atau admin yang bisa skip presentasi."));
                                    return 0;
                                }
                                default -> {
                                    ctx.getSource().sendFailure(Component.literal("Tidak ada diskusi atau presentasi yang bisa di-skip."));
                                    return 0;
                                }
                            }
                        }))

                // Testing: paksa mulai diskusi tanpa perlu FS beneran naro
                // paper (lihat PresentationManager#forceStartDiscussion).
                .then(literal("skipfs")
                        .requires(ModCommands::isOpOrForensicScientist)
                        .executes(ctx -> {
                            boolean ok = PresentationManager.get().forceStartDiscussion(ctx.getSource().getServer());
                            if (ok) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Diskusi dipaksa mulai.").withStyle(ChatFormatting.GREEN), true);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal("Gagal skip: tidak sedang menunggu FS, atau diskusi sudah dimulai."));
                            return 0;
                        }))

                // FS jawab [BENAR]/[SALAH] pas ada yang confession (klik
                // kanan police_badge) -- lihat PresentationManager#resolveConfession.
                .then(literal("true")
                        .requires(ModCommands::isOpOrForensicScientist)
                        .executes(ctx -> answerAsForensicScientist(ctx.getSource(), true)))
                .then(literal("false")
                        .requires(ModCommands::isOpOrForensicScientist)
                        .executes(ctx -> answerAsForensicScientist(ctx.getSource(), false)))

                // Inside Man nunjuk target tanpa harus klik kanan orangnya --
                // cadangan kalo orangnya lagi gak keliatan. Dipicu tombol nama
                // di chat, lihat GameManager#sendInsideManTargetList.
                .then(literal("target")
                        .then(argument("playername", StringArgumentType.word())
                                .suggests(REGISTERED_PLAYERS)
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                                        ctx.getSource().sendFailure(Component.literal("Command ini hanya bisa dijalankan oleh player."));
                                        return 0;
                                    }
                                    String name = StringArgumentType.getString(ctx, "playername");
                                    if (!GameManager.get().onInsideManPickByName(player, name)) {
                                        ctx.getSource().sendFailure(Component.literal("Sekarang bukan giliran kamu memilih target."));
                                        return 0;
                                    }
                                    return 1;
                                })))
                // Bangun arena dari structure NBT yang dibundel di jar --
                // lihat ArenaDimension buat alur lengkapnya.
                .then(literal("generatemap")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> generateMap(ctx.getSource())))

                // Lompat ke dimensi arena -- dipake pas mau bangun/ngedit
                // arenanya, soalnya dimensi custom gak bisa dituju /tp biasa.
                .then(literal("gotoarena")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.literal("Command ini hanya bisa dijalankan oleh player."));
                                return 0;
                            }
                            if (!GameManager.teleportToArenaLobby(ctx.getSource().getServer(), player)) {
                                ctx.getSource().sendFailure(Component.literal(
                                        "Dimensi arena tidak terdaftar. Cek data/deception/dimension/arena.json."));
                                return 0;
                            }
                            return 1;
                        }))

                // Jalan pulang dari dimensi arena. Pasangan /deception gotoarena
                // -- sama-sama perlu command sendiri soalnya dimensi custom gak
                // bisa dituju /tp biasa, jadi tanpa ini gampang nyangkut di
                // arena kalau masuk ke sana di luar alur game.
                .then(literal("gotoworld")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.literal("Command ini hanya bisa dijalankan oleh player."));
                                return 0;
                            }
                            ServerLevel overworld = ctx.getSource().getServer().overworld();
                            BlockPos spawn = overworld.getSharedSpawnPos();
                            player.teleportTo(overworld,
                                    spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                                    overworld.getSharedSpawnAngle(), 0.0F);
                            ctx.getSource().sendSuccess(() -> Component.literal("Kamu dipindah ke spawn overworld.")
                                    .withStyle(ChatFormatting.GREEN), false);
                            return 1;
                        }))

                // Debug voice chat -- lihat VoiceDebugState.
                .then(literal("voicedebug")
                        .requires(src -> src.hasPermission(2))
                        .then(literal("on").executes(ctx -> setVoiceDebug(ctx.getSource(), true)))
                        .then(literal("off").executes(ctx -> setVoiceDebug(ctx.getSource(), false)))
                        .executes(ctx -> {
                            boolean on = VoiceDebugState.isEnabled();
                            ctx.getSource().sendSuccess(() -> Component.literal("Voice debug sekarang: " + (on ? "ON" : "OFF"))
                                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
                            if (on) {
                                List<String> speaking = VoiceDebugState.activeSpeakers(ctx.getSource().getServer());
                                ctx.getSource().sendSuccess(() -> Component.literal("  Lagi kirim mic: "
                                                + (speaking.isEmpty() ? "-" : String.join(", ", speaking)))
                                        .withStyle(ChatFormatting.GRAY), false);
                            }
                            return 1;
                        }))

                // Tampilin role sendiri di pojok kanan atas layar tiap peserta.
                .then(literal("rolevisible")
                        .requires(src -> src.hasPermission(2))
                        .then(literal("on").executes(ctx -> setRoleVisible(ctx.getSource(), true)))
                        .then(literal("off").executes(ctx -> setRoleVisible(ctx.getSource(), false)))
                        .executes(ctx -> {
                            boolean on = GameManager.get().isRoleVisible();
                            ctx.getSource().sendSuccess(() -> Component.literal("Role visible sekarang: " + (on ? "ON" : "OFF"))
                                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
                            return 1;
                        }))

                // Bantuan command. Teksnya di CommandDescriptions.
                .then(literal("help")
                        .requires(src -> src.hasPermission(2))
                        .then(argument("command", StringArgumentType.word())
                                .suggests(HELP_COMMANDS)
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "command");
                                    CommandDescriptions.Entry entry = CommandDescriptions.get(name);
                                    if (entry == null) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "Command tidak dikenal: " + name + ". Ketik /deception help buat lihat daftarnya."));
                                        return 0;
                                    }
                                    sendHelpDetail(ctx.getSource(), name.toLowerCase(), entry);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            sendHelpList(ctx.getSource());
                            return CommandDescriptions.names().size();
                        }))

                        // Di dalam register method, setelah literal("skipreveal") atau di bagian manapun
                .then(literal("confirm")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.literal("Command ini hanya bisa dijalankan oleh player."));
                                return 0;
                            }
                                    
                            boolean ok = GameManager.get().onConfirmCommand(player);
                            if (ok) {
                                // Success message sudah dikirim dari GameManager
                                return 1;
                            } else {
                                ctx.getSource().sendFailure(Component.literal("Tidak ada yang perlu dikonfirmasi saat ini."));
                                return 0;
                            }
                        }))
                );
    }

    /** /deception help -- daftar semua command, tiap barisnya bisa diklik buat lihat detailnya. */
    private static void sendHelpList(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("===== Command Deception =====").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(""), false);

        for (Map.Entry<String, CommandDescriptions.Entry> e : CommandDescriptions.all()) {
            String name = e.getKey();
            source.sendSuccess(() -> Component.literal("  " + e.getValue().usage())
                    .withStyle(style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/deception help " + name))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Klik buat penjelasan lengkap")))), false);
        }

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("  Ketik /deception help <command> buat penjelasan lengkapnya.")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(""), false);
    }

    /** /deception help <command> -- sintaks, izin, penjelasan panjang. */
    private static void sendHelpDetail(CommandSourceStack source, String name, CommandDescriptions.Entry entry) {
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("===== /deception " + name + " =====").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("  Pemakaian: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(entry.usage()).withStyle(ChatFormatting.YELLOW)), false);
        source.sendSuccess(() -> Component.literal("  Izin: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(entry.permission()).withStyle(ChatFormatting.AQUA)), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("  " + entry.detail()).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(""), false);
    }

    /**
     * Pasang ULANG world save arena dari jar, nimpa yang sekarang. Yang
     * pemasangan otomatis pas server nyala ada di
     * DeceptionMod#onServerAboutToStart -- command ini buat maksa nimpa,
     * misal arenanya kadung keubah-ubah.
     */
    private static int generateMap(CommandSourceStack source) {
        MinecraftServer server = source.getServer();

        if (!ArenaDimension.isBundled()) {
            source.sendFailure(Component.literal(
                    "Belum ada world arena yang dibundel di mod ini. Lihat /deception help generatemap "
                            + "untuk cara menaruhnya."));
            return 0;
        }

        try {
            if (!ArenaDimension.install(server, true)) {
                source.sendFailure(Component.literal("Gagal memasang arena."));
                return 0;
            }
        } catch (java.io.IOException e) {
            source.sendFailure(Component.literal("Gagal memasang arena: " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Arena dipasang ulang ke world save.")
                .withStyle(ChatFormatting.GREEN), true);
        // Chunk arena yang TELANJUR ke-load masih versi lama di memori, dan
        // pas server nge-save nanti dia bakal nimpa balik file yang barusan
        // ditulis. Restart itu satu-satunya cara yang pasti bersih.
        source.sendSuccess(() -> Component.literal(
                        "  Restart server sekarang supaya arenanya benar-benar kebaca.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int setVoiceDebug(CommandSourceStack source, boolean enabled) {
        VoiceDebugState.setEnabled(enabled);
        source.sendSuccess(() -> Component.literal("Voice debug: " + (enabled ? "ON" : "OFF")
                        + (enabled ? " -- tiap mic yang nyampe server dilaporin ke OP." : ""))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int setRoleVisible(CommandSourceStack source, boolean visible) {
        GameManager.get().setRoleVisible(source.getServer(), visible);
        source.sendSuccess(() -> Component.literal("Role visible: " + (visible ? "ON" : "OFF")
                        + (visible ? " -- role masing-masing muncul di pojok kanan atas layar." : ""))
                .withStyle(visible ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return 1;
    }

    /**
     * /deception true &amp; /deception false. SATU tombol dipake dua fase yang
     * beda, jadi urutannya penting: pertanyaan Lab Technician (fase Allies)
     * dicoba DULUAN, baru jatuh ke jawaban confession police badge. Dua-duanya
     * gak mungkin nunggu barengan -- fase Allies mustahil ada confession yang
     * lagi ngegantung (lihat PresentationManager#tryStartConfession).
     */
    private static int answerAsForensicScientist(CommandSourceStack source, boolean answer) {
        if (GameManager.get().answerLabTechnician(answer)) {
            source.sendSuccess(() -> Component.literal("Jawaban dikirim ke Lab Technician.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        if (PresentationManager.get().resolveConfession(answer, source.getServer())) {
            return 1;
        }
        source.sendFailure(Component.literal("Tidak ada pertanyaan yang sedang menunggu jawaban."));
        return 0;
    }

    /** Satu cabang /deception customrole &lt;role&gt; &lt;add|remove&gt;. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> customRoleNode(
            String literalName, Role role) {
        return literal(literalName)
                .then(literal("add").executes(ctx -> toggleCustomRole(ctx.getSource(), role, true)))
                .then(literal("remove").executes(ctx -> toggleCustomRole(ctx.getSource(), role, false)));
    }

    private static int toggleCustomRole(CommandSourceStack source, Role role, boolean add) {
        GameManager.get().setCustomRole(role, add);
        source.sendSuccess(() -> Component.literal((add ? "Menambahkan " : "Menghapus ") + role.getDisplayName() + " dari komposisi.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}