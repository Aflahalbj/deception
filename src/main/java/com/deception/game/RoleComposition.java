package com.deception.game;

import java.util.HashMap;
import java.util.Map;

/**
 * Komposisi role default per jumlah pemain (4-12), sesuai tabel yang diminta.
 * FS dan Murderer selalu 1. Role opsional aktif otomatis mulai jumlah pemain
 * tertentu, tapi bisa di-override manual lewat /customrole.
 *
 * <p>Angka default buat tiga role expansion "Undercover Allies" ngikutin
 * rekomendasi resmi rulebook-nya:
 * <ul>
 *   <li>Lab Technician: 7 pemain ke atas</li>
 *   <li>Inside Man: 8 pemain ke atas</li>
 *   <li>Protective Detail: gak punya rekomendasi jumlah pemain sama sekali di
 *       rulebook, cuma disebut "opsional buat game yang pakai Witness" --
 *       jadi default-nya MATI di semua jumlah pemain, tinggal dinyalain
 *       manual lewat /customrole kalau mau dipakai.</li>
 * </ul>
 */
public class RoleComposition {

    // key = jumlah pemain, value = {accomplice, witness, protective_detail,
    // lab_technician, inside_man} default (0 atau 1)
    private static final Map<Integer, int[]> DEFAULT_TABLE = new HashMap<>();

    static {
        DEFAULT_TABLE.put(4, new int[]{0, 0, 0, 0, 0});
        DEFAULT_TABLE.put(5, new int[]{0, 0, 0, 0, 0});
        DEFAULT_TABLE.put(6, new int[]{1, 0, 0, 0, 0});
        DEFAULT_TABLE.put(7, new int[]{1, 1, 0, 1, 0});
        DEFAULT_TABLE.put(8, new int[]{1, 1, 0, 1, 1});
        DEFAULT_TABLE.put(9, new int[]{1, 1, 0, 1, 1});
        DEFAULT_TABLE.put(10, new int[]{1, 1, 0, 1, 1});
        DEFAULT_TABLE.put(11, new int[]{1, 1, 0, 1, 1});
        DEFAULT_TABLE.put(12, new int[]{1, 1, 0, 1, 1});
    }

    private static final int[] NONE = new int[]{0, 0, 0, 0, 0};

    private final int playerCount;
    private boolean accompliceEnabled;
    private boolean witnessEnabled;
    private boolean protectiveDetailEnabled;
    private boolean labTechnicianEnabled;
    private boolean insideManEnabled;

    public RoleComposition(int playerCount) {
        this.playerCount = Math.max(4, Math.min(12, playerCount));
        int[] def = DEFAULT_TABLE.getOrDefault(this.playerCount, NONE);
        this.accompliceEnabled = def[0] == 1;
        this.witnessEnabled = def[1] == 1;
        this.protectiveDetailEnabled = def[2] == 1;
        this.labTechnicianEnabled = def[3] == 1;
        this.insideManEnabled = def[4] == 1;
    }

    public void setAccompliceEnabled(boolean enabled) {
        this.accompliceEnabled = enabled;
    }

    public void setWitnessEnabled(boolean enabled) {
        this.witnessEnabled = enabled;
    }

    public void setProtectiveDetailEnabled(boolean enabled) {
        this.protectiveDetailEnabled = enabled;
    }

    public void setLabTechnicianEnabled(boolean enabled) {
        this.labTechnicianEnabled = enabled;
    }

    public void setInsideManEnabled(boolean enabled) {
        this.insideManEnabled = enabled;
    }

    public boolean isAccompliceEnabled() {
        return accompliceEnabled;
    }

    public boolean isWitnessEnabled() {
        return witnessEnabled;
    }

    /**
     * Protective Detail kerjaannya cuma satu: liat siapa Witness. Tanpa
     * Witness dia gak punya apa-apa buat diliat, jadi walau di-set AKTIF
     * manual hasilnya tetep dianggap mati -- sama kayak aturan resminya
     * ("opsional buat game yang pakai Witness").
     */
    public boolean isProtectiveDetailEnabled() {
        return protectiveDetailEnabled && witnessEnabled;
    }

    public boolean isLabTechnicianEnabled() {
        return labTechnicianEnabled;
    }

    public boolean isInsideManEnabled() {
        return insideManEnabled;
    }

    /**
     * Hitung jumlah tiap role final. Sisa dari total dikurangi role fixed
     * (FS + Murderer + role opsional yang aktif) masuk ke Investigator.
     */
    public Map<Role, Integer> resolve() {
        Map<Role, Integer> result = new HashMap<>();
        int used = 2; // FS + Murderer
        result.put(Role.forensic_scientist, 1);
        result.put(Role.murderer, 1);

        if (accompliceEnabled) {
            result.put(Role.accomplice, 1);
            used += 1;
        }
        if (witnessEnabled) {
            result.put(Role.witness, 1);
            used += 1;
        }
        if (isProtectiveDetailEnabled()) {
            result.put(Role.protective_detail, 1);
            used += 1;
        }
        if (labTechnicianEnabled) {
            result.put(Role.lab_technician, 1);
            used += 1;
        }
        if (insideManEnabled) {
            result.put(Role.inside_man, 1);
            used += 1;
        }

        int investigators = Math.max(0, playerCount - used);
        result.put(Role.investigator, investigators);
        return result;
    }
}
