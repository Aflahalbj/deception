package com.deception.command;

import com.deception.entity.ClueEntity;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(literal("deception")
                .requires(src -> src.hasPermission(2))

                .then(literal("regis")
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
                        .executes(ctx -> {
                            int count = GameManager.get().registerAll(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal(count + " player berhasil di-regis semua.").withStyle(ChatFormatting.GREEN), true);
                            return count;
                        }))

                .then(literal("unregis")
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
                        .executes(ctx -> {
                            int count = GameManager.get().unregisterAll();
                            ctx.getSource().sendSuccess(() -> Component.literal(count + " player berhasil di-unregis semua.").withStyle(ChatFormatting.YELLOW), true);
                            return count;
                        }))

                .then(literal("customrole")
                        .then(literal("witness")
                                .then(literal("add").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.WITNESS, true)))
                                .then(literal("remove").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.WITNESS, false))))
                        .then(literal("accomplice")
                                .then(literal("add").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.ACCOMPLICE, true)))
                                .then(literal("remove").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.ACCOMPLICE, false)))))

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
                                    ctx.getSource().sendSuccess(() -> Component.literal(RoleDescriptions.get(role)).withStyle(ChatFormatting.AQUA), false);
                                    return 1;
                                })))

                .then(literal("debugrole")
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
                        .then(argument("target", StringArgumentType.word())
                                .suggests(SETFS_OPTIONS)
                                .executes(ctx -> {
                                    String target = StringArgumentType.getString(ctx, "target");
                                    GameManager.get().setForensicScientistMode(target);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Forensic Scientist diset ke: " + target).withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))

                .then(literal("settimer")
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

                .then(literal("startgame")
                        .executes(ctx -> {
                            boolean ok = GameManager.get().startGame(ctx.getSource().getServer());
                            if (!ok) {
                                ctx.getSource().sendFailure(Component.literal("Gagal start (game sudah jalan / jumlah pemain harus 4-12)."));
                                return 0;
                            }
                            return 1;
                        }))

                .then(literal("stopgame")
                        .executes(ctx -> {
                            GameManager.get().stopGame();
                            ctx.getSource().sendSuccess(() -> Component.literal("Game dihentikan.").withStyle(ChatFormatting.RED), true);
                            return 1;
                        }))

                .then(literal("resizeitem")
                        .then(argument("persen", IntegerArgumentType.integer(1, 500))
                                .executes(ctx -> {
                                    int persen = IntegerArgumentType.getInteger(ctx, "persen");
                                    float scale = persen / 100.0F;
                                    int count = 0;
                                    // area gede yang nyakup seluruh build limit (gak ada AABB.INFINITE di 1.20.1)
                                    AABB wholeWorld = new AABB(-3.0E7, -64, -3.0E7, 3.0E7, 320, 3.0E7);
                                    for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                                        for (ClueEntity clue : level.getEntitiesOfClass(ClueEntity.class, wholeWorld, e -> true)) {
                                            clue.setClueScale(scale);
                                            count++;
                                        }
                                    }
                                    int finalCount = count;
                                    if (finalCount == 0) {
                                        ctx.getSource().sendFailure(Component.literal("Gak ada clue/means yang ke-detect di map."));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(finalCount + " clue/means di-resize jadi " + persen + "%.").withStyle(ChatFormatting.GREEN), true);
                                    return finalCount;
                                })))

                // Note: glow sekarang bisa dipasang langsung pake vanilla /effect, contoh:
                //   /effect give @e[type=deception:clue_entity] minecraft:glowing infinite 1 true
                // gak butuh command custom lagi karena ClueEntity udah jadi LivingEntity.
                );
    }

    private static int toggleCustomRole(CommandSourceStack source, Role role, boolean add) {
        GameManager.get().setCustomRole(role, add);
        source.sendSuccess(() -> Component.literal((add ? "Menambahkan " : "Menghapus ") + role.getDisplayName() + " dari komposisi.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}