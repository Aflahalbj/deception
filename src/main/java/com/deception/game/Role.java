package com.deception.game;

public enum Role {
    forensic_scientist("Forensic Scientist"),
    murderer("Murderer"),
    accomplice("Accomplice"),
    witness("Witness"),
    investigator("Investigator");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Role fromString(String name) {
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(name) || role.displayName.equalsIgnoreCase(name)) {
                return role;
            }
        }
        return null;
    }
}
