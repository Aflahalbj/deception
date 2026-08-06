package com.deception.command;

import com.deception.game.GameManager;
import com.deception.game.Role;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

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
                                        ctx.getSource().sendFailure(Component.literal("Player " + name + " tidak ditemukan (online/bot)."));
                                    }
                                    return 1;
                                })))

                .then(literal("regisall")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            int count = GameManager.get().registerAll(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal(count + " player berhasil di-regis semua.").withStyle(ChatFormatting.GREEN), true);
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
                        .then(literal("witness")
                                .then(literal("add").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.witness, true)))
                                .then(literal("remove").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.witness, false))))
                        .then(literal("accomplice")
                                .then(literal("add").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.accomplice, true)))
                                .then(literal("remove").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.accomplice, false)))))

                .then(literal("roleinfo")
                        .requires(src -> src.hasPermission(2))
                        .then(argument("namarole", StringArgumentType.word())
                                .suggests(ROLE_NAMES)
                                .executes(ctx -> {
                                    String roleName = StringArgumentType.getString(ctx, "namarole");
                                    Role role = Role.fromString(roleName);
                                    if (role == null) {
                                        ctx.getSource().sendFailure(Component.literal("Role tidak dikenal: " + roleName));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(RoleDescriptions.get(role)).withStyle(ChatFormatting.AQUA), false);
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
                            ctx.getSource().sendSuccess(() -> Component.literal("Player teregistrasi (" + lines.size() + "):").withStyle(ChatFormatting.AQUA), false);
                            for (String line : lines) {
                                ctx.getSource().sendSuccess(() -> Component.literal("- " + line), false);
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
                                ctx.getSource().sendFailure(Component.literal("Gagal skip: bukan lagi fase night, atau gagal random-in item murderer."));
                                return 0;
                            }
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

    private static int toggleCustomRole(CommandSourceStack source, Role role, boolean add) {
        GameManager.get().setCustomRole(role, add);
        source.sendSuccess(() -> Component.literal((add ? "Menambahkan " : "Menghapus ") + role.getDisplayName() + " dari komposisi.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}