package com.deception.client.gui;

import com.deception.game.Role;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/**
 * Isi GUI /deception inforole: foto + penjelasan lengkap tiap role.
 *
 * <p>Sengaja TERPISAH dari {@code RoleDescriptions} (yang dipake
 * /deception roleinfo di chat). Yang di sana satu kalimat buat ditempel di
 * chat; yang di sini penjelasan panjang buat dibaca di layar, ditulis ngikutin
 * mekanik yang beneran jalan di GameManager/PresentationManager -- bukan
 * deskripsi umum board game-nya.
 *
 * <p>Ukuran gambar dicatat manual karena Minecraft gak nyediain cara baca
 * dimensi texture yang murah pas render. Kalau gambarnya diganti dengan
 * ukuran beda, angka di sini HARUS ikut diubah, kalau enggak fotonya gepeng.
 */
public final class RoleInfo {

    private RoleInfo() {}

    public record Entry(ResourceLocation image, int imageWidth, int imageHeight, String description) {

        /** Rasio lebar : tinggi, buat aspect-fit ke kotak foto. */
        public float aspect() {
            return imageWidth / (float) imageHeight;
        }
    }

    private static final Map<Role, Entry> ENTRIES = new EnumMap<>(Role.class);

    private static ResourceLocation image(String name) {
        return new ResourceLocation("deception", "textures/gui/" + name + ".png");
    }

    static {
        ENTRIES.put(Role.forensic_scientist, new Entry(
                image("forensic_scientist"), 981, 652,
                """
                Kamu satu-satunya yang tahu kebenaran. Di malam hari kamu \
                melihat siapa Murderer, siapa Accomplice, siapa Witness, plus \
                means (alat yang dipakai) dan clue (barang bukti) \
                yang dipilih Murderer.

                Kamu tidak boleh berbicara. Satu-satunya \
                alat komunikasimu adalah scene tile yang didapat secara acak \
                seperti: (Cuaca, Ruangan, Motif, dll), beri petunjuk dengan tepat \
                lalu tempel di board. kamu mendapatkan 6 scene tile di ronde pertama, \
                ronde berikutnya kamu harus mengganti salah satu scene tile \
                yang aktif dengan yang baru.

                Saat ada yang menuduh menggunakan Police Badge, kamu yang memutuskan \
                [BENAR] atau [SALAH]. Kamu menang bersama tim investigator."""));

        ENTRIES.put(Role.murderer, new Entry(
                image("murderer"), 612, 408,
                """
                Kamu pelakunya. Di malam hari kamu memilih dua benda di TKP. \
                means: alat yang kamu pakai, dan clue: bukti yang kamu \
                tinggalkan.

                Sisanya kamu bermain sebagai orang biasa. Tugasmu: menyesatkan, \
                ikut menganalisis, ikut menuduh, dan pastikan tidak ada yang \
                berhasil menghubungkan scene tile Forensic Scientist ke dua \
                benda pilihanmu. Kamu juga dapat Police Badge, jadi kamu bisa \
                menuduh palsu untuk membakar kesempatan tim investigator.

                Jika ada yang menebak benar sementara Witness ikut bermain, \
                kamu masih punya satu kesempatan terakhir, tembak siapa Witness. \
                Jika benar, kamu tetap menang."""));

        ENTRIES.put(Role.accomplice, new Entry(
                image("accomplice"), 1050, 700,
                """
                Kamu tahu siapa Murderer, dan kamu tahu means serta clue yang \
                dia pilih. Tapi kamu tidak punya aksi apa pun di malam hari.

                Kekuatanmu ada di diskusi. Dari luar kamu terlihat persis seperti \
                Investigator, jadi kamu bisa memimpin analisis ke arah yang \
                salah, membela Murderer tanpa terlihat membelanya, atau sengaja \
                menuduh keliru pakai Police Badge supaya jatah tebakan tim \
                investigator habis.

                Saat Witness membuka mata di malam hari, kamu ikut \
                terlihat. Witness tidak bisa membedakan kamu dan Murderer, tapi \
                dia melihat namamu. Kamu menang bersama Murderer."""));

        ENTRIES.put(Role.witness, new Entry(
                image("witness"), 627, 350,
                """
                Kamu ada di TKP malam itu. Sesaat kamu membuka mata dan melihat \
                siapa saja yang terlibat, yaitu Murderer dan Accomplice. Kamu \
                dapat nama mereka, tapi tidak tahu siapa murderer yang asli, dan kamu \
                tidak tahu apa pun soal means maupun clue.

                Masalahnya, begitu kamu bicara terlalu terang-terangan, kamu \
                jadi sasaran. Jika tim investigator berhasil menuduh dengan benar, \
                Murderer masih dapat satu kesempatan terakhir untuk menembak siapa \
                Witness. Jika kamu tertembak, tim murderer yang menang.

                Jadi arahkan penyelidikan tanpa pernah membuka identitasmu."""));

        ENTRIES.put(Role.investigator, new Entry(
                image("investigator"), 612, 408,
                """
                Baca scene tile yang dipasang Forensic Scientist. Tiap tile berisi \
                satu kategori dengan satu jawaban, dan FS memilihnya untuk \
                menunjuk ke means dan clue yang dipakai Murderer. Cocokkan dengan \
                benda-benda yang ada di TKP, lalu adu pendapat saat diskusi.

                Kamu punya Police Badge, jatahnya sekali seumur permainan. Klik \
                kanan untuk mengajukan kesimpulanmu, lalu Forensic Scientist \
                menjawab [BENAR] atau [SALAH]. Jika benar, tim investigator menang. \
                Jika salah, badge-mu hangus.

                Jika semua badge habis tanpa ada satu pun tebakan yang benar, \
                tim murderer yang menang."""));
    }

    public static Entry get(Role role) {
        return ENTRIES.get(role);
    }
}
