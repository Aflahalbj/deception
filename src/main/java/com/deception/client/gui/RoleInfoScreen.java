package com.deception.client.gui;

import com.deception.game.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Daftar role di /deception inforole. Bisa dibuka siapa saja -- isinya cuma
 * penjelasan, gak ada satu pun yang ngubah state game.
 */
public class RoleInfoScreen extends DeceptionScreen {

    public RoleInfoScreen() {
        super(Component.literal("Role Info"), Board.CLEAN);
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new RoleInfoScreen());
    }

    @Override
    protected void buildContent() {
        Role[] roles = Role.values();
        Grid grid = column(roles.length);

        for (int i = 0; i < roles.length; i++) {
            Role role = roles[i];
            addRenderableWidget(new DeceptionButton(
                    grid.x(i), grid.y(i), grid.itemWidth, grid.itemHeight,
                    Component.literal(role.getDisplayName()),
                    b -> Minecraft.getInstance().setScreen(new RoleDetailScreen(this, role))));
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int y = contentY - this.font.lineHeight - 3;
        g.drawCenteredString(this.font, "Pilih role untuk lihat detailnya",
                centerX(), y, DeceptionTheme.TEXT_MUTED);
    }
}
