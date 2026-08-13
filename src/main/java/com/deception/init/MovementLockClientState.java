package com.deception.init;

import net.minecraft.client.player.Input;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Sisi CLIENT dari MovementLockPacket: nolin input gerak selama lagi
 * dibekuin (fase shootout). Dicegat di MovementInputUpdateEvent -- PERSIS
 * setelah vanilla baca keyboard, sebelum dipake buat ngitung gerakan -- jadi
 * playernya emang gak pernah jalan sama sekali, bukan dijalanin terus
 * ditarik balik (yang bakal keliatan rubber-band).
 *
 * Arah pandang SENGAJA gak dikunci, cuma posisi (lihat javadoc PlayerFreeze).
 */
public class MovementLockClientState {

    private static boolean locked = false;

    public static void setLocked(boolean locked) {
        MovementLockClientState.locked = locked;
    }

    public static boolean isLocked() {
        return locked;
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!locked) return;

        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }
}
