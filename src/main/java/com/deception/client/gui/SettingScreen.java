package com.deception.client.gui;

import com.deception.game.SettingSnapshot;
import com.deception.network.SettingActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Menu utama /deception setting. Cuma tiga pintu masuk ke sub-screen; gak ada
 * setting yang diubah langsung dari sini.
 *
 * <p>Pakai papan DETAILED (yang ada polaroid) soalnya isinya cuma 3 tombol --
 * muat di kolom tengah yang sempit, dan dekorasinya kepake. Sub-screen-nya
 * pakai papan CLEAN karena butuh lebar penuh.
 */
public class SettingScreen extends DeceptionScreen {

    public SettingScreen() {
        super(Component.literal("Setting"), Board.DETAILED);
    }

    @Override
    protected void buildContent() {
        Grid grid = column(4);

        addRenderableWidget(new DeceptionButton(
                grid.x(0), grid.y(0), grid.itemWidth, grid.itemHeight,
                Component.literal("Set Timer"),
                b -> Minecraft.getInstance().setScreen(new SetTimerScreen(this))));

        addRenderableWidget(new DeceptionButton(
                grid.x(1), grid.y(1), grid.itemWidth, grid.itemHeight,
                Component.literal("Custom Role"),
                b -> Minecraft.getInstance().setScreen(new CustomRoleScreen(this))));

        addRenderableWidget(new DeceptionButton(
                grid.x(2), grid.y(2), grid.itemWidth, grid.itemHeight,
                Component.literal("Set Role"),
                b -> Minecraft.getInstance().setScreen(new SetRoleScreen(this))));

        // Satu-satunya setting yang diubah LANGSUNG dari menu utama (yang lain
        // cuma pintu ke sub-screen) -- sengaja, biar cepet di-toggle pas lagi
        // ngetes. Statusnya dibaca dari snapshot server, bukan disimpen di GUI.
        SettingSnapshot snapshot = ClientSettingState.get();
        boolean roleVisible = snapshot != null && snapshot.roleVisible();
        addRenderableWidget(new DeceptionButton(
                grid.x(3), grid.y(3), grid.itemWidth, grid.itemHeight,
                Component.literal("Role Visible: " + (roleVisible ? "ON" : "OFF")),
                b -> ClientSettingState.send(SettingActionPacket.Action.TOGGLE_ROLE_VISIBLE)));
    }
}
