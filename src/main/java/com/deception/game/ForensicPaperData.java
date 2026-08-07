package com.deception.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data statis buat kategori investigation paper yang dikasih ke Forensic
 * Scientist pas reveal selesai (2 kategori tetap + 4 kategori acak dari
 * scene tiles). Hardcoded (bukan JSON/datapack) biar konsisten sama pola
 * ModBlocks.CLUE_IDS yang udah ada di mod ini.
 */
public final class ForensicPaperData {

    public record Category(String displayName, List<String> options) {}

    public static final Category CAUSE_OF_DEATH = new Category("Penyebab Kematian", List.of(
            "Tikaman", "Tembakan", "Cekikan", "Keracunan", "Pukulan", "Tenggelam", "Terbakar", "Jatuh"));

    public static final Category LOCATION_OF_CRIME = new Category("Lokasi Kejadian", List.of(
            "Kamar Tidur", "Dapur", "Kantor", "Jalanan", "Kapal", "Hotel", "Gudang", "Taman"));

    public static final List<Category> SCENE_TILES = List.of(
            new Category("Cuaca", List.of("Cerah", "Hujan", "Berkabut", "Berangin", "Mendung", "Badai")),
            new Category("Waktu", List.of("Pagi", "Siang", "Sore", "Malam", "Subuh", "Tengah Malam")),
            new Category("Ekspresi", List.of("Terkejut", "Takut", "Tenang", "Marah", "Sakit", "Bingung")),
            new Category("Jumlah Pelaku", List.of("Sendirian", "Berdua", "Bertiga", "Berempat", "Kelompok", "Tidak Jelas")),
            new Category("Ruangan", List.of("Loteng", "Ruang Bawah Tanah", "Kamar Mandi", "Ruang Tamu", "Garasi", "Ruang Cuci")),
            new Category("Pencahayaan", List.of("Terang", "Redup", "Gelap", "Berkedip", "Senter", "Neon")),
            new Category("Suara", List.of("Teriakan", "Debuman", "Ledakan", "Sunyi", "Musik", "Langkah")),
            new Category("Bau", List.of("Amis", "Busuk", "Alkohol", "Besi Berkarat", "Asap", "Bensin")),
            new Category("Posisi Tubuh", List.of("Terlentang", "Tengkurap", "Duduk", "Berdiri", "Meringkuk", "Tergantung")),
            new Category("Jenis Luka", List.of("Tusukan", "Bengkak", "Bakar", "Luka Gores", "Patah", "Sayatan")),
            new Category("Pakaian", List.of("Rapi", "Robek", "Basah", "Berdarah", "Kotor", "Terbuka")),
            new Category("Hubungan", List.of("Keluarga", "Teman", "Rekan", "Kekasih", "Musuh", "Asing")),
            new Category("Motif", List.of("Uang", "Cinta", "Dendam", "Cemburu", "Rahasia", "Kekuasaan")),
            new Category("Suasana Lokasi", List.of("Ramai", "Sepi", "Terpencil", "Perkotaan", "Pedesaan", "Bawah Tanah")),
            new Category("Transportasi", List.of("Mobil", "Motor", "Taksi", "Kereta", "Perahu", "Jalan Kaki")),
            new Category("Kondisi Barang Bukti", List.of("Hilang", "Rusak", "Tersembunyi", "Dipindah", "Ditemukan", "Palsu")),
            new Category("Kondisi Ruangan", List.of("Berantakan", "Rapi", "Terkunci", "Terbuka", "Gelap", "Dingin")),
            new Category("Waktu Kematian", List.of("Baru Saja", "Lama", "Beberapa Jam", "Semalam", "Kemarin", "Tidak Pasti")),
            new Category("Saksi", List.of("Tidak Ada", "Tetangga", "Satpam", "Pejalan Kaki", "Anak Kecil", "Pengantar Paket")),
            new Category("Reaksi Tubuh", List.of("Kejang", "Pucat", "Membiru", "Kaku", "Berkeringat", "Muntah")),
            new Category("Kondisi Dalam Ruangan", List.of("AC Menyala", "Kipas Mati", "Tirai Tertutup", "Pintu Terkunci", "Lampu Mati", "Bau Menyengat")),
            new Category("Suhu", List.of("Panas", "Dingin", "Sejuk", "Lembap", "Kering", "Beku")),
            new Category("Warna Dominan", List.of("Merah", "Hitam", "Putih", "Biru", "Kuning", "Hijau")),
            new Category("Kondisi Luar", List.of("Genangan Air", "Daun Berguguran", "Salju Tipis", "Kabut Tebal", "Angin Kencang", "Petir")),
            new Category("Sikap Pelaku", List.of("Tenang", "Gugup", "Terburu", "Santai", "Waspada", "Panik")),
            new Category("Kondisi Bukti", List.of("Kabur", "Jelas", "Terhapus", "Tersembunyi", "Rusak", "Utuh")),
            new Category("Perilaku Pelaku", List.of("Menyamar", "Berlari", "Bersembunyi", "Mengintai", "Merencanakan", "Melarikan Diri")),
            new Category("Arah Datang", List.of("Dari Depan", "Dari Belakang", "Dari Atas", "Dari Bawah", "Dari Samping", "Tidak Diketahui")),
            new Category("Kecepatan Kejadian", List.of("Cepat", "Lambat", "Mendadak", "Terencana", "Spontan", "Bertahap")),
            new Category("Kondisi Korban", List.of("Sadar", "Pingsan", "Melawan", "Diam", "Terkejut", "Panik")),
            new Category("Bukti Tambahan", List.of("Surat Wasiat", "Rekaman Suara", "Catatan Tangan", "Pesan Teks", "Email", "Nota")),
            new Category("Skala Kejadian", List.of("Kecil", "Sedang", "Besar", "Tersembunyi", "Terbuka", "Direncanakan")));

    private static final Map<String, Category> BY_DISPLAY_NAME = new HashMap<>();

    static {
        BY_DISPLAY_NAME.put(CAUSE_OF_DEATH.displayName(), CAUSE_OF_DEATH);
        BY_DISPLAY_NAME.put(LOCATION_OF_CRIME.displayName(), LOCATION_OF_CRIME);
        for (Category category : SCENE_TILES) {
            BY_DISPLAY_NAME.put(category.displayName(), category);
        }
    }

    public static Category findByDisplayName(String displayName) {
        return BY_DISPLAY_NAME.get(displayName);
    }

    private ForensicPaperData() {}
}
