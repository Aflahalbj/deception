package com.deception.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Jembatan tipis ke MrCrayfish's Gun Mod [Unofficial] (modid "cgm") buat
 * shotgun fase shootout.
 *
 * SENGAJA gak compile ke API CGM sama sekali -- item-nya diambil lewat
 * ResourceLocation + registry, isi pelurunya lewat NBT mentah. Alasannya:
 * (1) build kita gak jadi ikut ketarik ke API mod pihak ketiga yang bisa
 * berubah tiap versi, (2) satu-satunya yang kita butuhin emang cuma "kasih
 * shotgun isi 1 peluru" -- sisanya (nembak, recoil, partikel, suara)
 * urusan CGM sendiri. Deteksi kena/meleset juga gak nyentuh CGM: kena
 * dibaca dari LivingAttackEvent vanilla (peluru CGM manggil Entity#hurt
 * biasa), meleset dibaca dari AmmoCount yang turun jadi 0.
 *
 * CGM didaftar mandatory di mods.toml, jadi normalnya item ini PASTI ada.
 * Method di sini tetep balikin ItemStack.EMPTY kalo somehow gak ketemu
 * (misal CGM ganti nama item di versi baru) -- caller yang ngasih tau
 * kenapa fase shootout-nya gak jalan, daripada NPE di tengah game.
 */
public class ShootoutGun {

    private static final ResourceLocation SHOTGUN_ID = new ResourceLocation("cgm", "shotgun");
    /** Key NBT tempat CGM nyimpen sisa peluru di dalem stack gun-nya. */
    private static final String AMMO_COUNT_TAG = "AmmoCount";
    /**
     * Nandain shotgun MANA yang beneran punya kita (pola sama kayak
     * police badge) -- biar shotgun CGM biasa punya siapapun gak ikut
     * ke-lock/ke-detect sebagai senjata shootout.
     */
    private static final String SHOOTOUT_TAG = "DeceptionShootoutGun";

    /** 1 tembakan doang -- sekali tarik pelatuk, nasib murderer ditentuin di situ. */
    private static final int SHOTGUN_AMMO = 1;

    public static boolean isAvailable() {
        return ForgeRegistries.ITEMS.containsKey(SHOTGUN_ID);
    }

    /** @return ItemStack.EMPTY kalo CGM gak keinstall / item-nya gak ketemu. */
    public static ItemStack createLoadedShotgun() {
        Item item = ForgeRegistries.ITEMS.getValue(SHOTGUN_ID);
        if (item == null) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putInt(AMMO_COUNT_TAG, SHOTGUN_AMMO);
        stack.getOrCreateTag().putBoolean(SHOOTOUT_TAG, true);
        stack.setHoverName(Component.literal("Tembakan Terakhir").withStyle(ChatFormatting.DARK_RED));
        return stack;
    }

    public static boolean isShootoutGun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getTag() == null || !stack.getTag().getBoolean(SHOOTOUT_TAG)) return false;
        return SHOTGUN_ID.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /** Sisa peluru di stack gun-nya. 0 = udah ditembakin (atau emang gak pernah keisi). */
    public static int getAmmoCount(ItemStack stack) {
        if (stack.getTag() == null) return 0;
        return stack.getTag().getInt(AMMO_COUNT_TAG);
    }
}
