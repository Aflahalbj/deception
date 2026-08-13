package com.deception.init;

import com.deception.client.gui.DeceptionButton;
import com.deception.client.gui.DeceptionScreen;
import com.deception.network.ChooseForensicOptionPacket;
import com.deception.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.util.List;

/**
 * GUI buat milih satu opsi investigation paper (misal kategori "Cuaca" ->
 * pilih "Cerah"). Dibuka dari OpenForensicPickerPacket, pilihan dikirim
 * balik ke server lewat ChooseForensicOptionPacket. Bukan container
 * screen -- gak ada slot/inventory, cuma list tombol biasa.
 *
 * <p>Layout-nya diserahin ke DeceptionScreen: semua posisi dihitung sebagai
 * fraksi area konten papan, gak ada koordinat pixel yang dihardcode.
 */
public class ForensicPaperScreen extends DeceptionScreen {

    private final String category;
    private final List<String> options;
    private final InteractionHand hand;

    private ForensicPaperScreen(String category, List<String> options, InteractionHand hand) {
        // Papan polos: opsinya bisa banyak, butuh lebar penuh -- papan DETAILED
        // kolom tengahnya kesempitan karena kiri-kanan keisi polaroid.
        super(Component.literal(category), Board.CLEAN);
        this.category = category;
        this.options = options;
        this.hand = hand;
    }

    public static void open(String category, List<String> options, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new ForensicPaperScreen(category, options, hand));
    }

    @Override
    protected void buildContent() {
        int count = options.size();
        // Kategori bisa punya 6 atau 8 opsi -- Grid yang ngatur biar dua-duanya
        // tetap muat di papan tanpa perlu angka beda per kategori.
        Grid grid = column(count);

        for (int i = 0; i < count; i++) {
            String option = options.get(i);
            this.addRenderableWidget(new DeceptionButton(
                    grid.x(i), grid.y(i), grid.itemWidth, grid.itemHeight,
                    Component.literal(option),
                    button -> chooseOption(option)));
        }
    }

    private void chooseOption(String option) {
        ModNetworking.CHANNEL.sendToServer(
                new ChooseForensicOptionPacket(hand, category, option));
        this.onClose();
    }
}
