package com.deception.entity;

import com.deception.DeceptionMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Render ClueEntity dengan cara "numpang" ke ItemRenderer vanilla, BUKAN
 * bikin quad/geometry manual. Kenapa: model item 2D di Minecraft (parent
 * "item/generated") otomatis di-extrude ngikutin siluet pixel yang gak
 * transparan di texture-nya (ini yang bikin item kayak pedang/pickaxe
 * keliatan "tipis 3D" pas didrop/ditaruh di item frame). Jadi bentuk fisik
 * clue ini OTOMATIS ngikutin bentuk texture-nya, gak perlu kita hitung
 * geometry sendiri sama sekali — cukup render ItemStack yang clueId-nya
 * sesuai item yang udah didaftarin di ModItems.
 *
 * NAMETAG: sengaja BUKAN pake nametag billboard bawaan vanilla (yang selalu
 * ngambang & ngadep kamera). Nama item digambar manual di frame rotasi yang
 * SAMA kayak texture-nya, jadi rebah nempel di plane yang sama (floor/wall/
 * ceiling), otomatis mengecil/menyesuaikan biar muat di lebar 1 block.
 *
 * NOTE ROTASI: sudut rotasi di bawah (buat floor/wall/ceiling) itu tebakan
 * terbaik berdasarkan orientasi default item model vanilla. Kalau pas
 * ditest ternyata arah hadap/miringnya masih salah (misal kebalik atau
 * miring 90 derajat), tinggal kasih tau sudutnya kurang berapa derajat ke
 * arah mana, gampang banget di-tweak tanpa ubah struktur lain.
 */
public class ClueEntityRenderer extends EntityRenderer<ClueEntity> {

    // model item vanilla (item/generated) defaultnya cuma extrude ~1 pixel
    // ketebalannya. Kita stretch di sumbu kedalaman (lokal Z) biar jadi 3
    // pixel sesuai yang dimau, TANPA ngubah lebar/tinggi tampilan depannya.
    private static final float DEPTH_STRETCH = 3.0F;

    // lebar maksimum teks nama, dalam satuan block (biar ada margin dikit
    // dari tepi block, gak mepet banget ke ujung)
    private static final float MAX_TEXT_WIDTH_BLOCKS = 0.9F;

    public ClueEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ClueEntity entity) {
        // wajib di-override tapi gak dipake langsung buat gambar apa-apa;
        // texture asli-nya nempel di model item-nya sendiri (lewat ItemRenderer)
        return new ResourceLocation(DeceptionMod.MOD_ID, "textures/item/" + entity.getClueId() + ".png");
    }

    @Override
    protected boolean shouldShowName(ClueEntity entity) {
        // matiin nametag billboard bawaan; nama digambar manual di render()
        // biar rebah sesuai plane, bukan ngambang ngadep kamera
        return false;
    }

    @Override
    public void render(ClueEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(DeceptionMod.MOD_ID, entity.getClueId()));
        if (item == null) {
            // clueId belum kedaftar sebagai item di ModItems, gak ada yang bisa di-render
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }

        float scale = entity.getClueScale();
        int face = entity.getFace();
        Direction facing = entity.getFacing();

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);

        if (face == 1) {
            // wall: berdiri tegak, menghadap ke arah `facing` (keluar dari dinding)
            poseStack.translate(0, 0.5, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        } else {
            // floor/ceiling: rebah horizontal, TAPI tetep di-rotate ngikutin
            // `facing` (arah hadap player pas ditaruh) — sebelumnya kelewat,
            // makanya orientasinya selalu sama gak peduli arah player pas
            // nge-taruh
            boolean ceiling = face == 2;
            poseStack.translate(0, ceiling ? -0.02 : 0.02, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(ceiling ? 90 : -90));
        }

        // --- nama item, rebah di plane yang SAMA kayak texture (sebelum di-stretch) ---
        Component name = entity.getCustomName();
        if (name != null) {
            renderFlatName(name, poseStack, buffer, packedLight);
        }

        // stretch ketebalan (lokal Z, sumbu ini "aman" di-scale sendiri
        // karena scale diagonal, gak keganggu sama rotasi di atas)
        poseStack.scale(1F, 1F, DEPTH_STRETCH);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(item), ItemDisplayContext.FIXED,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), (int) entity.getId());

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * Gambar teks nama rebah di frame yang lagi aktif (udah ke-rotate sesuai
     * floor/wall/ceiling di render() di atas), otomatis di-scale kecil biar
     * muat di lebar MAX_TEXT_WIDTH_BLOCKS (jadi nama panjang tetep muat
     * dalam 1 block, bukan meluber keluar).
     */
    private void renderFlatName(Component name, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Font font = Minecraft.getInstance().font;
        int textWidthPx = font.width(name);
        if (textWidthPx <= 0) return;

        float textScale = Math.min(0.02F, MAX_TEXT_WIDTH_BLOCKS / (float) textWidthPx);

        poseStack.pushPose();
        // majuin lumayan jauh biar gak ke-occlude sama geometry icon (yang
        // ketebalannya udah di-stretch 3x, ~0.1875 block)
        poseStack.translate(0, 0, -0.06);
        poseStack.scale(textScale, textScale, textScale);
        poseStack.translate(-textWidthPx / 2.0, 0, 0);
        // SEE_THROUGH biar teks selalu keliatan (gak kena depth-test), sama
        // kayak cara nametag vanilla nembus tembok
        font.drawInBatch(name, 0, 0, 0xFFFFFF, false, poseStack.last().pose(), buffer,
                Font.DisplayMode.SEE_THROUGH, 0, packedLight);
        poseStack.popPose();
    }
}