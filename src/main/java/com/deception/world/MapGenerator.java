package com.deception.world;

import com.deception.DeceptionMod;
import com.deception.game.GameManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Teleport semua player yang terdaftar ke dimensi arena (deception:arena).
 *
 * Map arena sekarang berasal dari world save folder yang sudah lo siapin —
 * caranya: copy folder "region" (dan "poi"/"entities" kalau ada) dari world
 * save lo ke folder world server di:
 *   <world_server>/dimensions/deception/arena/region/
 * Chunk yang udah ada di region file itu bakal langsung kepake (gak di-generate
 * ulang), chunk generator di dimension json cuma dipakai buat area yang belum
 * pernah lo bangun.
 *
 * PENTING: ganti ARENA_SPAWN di bawah sesuai koordinat spawn yang lo mau di
 * map lo (koordinat X/Y/Z pas world lo bikin map itu, bukan spawn dunia server).
 */
public class MapGenerator {

    // TODO: ganti sesuai titik spawn yang lo mau di map lo
    private static final BlockPos ARENA_SPAWN = new BlockPos(0, 100, 0);

    public static final ResourceKey<Level> ARENA_LEVEL = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            new ResourceLocation(DeceptionMod.MOD_ID, "arena"));

    public static void generate(CommandSourceStack source) {
        var server = source.getServer();
        ServerLevel arenaLevel = server.getLevel(ARENA_LEVEL);
        if (arenaLevel == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "Dimensi arena tidak ditemukan — pastikan world di-restart setelah mod di-install (dimension baru cuma dibaca saat world dibuat)."));
            return;
        }

        BlockPos spawnPos = ARENA_SPAWN;

        for (UUID uuid : GameManager.get().getRegisteredPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.teleportTo(arenaLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
            }
        }

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Map arena siap, semua player terdaftar dipindahkan."), true);
    }
}
