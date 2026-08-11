package com.deception.init;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Nyimpen siapa aja yang lagi pegang police_badge (buat render icon di
 * sebelah nametag) -- di-update lewat PoliceBadgeHolderPacket dari server,
 * lihat javadoc di situ kenapa perlu di-broadcast.
 */
public class PoliceBadgeClientState {

    private static final Set<UUID> holders = new HashSet<>();

    public static void setHasBadge(UUID uuid, boolean hasBadge) {
        if (hasBadge) {
            holders.add(uuid);
        } else {
            holders.remove(uuid);
        }
    }

    public static boolean hasBadge(UUID uuid) {
        return holders.contains(uuid);
    }
}
