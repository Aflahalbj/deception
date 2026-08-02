package com.deception.command;

import com.deception.game.Role;

import java.util.HashMap;
import java.util.Map;

public class RoleDescriptions {

    private static final Map<Role, String> DESCRIPTIONS = new HashMap<>();

    static {
        DESCRIPTIONS.put(Role.FORENSIC_SCIENTIST, "Forensic Scientist: melihat means & clue scene secara privat, lalu memberi hint ke Investigator tanpa bicara langsung.");
        DESCRIPTIONS.put(Role.MURDERER, "Murderer: pelaku pembunuhan, tugasnya menyesatkan investigasi dan tidak ketahuan sampai waktu diskusi habis.");
        DESCRIPTIONS.put(Role.ACCOMPLICE, "Accomplice: tahu siapa Murderer dan membantu menyesatkan Investigator tanpa ketahuan.");
        DESCRIPTIONS.put(Role.WITNESS, "Witness: melihat sedikit informasi tentang kejadian, tapi tidak tahu siapa Murderer secara pasti.");
        DESCRIPTIONS.put(Role.INVESTIGATOR, "Investigator: menganalisis clue dan hint dari Forensic Scientist untuk menebak siapa Murderer.");
    }

    public static String get(Role role) {
        return DESCRIPTIONS.getOrDefault(role, role.getDisplayName());
    }
}
