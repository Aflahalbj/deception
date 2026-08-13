package com.deception.init;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * Sisi CLIENT dari PoliceBadgeUsePacket: niru persis animasi pop totem
 * vanilla (LivingEntity#handleEntityEvent case 35) tapi popup item
 * activation-nya dimainin di SEMUA layar, bukan cuma di layar yang make.
 * Item-nya Totem of Undying asli yang di-retexture jadi badge (lihat komentar
 * di PresentationManager), jadi popup-nya otomatis nunjukin gambar badge.
 */
public class PoliceBadgeUseAnimation {

    private static final int PARTICLE_COUNT = 30;

    public static void play(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Partikel cuma bisa digambar kalo playernya emang ke-load di client
        // ini (sejangkauan render). Kalo jauh/gak ke-track, popup + suaranya
        // tetep jalan -- itu yang penting semua orang liat.
        Player target = mc.level.getPlayerByUUID(uuid);
        if (target != null) {
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                mc.level.addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                        target.getRandomX(1.0),
                        target.getRandomY() + 0.5,
                        target.getRandomZ(1.0),
                        mc.level.random.nextGaussian() * 0.05,
                        mc.level.random.nextGaussian() * 0.05 + 0.2,
                        mc.level.random.nextGaussian() * 0.05);
            }
        }

        // Suaranya sengaja dimainin di posisi player LOKAL (bukan posisi yang
        // make badge) biar volumenya sama rata buat semua orang, gak ngecil
        // kalo kejauhan -- ini momen pengumuman, bukan efek ambient.
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f, false);
        mc.gameRenderer.displayItemActivation(new ItemStack(Items.TOTEM_OF_UNDYING));
    }
}
