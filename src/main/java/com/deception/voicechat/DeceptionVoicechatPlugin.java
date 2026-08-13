package com.deception.voicechat;

import com.deception.DeceptionMod;
import com.deception.game.GameManager;
import com.deception.game.PresentationManager;
import com.deception.game.VoiceDebugState;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;

import java.util.UUID;

/**
 * Plugin Simple Voice Chat -- di-scan otomatis lewat annotation
 * @ForgeVoicechatPlugin, gak perlu didaftarin manual di DeceptionMod.
 *
 * Enforce mute fase PRESENTASI: cancel MicrophonePacketEvent (dipicu pas
 * mic packet BARU NYAMPE di server, sebelum sempet di-relay ke siapapun)
 * buat semua player KECUALI yang lagi giliran ngomong. Beda dari
 * group/disable toggle biasa (yang bisa di-bypass player manual lewat
 * /voicechat), ini beneran nyegat di titik paling awal jadi audio-nya gak
 * pernah sampe di-relay ke siapapun.
 *
 * Butuh mod Simple Voice Chat beneran ke-install di server (bukan cuma API
 * jar-nya) -- API ini cuma compileOnly, class ini bakal error kalo mod
 * aslinya gak ada.
 */
@ForgeVoicechatPlugin
public class DeceptionVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return DeceptionMod.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        UUID sender = event.getSenderConnection().getPlayer().getUuid();

        // Di luar fase presentasi gak ada yang dibatasi -- semua paket lewat.
        if (GameManager.get().getState() != GameManager.State.PRESENTASI) {
            VoiceDebugState.recordPacket(sender, true);
            return;
        }

        UUID speaker = PresentationManager.get().getCurrentSpeaker();
        boolean passed = sender.equals(speaker);
        if (!passed) {
            event.cancel();
        }
        // Dicatet SETELAH keputusan cancel-nya, biar yang kelaporan itu nasib
        // asli paketnya, bukan tebakan. Method ini jalan di thread voicechat
        // -- lihat javadoc VoiceDebugState.
        VoiceDebugState.recordPacket(sender, passed);
    }
}
