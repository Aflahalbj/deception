package com.deception.command;

import com.deception.entity.BotManager;
import com.deception.entity.ClueEntity;
import com.deception.game.GameManager;
import com.deception.game.Role;
import com.deception.world.MapGenerator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ModCommands {

    // suggestion provider: nama semua player teregistrasi (buat /unregis tab)
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

        dispatcher.register(literal("regis")
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
                        })));

        dispatcher.register(literal("regisall")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    int count = GameManager.get().registerAll(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(count + " player berhasil di-regis semua.").withStyle(ChatFormatting.GREEN), true);
                    return count;
                }));

        dispatcher.register(literal("unregis")
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
                        })));

        dispatcher.register(literal("unregisall")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    int count = GameManager.get().unregisterAll();
                    ctx.getSource().sendSuccess(() -> Component.literal(count + " player berhasil di-unregis semua.").withStyle(ChatFormatting.YELLOW), true);
                    return count;
                }));

        dispatcher.register(literal("generatemap")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Generating map...").withStyle(ChatFormatting.AQUA), true);
                    MapGenerator.generate(ctx.getSource());
                    return 1;
                }));

        dispatcher.register(literal("customrole")
                .requires(src -> src.hasPermission(2))
                .then(literal("witness")
                        .then(literal("add").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.WITNESS, true)))
                        .then(literal("remove").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.WITNESS, false))))
                .then(literal("accomplice")
                        .then(literal("add").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.ACCOMPLICE, true)))
                        .then(literal("remove").executes(ctx -> toggleCustomRole(ctx.getSource(), Role.ACCOMPLICE, false)))));

        dispatcher.register(literal("roleinfo")
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
                        })));

        dispatcher.register(literal("debugrole")
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
                }));

        dispatcher.register(literal("setfs")
                .requires(src -> src.hasPermission(2))
                .then(argument("target", StringArgumentType.word())
                        .suggests(SETFS_OPTIONS)
                        .executes(ctx -> {
                            String target = StringArgumentType.getString(ctx, "target");
                            GameManager.get().setForensicScientistMode(target);
                            ctx.getSource().sendSuccess(() -> Component.literal("Forensic Scientist diset ke: " + target).withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        })));

        dispatcher.register(literal("settimer")
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
                        })));

        dispatcher.register(literal("startgame")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    boolean ok = GameManager.get().startGame(ctx.getSource().getServer());
                    if (!ok) {
                        ctx.getSource().sendFailure(Component.literal("Gagal start (game sudah jalan / jumlah pemain harus 4-12)."));
                        return 0;
                    }
                    return 1;
                }));

        dispatcher.register(literal("stopgame")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    GameManager.get().stopGame();
                    ctx.getSource().sendSuccess(() -> Component.literal("Game dihentikan.").withStyle(ChatFormatting.RED), true);
                    return 1;
                }));

        dispatcher.register(literal("resizeclue")
                .requires(src -> src.hasPermission(2))
                .then(argument("persen", IntegerArgumentType.integer(1, 500))
                        .executes(ctx -> {
                            int persen = IntegerArgumentType.getInteger(ctx, "persen");
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Entity target = getLookedAtClue(player);
                            if (!(target instanceof ClueEntity clue)) {
                                ctx.getSource().sendFailure(Component.literal("Gak ada clue entity yang lagi lo liat (jarak maks 6 blok)."));
                                return 0;
                            }
                            clue.setClueScale(persen / 100.0F);
                            ctx.getSource().sendSuccess(() -> Component.literal("Clue di-resize jadi " + persen + "%.").withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        })));

        // command tambahan buat manage bot testing (offline-friendly)
        dispatcher.register(literal("deceptionbot")
                .requires(src -> src.hasPermission(2))
                .then(literal("spawn")
                        .then(argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    ServerPlayer bot = BotManager.get().spawnBot(ctx.getSource().getLevel(), name);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Bot '" + bot.getGameProfile().getName() + "' spawned.").withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                .then(literal("despawn")
                        .then(argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    boolean ok = BotManager.get().despawnBot(name);
                                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Bot dihapus." : "Bot tidak ditemukan."), true);
                                    return 1;
                                })))
                .then(literal("despawnall")
                        .executes(ctx -> {
                            BotManager.get().despawnAll();
                            ctx.getSource().sendSuccess(() -> Component.literal("Semua bot dihapus."), true);
                            return 1;
                        })));
    }

    private static Entity getLookedAtClue(ServerPlayer player) {
        double reach = 6.0;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 reachPoint = eye.add(look.scale(reach));
        AABB searchBox = player.getBoundingBox().inflate(reach);

        Entity closest = null;
        double closestDist = reach;
        for (Entity e : player.level().getEntities(player, searchBox, ent -> ent instanceof ClueEntity)) {
            AABB entityBox = e.getBoundingBox().inflate(0.3);
            var clip = entityBox.clip(eye, reachPoint);
            if (clip.isPresent()) {
                double dist = eye.distanceTo(clip.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }

    private static int toggleCustomRole(CommandSourceStack source, Role role, boolean add) {
        GameManager.get().setCustomRole(role, add);
        source.sendSuccess(() -> Component.literal((add ? "Menambahkan " : "Menghapus ") + role.getDisplayName() + " dari komposisi.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
