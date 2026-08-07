package com.deception.init;

import com.deception.network.ChooseForensicOptionPacket;
import com.deception.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.util.List;

/**
 * GUI buat milih satu opsi investigation paper (misal kategori "Cuaca" ->
 * pilih "Cerah"). Dibuka dari OpenForensicPickerPacket, pilihan dikirim
 * balik ke server lewat ChooseForensicOptionPacket. Bukan container
 * screen -- gak ada slot/inventory, cuma list tombol biasa.
 */
public class ForensicPaperScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 24;

    private final String category;
    private final List<String> options;
    private final InteractionHand hand;

    private ForensicPaperScreen(String category, List<String> options, InteractionHand hand) {
        super(Component.literal("Kategori: " + category));
        this.category = category;
        this.options = options;
        this.hand = hand;
    }

    public static void open(String category, List<String> options, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new ForensicPaperScreen(category, options, hand));
    }

    @Override
    protected void init() {
        int startY = this.height / 2 - (options.size() * BUTTON_SPACING) / 2;
        int centerX = this.width / 2 - BUTTON_WIDTH / 2;

        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            int y = startY + i * BUTTON_SPACING;
            this.addRenderableWidget(Button.builder(Component.literal(option), btn -> chooseOption(option))
                    .bounds(centerX, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }
    }

    private void chooseOption(String option) {
        ModNetworking.CHANNEL.sendToServer(new ChooseForensicOptionPacket(hand, category, option));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int titleY = this.height / 2 - (options.size() * BUTTON_SPACING) / 2 - 24;
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, titleY, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
