package com.deception.init;

/**
 * Nampung role sendiri yang lagi ditampilin di pojok kanan atas (lihat
 * network/RoleVisibleHudPacket). Cuma state mentah -- gambarnya di
 * RoleVisibleOverlay.
 */
public class RoleVisibleHudState {

    private static String roleName = "";
    private static boolean evilTeam = false;

    public static void show(String name, boolean evil) {
        roleName = name;
        evilTeam = evil;
    }

    public static void clear() {
        roleName = "";
        evilTeam = false;
    }

    public static String getRoleName() {
        return roleName;
    }

    public static boolean isEvilTeam() {
        return evilTeam;
    }

    public static boolean isEmpty() {
        return roleName.isEmpty();
    }
}
