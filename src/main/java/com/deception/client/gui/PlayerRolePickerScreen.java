package com.deception.client.gui;

import com.deception.game.Role;
import com.deception.game.SettingSnapshot;
import com.deception.network.SettingActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Pilih role buat satu player. Dibuka dari {@link SetRoleScreen}, dan langsung
 * balik ke sana begitu dipilih -- daftar di sana ke-refresh sendiri pas
 * snapshot baru dari server nyampe.
 */
public class PlayerRolePickerScreen extends SettingSubScreen {

    /** Nilai yang dikirim buat "lepas paksaan role". */
    private static final int ROLE_AUTO = -1;

    private final String playerName;

    public PlayerRolePickerScreen(Screen parent, String playerName) {
        super(Component.literal(playerName), parent);
        this.playerName = playerName;
    }

    @Override
    protected void buildSettings(SettingSnapshot snapshot) {
        Role[] roles = Role.values();
        // +1 buat opsi Auto di paling atas.
        Grid grid = column(roles.length + 1);

        addRenderableWidget(new DeceptionButton(
                grid.x(0), grid.y(0), grid.itemWidth, grid.itemHeight,
                Component.literal("Auto (Acak)"),
                b -> choose(ROLE_AUTO)));

        for (int i = 0; i < roles.length; i++) {
            Role role = roles[i];
            int slot = i + 1;

            DeceptionButton button = new DeceptionButton(
                    grid.x(slot), grid.y(slot), grid.itemWidth, grid.itemHeight,
                    Component.literal(role.getDisplayName()),
                    b -> choose(role.ordinal()));

            String blocked = blockedReason(snapshot, role);
            button.active = blocked == null;
            button.setTooltip(Tooltip.create(Component.literal(
                    blocked != null ? blocked : "Set " + playerName + " menjadi " + role.getDisplayName())));

            addRenderableWidget(button);
        }
    }

    /**
     * Kenapa role ini gak bisa dipilih, atau null kalau boleh. Aturannya
     * dihitung sama {@code SettingSnapshot} -- persis yang dipaksain server,
     * jadi gak mungkin ada tombol nyala yang ternyata ditolak.
     */
    private String blockedReason(SettingSnapshot snapshot, Role role) {
        if (snapshot.canAssign(playerName, role)) {
            return null;
        }
        if (snapshot.roleCapacity().getOrDefault(role, 0) == 0) {
            return role.getDisplayName() + " nonaktif di Custom Role";
        }
        String holder = snapshot.holderOf(role, playerName);
        return holder != null
                ? "Sudah dipegang " + holder
                : role.getDisplayName() + " sudah penuh";
    }

    private void choose(int roleOrdinal) {
        ClientSettingState.send(SettingActionPacket.Action.SET_PLAYER_ROLE, roleOrdinal, playerName);
        onClose();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawNote(g, "Role untuk " + playerName);
    }
}
