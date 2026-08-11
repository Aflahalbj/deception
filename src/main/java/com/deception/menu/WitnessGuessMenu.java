package com.deception.menu;

import com.deception.game.PresentationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ChestMenu;

import java.util.List;
import java.util.UUID;

/**
 * UI mirip chest (reuse ChestScreen vanilla, gak perlu bikin screen custom)
 * isinya head semua player teregistrasi kecuali FS -- murderer klik salah
 * satu buat nebak witness. BUKAN kontainer beneran: clicked() di-override
 * biar item-nya gak bisa diambil/dipindah sama sekali, klik cuma dibaca
 * sebagai "pilihan", gak manggil super.clicked() sama sekali.
 */
public class WitnessGuessMenu extends ChestMenu {

    private final List<UUID> slotOrder;

    /** Dipanggil server pas beneran buka menu ini -- container-nya udah keisi head + urutan slot->UUID. */
    public WitnessGuessMenu(int containerId, Inventory playerInventory, Container container, List<UUID> slotOrder) {
        super(ModMenus.WITNESS_GUESS.get(), containerId, playerInventory, container, 2);
        this.slotOrder = slotOrder;
    }

    /** Dipanggil client pas nerima packet buka menu -- container kosong, keisi otomatis lewat sync slot biasa. */
    public WitnessGuessMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(18), List.of());
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slotOrder.size() && player instanceof ServerPlayer serverPlayer) {
            UUID guessed = slotOrder.get(slotId);
            PresentationManager.get().onWitnessGuessClicked(serverPlayer, guessed);
        }
        // SENGAJA gak manggil super.clicked(...) -- biar head-nya gak bisa
        // diambil/dipindah, klik cuma trigger pilihan doang.
    }
}
