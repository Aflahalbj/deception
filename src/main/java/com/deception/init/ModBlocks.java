package com.deception.init;

import com.deception.DeceptionMod;
import com.deception.block.ClueBlock;
import com.deception.block.FlagPoleBlock;
import com.deception.block.HongkongFlagBlock;
import com.deception.block.InvestigationPaperBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry semua block clue/means (290 block, 1 per asset, 1 clueId = 1
 * block). Ganti nama file texture-nya buat nambah/ganti asset baru, terus
 * tambahin ID-nya di CLUE_IDS di bawah + bikinin blockstate & model JSON-nya
 * (lihat folder assets/deception/blockstates & models/block).
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, DeceptionMod.MOD_ID);

    public static final String[] CLUE_IDS = {
            "clue_abu_rokok",
            "clue_air_liur",
            "clue_aki_mobil",
            "clue_alarm",
            "clue_amplop",
            "clue_amplop_merah",
            "clue_angpao",
            "clue_anting",
            "clue_asbak",
            "clue_bakpao",
            "clue_ban_mobil",
            "clue_bantal",
            "clue_basement",
            "clue_baterai",
            "clue_bedak",
            "clue_bekas_cakaran",
            "clue_bekas_gigitan",
            "clue_bekas_luka",
            "clue_bel_sepeda",
            "clue_bercak_lilin",
            "clue_bercak_lumpur",
            "clue_bercak_minyak",
            "clue_bercak_tinta",
            "clue_bir",
            "clue_botol_anggur",
            "clue_bubur",
            "clue_buku_alamat",
            "clue_buku_catatan",
            "clue_buku_harian",
            "clue_cangkir_teh",
            "clue_cap_stempel",
            "clue_cctv",
            "clue_cermin",
            "clue_cermin_saku",
            "clue_charger",
            "clue_chip",
            "clue_cincin",
            "clue_darah",
            "clue_dasi",
            "clue_debu",
            "clue_dermaga",
            "clue_dim_sum",
            "clue_dompet",
            "clue_dompet_koin",
            "clue_dompet_kulit",
            "clue_dupa",
            "clue_earphone",
            "clue_flashdisk",
            "clue_foto",
            "clue_gang",
            "clue_gantungan_baju",
            "clue_gantungan_kunci",
            "clue_garpu",
            "clue_gelang",
            "clue_gelas",
            "clue_gong",
            "clue_gorden",
            "clue_handuk",
            "clue_helm",
            "clue_jaket",
            "clue_jam_dinding",
            "clue_jam_tangan",
            "clue_jam_weker",
            "clue_jejak_ban",
            "clue_jejak_kaki",
            "clue_jepit_rambut",
            "clue_jimat",
            "clue_kaca_spion",
            "clue_kacamata",
            "clue_kalender",
            "clue_kalung",
            "clue_kamera",
            "clue_kancing",
            "clue_kaos_kaki",
            "clue_kapas",
            "clue_karcis_parkir",
            "clue_karcis_tol",
            "clue_karet_gelang",
            "clue_karpet",
            "clue_kartu_boarding",
            "clue_kartu_kredit",
            "clue_kartu_nama",
            "clue_kartu_remi",
            "clue_kartu_sim",
            "clue_kaset",
            "clue_keringat",
            "clue_kertas_sembahyang",
            "clue_kipas_sutra",
            "clue_klakson",
            "clue_klip_kertas",
            "clue_kompas",
            "clue_koper",
            "clue_kopi",
            "clue_koran",
            "clue_korek_api",
            "clue_kotak_musik",
            "clue_ktp",
            "clue_kuas_kaligrafi",
            "clue_kue_keranjang",
            "clue_kuku",
            "clue_kunci",
            "clue_kunci_mobil",
            "clue_kursi",
            "clue_kwetiau",
            "clue_kwitansi",
            "clue_lampion",
            "clue_lampu_meja",
            "clue_laptop",
            "clue_lecet",
            "clue_lentera",
            "clue_lilin",
            "clue_liontin_giok",
            "clue_lipstik",
            "clue_lorong",
            "clue_lukisan",
            "clue_mahjong",
            "clue_majalah",
            "clue_mantel",
            "clue_meja",
            "clue_memar",
            "clue_mesin_ketik",
            "clue_mie_instan",
            "clue_modem",
            "clue_naga_kertas",
            "clue_nasi_goreng",
            "clue_palang_pintu",
            "clue_parfum",
            "clue_paspor",
            "clue_password",
            "clue_payung",
            "clue_payung_kertas",
            "clue_pecahan_kaca",
            "clue_pelabuhan",
            "clue_penghapus",
            "clue_peniti",
            "clue_pensil",
            "clue_perangko",
            "clue_perban",
            "clue_permen_karet",
            "clue_peta",
            "clue_peta_jalan",
            "clue_pintu_darurat",
            "clue_piring",
            "clue_piringan_hitam",
            "clue_plat_nomor",
            "clue_ponsel",
            "clue_portal",
            "clue_pulpen",
            "clue_puntung_rokok",
            "clue_radio",
            "clue_rambu_jalan",
            "clue_rambut",
            "clue_rautan_pensil",
            "clue_remote",
            "clue_ritsleting",
            "clue_rokok",
            "clue_rol_film",
            "clue_sabun",
            "clue_sampanye",
            "clue_sandal",
            "clue_sarung_tangan",
            "clue_sate",
            "clue_selimut",
            "clue_sempoa",
            "clue_sendok",
            "clue_sensor",
            "clue_sepatu",
            "clue_serat_kain",
            "clue_serpihan_cat",
            "clue_sidik_jari",
            "clue_sikat_gigi",
            "clue_sikat_rambut",
            "clue_sisir",
            "clue_sobekan_kertas",
            "clue_speaker",
            "clue_sprei",
            "clue_stiker",
            "clue_struk_belanja",
            "clue_sup",
            "clue_surat",
            "clue_sushi",
            "clue_syal",
            "clue_tas_ransel",
            "clue_tas_tangan",
            "clue_teh",
            "clue_teko",
            "clue_telepon_rumah",
            "clue_televisi",
            "clue_terompet",
            "clue_tiket_bioskop",
            "clue_tiket_bus",
            "clue_tiket_kereta",
            "clue_tiket_pesawat",
            "clue_tinta_cina",
            "clue_topi",
            "clue_trotoar",
            "clue_uang_tunai",
            "clue_vas_bunga",
            "clue_ventilasi",
            "clue_wiski",
            "means_air_pasang",
            "means_akuarium",
            "means_alat_pemadam",
            "means_alkohol_metanol",
            "means_tangga_rusak",
            "means_arsenik",
            "means_atap_bocor",
            "means_bak_mandi",
            "means_balkon_rapuh",
            "means_balok_kayu",
            "means_bara_api",
            "means_batu",
            "means_batu_bata",
            "means_belati",
            "means_bendungan",
            "means_botol_kaca",
            "means_bubuk_mesiu",
            "means_cairan_pembakar",
            "means_crossbow",
            "means_cutter",
            "means_ember_air",
            "means_eskalator_rusak",
            "means_gas_beracun",
            "means_golok",
            "means_insulin",
            "means_isolasi",
            "means_jamur_beracun",
            "means_jarum",
            "means_jembatan_rapuh",
            "means_jendela_terbuka",
            "means_kabel_korslet",
            "means_kain_basah",
            "means_kantong_plastik",
            "means_karung",
            "means_kawat_baja",
            "means_kembang_api",
            "means_kolam_renang",
            "means_kompor_gas",
            "means_kubangan_lumpur",
            "means_kunci_inggris",
            "means_kursi_goyah",
            "means_lift_rusak",
            "means_lilin_besar",
            "means_meriam_mini",
            "means_merkuri",
            "means_minyak_tanah",
            "means_obat_tidur",
            "means_obeng",
            "means_obor",
            "means_paku",
            "means_palu",
            "means_panah",
            "means_pedang",
            "means_peluru",
            "means_pestisida",
            "means_petasan_modifikasi",
            "means_pipa_besi",
            "means_pisau_dapur",
            "means_pistol",
            "means_racun_tikus",
            "means_racun_ular",
            "means_raket",
            "means_rantai",
            "means_revolver",
            "means_sabit",
            "means_sabuk",
            "means_selang",
            "means_selendang",
            "means_selokan_banjir",
            "means_senapan",
            "means_senapan_angin",
            "means_senapan_sniper",
            "means_senjata_rakitan",
            "means_shotgun",
            "means_sianida",
            "means_silet",
            "means_sumbu_api",
            "means_sumur",
            "means_tabung_gas",
            "means_tali_putus",
            "means_tali_sepatu",
            "means_tali_tambang",
            "means_tangga_licin",
            "means_tebing",
            "means_tombak",
            "means_tongkat_baseball",
            "means_tongkat_golf",
            "means_waduk",
            "means_wajan",
            "means_wastafel",
    };

    public static final Map<String, RegistryObject<Block>> CLUE_BLOCKS = new LinkedHashMap<>();

    static {
        for (String id : CLUE_IDS) {
            RegistryObject<Block> block = BLOCKS.register(id, () -> new ClueBlock(
                    Block.Properties.of()
                            .mapColor(MapColor.NONE)
                            .noOcclusion()
                            .noCollission()
                            .sound(SoundType.WOOL)
                            .strength(0.1F)
                            .instabreak()));
            CLUE_BLOCKS.put(id, block);
        }
    }

    // Block dekoratif terpisah dari sistem clue/means -- model 3D utuh
    // (kertas + pin) pake Forge OBJ loader, cuma bisa nempel di tembok.
    // Liat InvestigationPaperBlock & models/block/investigation_paper.json.
    public static final RegistryObject<Block> INVESTIGATION_PAPER = BLOCKS.register("investigation_paper",
            () -> new InvestigationPaperBlock(Block.Properties.of()
                    .mapColor(MapColor.NONE)
                    .noOcclusion()
                    .noCollission()
                    .sound(SoundType.WOOL)
                    .strength(0.1F)
                    .instabreak()));

    // Tiang bendera -- block dekoratif, ditumpuk buat bikin tiang setinggi
    // apa pun. Ruas paling atas diganti HONGKONG_FLAG.
    public static final RegistryObject<Block> FLAG_POLE = BLOCKS.register("flag_pole",
            () -> new FlagPoleBlock(Block.Properties.of()
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .sound(SoundType.METAL)
                    .strength(1.5F)));

    // Ujung tiang + kain bendera Hong Kong 3x2 block yang berkibar
    // (kainnya digambar HongkongFlagRenderer, bukan block).
    public static final RegistryObject<Block> HONGKONG_FLAG = BLOCKS.register("hongkong_flag",
            () -> new HongkongFlagBlock(Block.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .noOcclusion()
                    .sound(SoundType.METAL)
                    .strength(1.5F)));
}