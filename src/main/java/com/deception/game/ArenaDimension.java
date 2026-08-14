package com.deception.game;

import com.deception.DeceptionMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Dimensi khusus tempat arena berdiri, plus pemasangan world save-nya dari
 * dalem jar.
 *
 * <p>Kenapa dimensi sendiri, bukan numpang overworld: arena ini world save
 * JADI yang ikut dibundel di mod. Kalo ditaro di overworld, dia bakal nimpa
 * apapun yang kebetulan ada di koordinat itu di dunia orang. Dimensi sendiri
 * bikin arena gak pernah nyenggol dunia pemain sama sekali.
 *
 * <p>Isi dimensinya SENGAJA void (lihat data/deception/dimension/arena.json):
 * gak ada terrain yang di-generate, jadi yang keliatan cuma arena yang kita
 * pasang.
 *
 * <h3>Cara naro world-nya (sekali doang, pas development)</h3>
 * <ol>
 *   <li>Bangun arenanya di dimensi ini -- masuknya lewat
 *       {@code /execute in deception:arena run tp @s 119 -59 179}.
 *   <li>Stop server, biar chunk-nya beneran ke-tulis ke disk.
 *   <li>Copy isi {@code run/saves/<dunia>/dimensions/deception/arena/}
 *       (folder {@code region/}, plus {@code entities/} & {@code data/} kalo
 *       ada) ke {@code src/main/resources/map/}.
 *   <li>Build ulang. Mulai sekarang mod-nya bawa arena sendiri.
 * </ol>
 */
public final class ArenaDimension {

    private ArenaDimension() {}

    public static final ResourceKey<Level> ARENA = ResourceKey.create(
            Registries.DIMENSION, new ResourceLocation(DeceptionMod.MOD_ID, "arena"));

    /** Folder di dalem jar tempat world save arena dibundel. */
    private static final String BUNDLED_MAP_DIR = "map";

    /**
     * Level tempat arena berdiri. Jatuh balik ke overworld kalo dimensinya
     * gagal ke-load -- lebih baik game-nya jalan di tempat yang salah
     * daripada NPE di mana-mana.
     */
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel arena = server.getLevel(ARENA);
        return arena != null ? arena : server.overworld();
    }

    /** Dimensi arenanya beneran kedaftar apa kagak. */
    public static boolean isAvailable(MinecraftServer server) {
        return server.getLevel(ARENA) != null;
    }

    /** Folder dimensi arena di dalem world save yang lagi kebuka. */
    public static Path saveDirectory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions")
                .resolve(DeceptionMod.MOD_ID)
                .resolve("arena");
    }

    /** Arena udah kepasang di world save ini belum. */
    public static boolean isInstalled(MinecraftServer server) {
        return Files.isDirectory(saveDirectory(server).resolve("region"));
    }

    /** Ada gak world save arena yang dibundel di jar-nya. */
    public static boolean isBundled() {
        Path source = bundledMapPath();
        return source != null && Files.isDirectory(source);
    }

    /**
     * Salin world save arena dari jar ke world save yang lagi kebuka.
     *
     * <p>HARUS dipanggil sebelum dimensinya kepake (lihat
     * DeceptionMod#onServerAboutToStart). Kalo disalin pas chunk-nya udah
     * ke-load, Minecraft masih megang versi lamanya di memori dan bakal
     * nimpa balik file yang barusan kita tulis pas dia nge-save.
     *
     * @param overwrite true = tetep salin walaupun udah kepasang
     * @return true kalo ada yang disalin
     */
    public static boolean install(MinecraftServer server, boolean overwrite) throws IOException {
        Path source = bundledMapPath();
        if (source == null || !Files.isDirectory(source)) return false;

        Path target = saveDirectory(server);
        if (Files.exists(target)) {
            if (!overwrite) return false;
            deleteRecursively(target);
        }
        Files.createDirectories(target);

        // Jalan di FileSystem-nya jar, jadi Path-nya beda provider sama
        // target -- relativize/resolve harus lewat String, gak bisa
        // target.resolve(jarPath) langsung.
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                String relative = source.relativize(path).toString().replace('\\', '/');
                Path destination = relative.isEmpty() ? target : target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return true;
    }

    /** Path folder {@code map/} di dalem jar mod, null kalo mod file-nya gak ketemu. */
    private static Path bundledMapPath() {
        var modFile = ModList.get().getModFileById(DeceptionMod.MOD_ID);
        if (modFile == null) return null;
        return modFile.getFile().findResource(BUNDLED_MAP_DIR);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
