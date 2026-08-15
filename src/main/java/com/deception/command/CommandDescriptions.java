package com.deception.command;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Teks bantuan tiap subcommand buat /deception help (lihat ModCommands).
 * Dipisah dari ModCommands biar file command-nya gak makin panjang, pola
 * sama kayak RoleDescriptions.
 *
 * LinkedHashMap, bukan HashMap: urutannya sengaja dijaga biar /deception
 * help nampilin command-nya urut sesuai alur main (registrasi -> setting ->
 * mulai -> fase jalan), bukan acak.
 */
public class CommandDescriptions {

    /**
     * @param usage      sintaks lengkap, termasuk argumennya
     * @param permission siapa yang boleh manggil
     * @param detail     penjelasan panjang, ditampilin di /deception help <command>
     */
    public record Entry(String usage, String permission, String detail) {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private static void put(String name, String usage, String permission, String detail) {
        ENTRIES.put(name, new Entry(usage, permission, detail));
    }

    static {
        // ---------- Registrasi peserta ----------
        put("regis", "/deception regis <playername>", "OP",
                "Daftarin satu player jadi peserta game. Player-nya harus lagi ONLINE. "
                        + "Cuma yang teregistrasi yang bakal dapet role pas /deception startgame.");
        put("regisall", "/deception regisall", "OP",
                "Daftarin SEMUA player yang lagi online sekaligus. Jalan pintas dari /deception regis satu-satu.");
        put("unregis", "/deception unregis <playername>", "OP",
                "Keluarin satu player dari daftar peserta. Tab-completion-nya cuma nampilin yang emang udah teregistrasi.");
        put("unregisall", "/deception unregisall", "OP",
                "Kosongin seluruh daftar peserta sekaligus.");
        put("listplayer", "/deception listplayer", "OP",
                "Tampilin semua peserta yang udah teregistrasi, lengkap sama status online/offline-nya. "
                        + "Berguna dicek sebelum startgame -- game gak bakal mau mulai kalo ada peserta yang offline.");

        // ---------- Setting sebelum main ----------
        put("setting", "/deception setting", "OP",
                "Buka GUI setting. Isinya sama persis kayak subcommand settimer/customrole/setrole, "
                        + "GUI-nya cuma pembungkus biar gak perlu ngapal command.");
        put("customrole",
                "/deception customrole <witness|accomplice|protective_detail|lab_technician|inside_man> <add|remove>", "OP",
                "Paksa role opsional buat MASUK atau GAK MASUK ke komposisi, nimpa aturan otomatis "
                        + "yang normalnya nentuin dari jumlah pemain. Default otomatisnya: accomplice mulai 6 "
                        + "pemain, witness mulai 7, lab_technician mulai 7, inside_man mulai 8. "
                        + "protective_detail default MATI di semua jumlah pemain (rulebook resminya gak "
                        + "ngasih angka, cuma bilang opsional) dan cuma jalan kalau witness aktif.");
        put("setrole", "/deception setrole <playername> <role>", "OP",
                "Kunci satu player ke role tertentu. Sisanya tetep diacak seperti biasa. "
                        + "Dipake buat ngetes, atau kalo emang mau ngatur pembagian.");
        put("setfs", "/deception setfs <playername|random>", "OP",
                "Tentuin siapa yang jadi Forensic Scientist. Isi \"random\" buat balikin ke acak.");
        put("settimer", "/deception settimer <discuss <menit> | presentation <detik>>", "OP",
                "Atur durasi fase. \"discuss\" satuannya MENIT (1-120, default 10) buat satu ronde diskusi; "
                        + "\"presentation\" satuannya DETIK (5-300, default 30) buat jatah bicara PER ORANG. "
                        + "Dipanggil tanpa angka = cuma nampilin nilai default-nya.");

        // ---------- Arena ----------
        put("generatemap", "/deception generatemap", "OP",
                "Pasang ULANG world arena dari dalam jar, nimpa yang sekarang -- dipakai kalau arenanya "
                        + "kadung keubah dan mau dibalikin. Normalnya gak perlu dipanggil: arenanya "
                        + "dipasang OTOMATIS pas server nyala kalau world save-nya belum punya. "
                        + "Harus restart server setelahnya. Arena tinggal di dimensi sendiri "
                        + "(deception:arena), jadi gak pernah nimpa dunia pemain.");
        put("gotoarena", "/deception gotoarena", "OP",
                "Lompat ke dimensi arena. Perlu command sendiri soalnya dimensi custom gak bisa "
                        + "dituju /tp biasa. Dipakai waktu mau bangun atau ngedit arenanya.");
        put("gotoworld", "/deception gotoworld", "OP",
                "Jalan pulang dari arena: pindahin kamu ke titik spawn overworld. Pasangan "
                        + "/deception gotoarena, dipakai kalau nyangkut di dimensi arena di luar alur game "
                        + "(kalau lewat alur normal, /deception stopgame udah otomatis mulangin semua peserta "
                        + "ke tempat mereka sebelum ditarik ke arena).");

        // ---------- Kontrol game ----------
        put("startgame", "/deception startgame", "OP",
                "Mulai game. Syaratnya: peserta 4-12 orang dan SEMUANYA lagi online. "
                        + "Kalo gagal, alasannya dikasih tau di chat.");
        put("stopgame", "/deception stopgame", "OP",
                "Hentiin game paksa di tengah jalan. Arena dibersihin, gamemode balik survival, "
                        + "inventory & efek peserta dibersihin.");

        // ---------- Dipake selagi game jalan ----------
        put("skip", "/deception skip", "Semua player",
                "Skip fase yang lagi jalan. Pas DISKUSI: ini vote (bisa di-toggle), dan diskusi baru "
                        + "dilewatin kalo SEMUA peserta online yang bukan Forensic Scientist udah vote -- "
                        + "satu orang yang belum setuju cukup buat nahan. FS gak ikut hitungan sama sekali. "
                        + "Pas PRESENTASI: langsung dilewatin tanpa vote, tapi cuma boleh sama yang lagi dapet "
                        + "giliran bicara, Forensic Scientist, atau OP.");
        put("skipreveal", "/deception skipreveal", "OP atau Forensic Scientist",
                "Majuin SATU tahap fase malam (night) atau fase Allies yang lagi jalan. Kalo murderer "
                        + "belum sempet milih means & clue, pilihannya diacak otomatis dulu biar game tetep "
                        + "bisa lanjut; giliran witness / protective detail / lab technician / inside man "
                        + "yang di-skip cuma dilewatin, gak diacak.");
        put("skipfs", "/deception skipfs", "OP atau Forensic Scientist",
                "Paksa diskusi mulai tanpa nunggu Forensic Scientist nempel investigation paper beneran. "
                        + "Buat testing.");
        put("confirm", "/deception confirm", "Semua player",
                "Konfirmasi pilihan kamu di fase malam atau fase Allies -- means & clue buat murderer, "
                        + "\"udah liat\" buat witness & protective detail, item yang mau ditanyain buat lab "
                        + "technician, atau target badge buat inside man. Biasanya diklik dari tombol di chat, "
                        + "command ini cadangan kalo tombolnya kelewat.");
        put("target", "/deception target <playername>", "Inside Man",
                "Pilih orang yang badge-nya bakal dicabut, tanpa harus klik kanan orangnya. Cadangan "
                        + "buat kasus targetnya lagi gak keliatan dari tempat kamu berdiri. Cuma jalan pas "
                        + "giliran Inside Man di fase Allies.");
        put("true", "/deception true", "Forensic Scientist",
                "Jawaban Forensic Scientist. Pas fase Allies: jawaban buat pertanyaan Lab Technician, "
                        + "artinya item yang ditunjuk EMANG dipakai di TKP. Di luar itu: jawaban buat yang "
                        + "pake police badge, tebakannya BENAR -- kalo gak ada witness tim penyelidik langsung "
                        + "menang, kalo ada, murderer masih dapet satu tembakan terakhir buat ngebungkam witness.");
        put("false", "/deception false", "Forensic Scientist",
                "Kebalikan /deception true. Pas fase Allies: item yang ditunjuk Lab Technician BUKAN "
                        + "bagian dari solusi. Di luar itu: tebakan pemegang police badge SALAH -- kalo badge-nya "
                        + "udah habis kepake semua, pembunuh langsung menang.");

        // ---------- Info ----------
        put("roleinfo", "/deception roleinfo [namarole]", "Semua player",
                "Tanpa argumen: buka GUI penjelasan semua role. Pakai nama role: "
                        + "kirim penjelasannya ke chat. Isinya penjelasan umum, GAK bocorin role siapa pun.");
        put("rolevisible", "/deception rolevisible [on|off]", "OP",
                "Kalo ON, tiap peserta liat role DIA SENDIRI nempel di pojok kanan atas layar "
                        + "-- biru kalo tim baik, merah kalo tim jahat. Gak bocorin role orang lain. "
                        + "Dipanggil tanpa on/off = cuma nampilin status sekarang. "
                        + "Bisa juga di-toggle dari /deception setting.");
        put("voicedebug", "/deception voicedebug [on|off]", "OP",
                "Debug voice chat buat testing. Kalo ON, tiap ada mic yang nyampe server dilaporin ke "
                        + "chat SEMUA OP: siapa yang mulai/berhenti ngomong, berapa paket mic-nya, dan "
                        + "berapa yang DITERUSKAN vs DIBLOKIR gate presentasi. Gunanya mbedain \"mic-nya "
                        + "emang gak nyampe server\" dari \"nyampe tapi sengaja diblokir\" -- dari telinga "
                        + "dua-duanya sama-sama sunyi. Dipanggil tanpa on/off = nampilin status + siapa "
                        + "yang detik itu lagi kirim mic.");
        put("debugrole", "/deception debugrole", "OP",
                "Tampilin role SEMUA peserta di chat kamu sendiri. Buat testing -- jelas bocor kalo dipake pas main beneran.");
        put("help", "/deception help [command]", "OP",
                "Tanpa argumen: daftar semua command. Pakai nama command: penjelasan lengkap command itu.");
    }

    public static Entry get(String command) {
        return ENTRIES.get(command.toLowerCase());
    }

    public static Collection<String> names() {
        return ENTRIES.keySet();
    }

    public static Collection<Map.Entry<String, Entry>> all() {
        return ENTRIES.entrySet();
    }
}
