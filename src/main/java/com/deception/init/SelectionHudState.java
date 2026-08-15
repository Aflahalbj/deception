package com.deception.init;

import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Nampung isi panel pilihan yang dikirim server (lihat
 * network/SelectionHudPacket). Cuma state mentah -- gambarnya di
 * {@link SelectionOverlay}.
 *
 * <p>Baris {@code warning} punya umur sendiri di client: dia ilang otomatis
 * setelah {@link #WARNING_TICKS}, tanpa server perlu ngirim apa-apa lagi.
 * Sengaja gitu -- yang perlu ngilangin cuma tampilan, dan server gak punya
 * urusan sama timer visual.
 */
public class SelectionHudState {

    /** Umur baris penolakan: 4 detik, cukup kebaca tapi gak nyangkut lama. */
    private static final int WARNING_TICKS = 80;

    private static List<Component> lines = List.of();
    private static Component warning = null;
    private static int warningTicksLeft = 0;

    public static void set(List<Component> newLines, Component newWarning) {
        lines = newLines == null ? List.of() : newLines;
        warning = newWarning;
        warningTicksLeft = newWarning == null ? 0 : WARNING_TICKS;
    }

    public static void clear() {
        lines = List.of();
        warning = null;
        warningTicksLeft = 0;
    }

    public static List<Component> getLines() {
        return lines;
    }

    /** null kalau lagi gak ada penolakan yang perlu ditampilin (atau udah kedaluwarsa). */
    public static Component getWarning() {
        return warningTicksLeft > 0 ? warning : null;
    }

    public static boolean isEmpty() {
        return lines.isEmpty() && getWarning() == null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (warningTicksLeft > 0) {
            warningTicksLeft--;
            if (warningTicksLeft == 0) warning = null;
        }
    }
}
