# Deception (Forge 1.20.1)

Mod social deduction berbasis board game Deception: Murder in Hong Kong.

## Yang sudah jalan (logic-nya)
- Semua command: /regis, /regisall, /unregis, /unregisall, /generatemap,
  /customrole witness|accomplice add|remove, /roleinfo <role> (tab complete),
  /debugrole, /setfs <random|playername> (tab complete), /settimer discuss <menit>,
  /startgame, /stopgame, /resizeclue <persen>
- /deceptionbot spawn|despawn|despawnall <name> — bot pakai Forge FakePlayer asli
  (ServerPlayer), jadi bisa langsung di-/regis kayak player biasa. Ini yang bikin
  testing offline-friendly.
- Role composition table 4-12 pemain sesuai spec, override manual via /customrole
- **Animasi ngocok role**: pas countdown /startgame, semua player terdaftar
  dapet title yang nge-flash nama role random tiap 0.2 detik (kayak slot machine),
  terus di akhir countdown title berhenti nunjukin role asli masing-masing
  player (title gede + subtitle "Peran kamu!"). Ada di `GameManager.spinRoleTitle()`
  dan `GameManager.assignRoles()`.
- **Clue/means sekarang ENTITY**, bukan block lagi:
  - `ClueEntity` — custom entity, nempel floor/wall/ceiling tergantung sisi yang
    diklik pas ditaruh, scale-nya bisa diubah lewat `/resizeclue <persen>`
    (targetnya entity yang lagi diliat player, jarak maks 6 blok)
  - `ClueItem` — 1 item per asset (290 item), klik kanan ke block buat nyepawn
    `ClueEntity` sesuai `clueId`-nya
  - Icon di inventory sekarang flat 2D (`item/generated` + `layer0` transparan),
    bukan render block 3D — jadi ga ada lagi masalah background item yang aneh
  - Renderer (`ClueEntityRenderer`) ini bagian PALING berisiko meleset dari sisi
    compile — API `VertexConsumer`/`PoseStack` gampang beda method chain-nya
    antar versi mapping. Kalau error compile di situ, kemungkinan besar cuma
    perlu benerin urutan/nama method vertex builder.

## Map arena — HARUS dilakuin manual
Map sekarang dari world save folder yang udah lo siapin, bukan generate lewat kode:
1. Bikin/jalanin world server dengan mod ini sekali dulu (biar dimensi
   `deception:arena` ke-generate foldernya).
2. Server bakal punya folder dimensi baru di:
   `<world_folder>/dimensions/deception/arena/`
3. Copy folder `region` (dan `poi`/`entities` kalau ada) dari world save lo yang
   udah jadi map, timpa ke folder di atas.
4. Buka `MapGenerator.java`, ganti konstanta `ARENA_SPAWN` ke koordinat spawn
   yang bener sesuai map lo (bukan spawn dunia server).
5. `/generatemap` sekarang cuma teleport semua player terdaftar ke `ARENA_SPAWN`
   di dimensi itu — chunk generator (`minecraft:flat` void) cuma jaga-jaga buat
   area yang belum lo bangun, gak dipake buat chunk yang udah ada region file-nya.

Custom dimension baru kebaca **saat world pertama kali dibuat** — kalau nambah
mod ini ke world yang udah ada, biasanya perlu world baru (atau restart abis
copy region file di atas, tergantung server-nya).

## Nambah asset baru
1. Taruh PNG (16x16, transparan) di `src/main/resources/assets/deception/textures/item/`
2. Tambah nama file (tanpa `.png`) ke array `CLUE_IDS` di `ModItems.java`
3. Bikin model item-nya:
   ```json
   {
     "parent": "minecraft:item/generated",
     "textures": { "layer0": "deception:item/NAMA_FILE" }
   }
   ```
   simpen di `src/main/resources/assets/deception/models/item/NAMA_FILE.json`
4. (opsional) tambah lang key `item.deception.NAMA_FILE` di `assets/deception/lang/en_us.json`

## Belum ada compile test
Belum sempet gw compile beneran (sandbox gw gaada akses Forge Maven) — kemarin
udah 3 ronde fix compile error bareng-bareng buat versi block-based, jadi kalau
ada error lagi di bagian entity yang baru, kirim aja log-nya, gampang dibenerin.

## Belum ada logic gameplay lanjutan
`/customrole`, `/setfs`, `/settimer` udah nyimpen state dan role udah keassign +
ke-reveal lewat title, tapi belum ada: mekanisme Forensic Scientist ngasih clue
ke Investigator, voting akhir nebak Murderer, dst.
