package com.deception.command;

import com.deception.game.Role;

import java.util.HashMap;
import java.util.Map;

public class RoleDescriptions {

    private static final Map<Role, String> DESCRIPTIONS = new HashMap<>();

    static {
        DESCRIPTIONS.put(Role.forensic_scientist, "  Forensic Scientist: Melihat means & clue murderer secara \n  privat, lalu memberi hint ke Investigator tanpa berbicara \n  langsung.");
        DESCRIPTIONS.put(Role.murderer, "  Murderer: Pelaku pembunuhan, tugasnya menyesatkan \n  investigasi dan tidak ketahuan sampai akhir.");
        DESCRIPTIONS.put(Role.accomplice, "  Accomplice: Tahu siapa Murderer dan membantu \n  menyesatkan Investigator tanpa ketahuan.");
        DESCRIPTIONS.put(Role.witness, "  Witness: Melihat siapa saja yang terlibat, \n  tapi tidak tahu siapa Murderer secara pasti.");
        DESCRIPTIONS.put(Role.investigator, "  Investigator: Menganalisis petunjuk dari Forensic Scientist \n  untuk menebak means & clue milik murderer.");
    }

    public static String get(Role role) {
        return DESCRIPTIONS.getOrDefault(role, role.getDisplayName());
    }
}
