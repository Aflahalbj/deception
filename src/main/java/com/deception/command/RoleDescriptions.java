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
        DESCRIPTIONS.put(Role.protective_detail, "  Protective Detail: Tahu siapa Witness, tugasnya menarik \n  perhatian murderer supaya salah menembak di akhir.");
        DESCRIPTIONS.put(Role.lab_technician, "  Lab Technician: Sebelum ronde 2, boleh menanyakan 1 item \n  ke Forensic Scientist apakah dipakai di TKP.");
        DESCRIPTIONS.put(Role.inside_man, "  Inside Man: Tim murderer. Sebelum ronde 2, mencabut \n  police badge 1 orang supaya jatah tebakan berkurang.");
    }

    public static String get(Role role) {
        return DESCRIPTIONS.getOrDefault(role, role.getDisplayName());
    }
}
