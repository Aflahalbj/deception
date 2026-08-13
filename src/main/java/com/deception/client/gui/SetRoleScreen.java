package com.deception.client.gui;

import com.deception.game.Role;
import com.deception.game.SettingSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Set Role: paksa role satu player, atau balikin ke auto (ikut random pas
 * start game). Satu tombol per player teregistrasi, isinya nama + role yang
 * lagi keset.
 *
 * <p>Player bisa sampe 12. Lebih dari 6 otomatis pindah ke 2 kolom -- kalo
 * dipaksa 1 kolom, tombolnya kekecilan sampe gak kebaca.
 */
public class SetRoleScreen extends SettingSubScreen {

    /** Di atas jumlah ini, tata letaknya pecah jadi 2 kolom. */
    private static final int SINGLE_COLUMN_MAX = 6;

    private boolean empty;

    public SetRoleScreen(Screen parent) {
        super(Component.literal("Set Role"), parent);
    }

    @Override
    protected void buildSettings(SettingSnapshot snapshot) {
        List<SettingSnapshot.PlayerEntry> players = snapshot.players();
        this.empty = players.isEmpty();
        if (empty) return;

        int columns = players.size() > SINGLE_COLUMN_MAX ? 2 : 1;
        Grid grid = grid(players.size(), columns);

        for (int i = 0; i < players.size(); i++) {
            SettingSnapshot.PlayerEntry entry = players.get(i);
            addRenderableWidget(DeceptionButton.withTooltip(
                    grid.x(i), grid.y(i), grid.itemWidth, grid.itemHeight,
                    Component.literal(label(entry)), tooltip(entry),
                    b -> Minecraft.getInstance().setScreen(
                            new PlayerRolePickerScreen(this, entry.name()))));
        }
    }

    /**
     * Role disingkat di tombol. Nama lengkap ("Forensic Scientist") bikin
     * label kepanjangan sampe kepotong pas mode 2 kolom -- versi panjangnya
     * tetep kebaca lewat tooltip.
     */
    private static String shortRole(Role role) {
        if (role == null) return "Auto";
        return switch (role) {
            case forensic_scientist -> "Forensic Scientist";
            case murderer -> "Murderer";
            case accomplice -> "Accomplice";
            case witness -> "Witness";
            case investigator -> "Investigator";
        };
    }

    private static String label(SettingSnapshot.PlayerEntry entry) {
        return entry.name() + " - " + shortRole(entry.role());
    }

    private static Component tooltip(SettingSnapshot.PlayerEntry entry) {
        Role role = entry.role();
        String current = role == null ? "Auto (acak)" : role.getDisplayName();
        return Component.literal(current + " - klik untuk ganti");
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (empty) {
            g.drawCenteredString(this.font, "Belum ada player yang di-regis.",
                    centerX(), contentY + contentH / 2, DeceptionTheme.TEXT_MUTED);
            return;
        }
    }
}
