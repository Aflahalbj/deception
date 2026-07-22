package com.deception.game;

import java.util.HashMap;
import java.util.Map;

/**
 * Komposisi role default per jumlah pemain (4-12), sesuai tabel yang diminta.
 * FS dan Murderer selalu 1. Accomplice/Witness aktif otomatis mulai jumlah
 * pemain tertentu, tapi bisa di-override manual lewat /customrole.
 */
public class RoleComposition {

    // key = jumlah pemain, value = {accomplice, witness} default (0 atau 1)
    private static final Map<Integer, int[]> DEFAULT_TABLE = new HashMap<>();

    static {
        DEFAULT_TABLE.put(4, new int[]{0, 0});
        DEFAULT_TABLE.put(5, new int[]{0, 0});
        DEFAULT_TABLE.put(6, new int[]{1, 0});
        DEFAULT_TABLE.put(7, new int[]{1, 1});
        DEFAULT_TABLE.put(8, new int[]{1, 1});
        DEFAULT_TABLE.put(9, new int[]{1, 1});
        DEFAULT_TABLE.put(10, new int[]{1, 1});
        DEFAULT_TABLE.put(11, new int[]{1, 1});
        DEFAULT_TABLE.put(12, new int[]{1, 1});
    }

    private final int playerCount;
    private boolean accompliceEnabled;
    private boolean witnessEnabled;

    public RoleComposition(int playerCount) {
        this.playerCount = Math.max(4, Math.min(12, playerCount));
        int[] def = DEFAULT_TABLE.getOrDefault(this.playerCount, new int[]{0, 0});
        this.accompliceEnabled = def[0] == 1;
        this.witnessEnabled = def[1] == 1;
    }

    public void setAccompliceEnabled(boolean enabled) {
        this.accompliceEnabled = enabled;
    }

    public void setWitnessEnabled(boolean enabled) {
        this.witnessEnabled = enabled;
    }

    public boolean isAccompliceEnabled() {
        return accompliceEnabled;
    }

    public boolean isWitnessEnabled() {
        return witnessEnabled;
    }

    /**
     * Hitung jumlah tiap role final. Sisa dari total dikurangi role fixed
     * (FS + Murderer + Accomplice? + Witness?) masuk ke Investigator.
     */
    public Map<Role, Integer> resolve() {
        Map<Role, Integer> result = new HashMap<>();
        int used = 2; // FS + Murderer
        result.put(Role.FORENSIC_SCIENTIST, 1);
        result.put(Role.MURDERER, 1);

        if (accompliceEnabled) {
            result.put(Role.ACCOMPLICE, 1);
            used += 1;
        }
        if (witnessEnabled) {
            result.put(Role.WITNESS, 1);
            used += 1;
        }

        int investigators = Math.max(0, playerCount - used);
        result.put(Role.INVESTIGATOR, investigators);
        return result;
    }
}
