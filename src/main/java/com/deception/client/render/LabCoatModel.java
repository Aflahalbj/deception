package com.deception.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Model jas lab (Forensic Scientist). Turunan HumanoidModel biar
 * transformasi tiap bagian bisa disalin mentah dari model pemain
 * (lihat LabCoatLayer) -- jadi jasnya ikut gerak, nunduk, dan ngayun persis
 * sama badan yang makenya, tanpa animasi sendiri.
 *
 * <p>Yang kepake: badan, dua lengan, dan dua kaki (bagian bawah jas).
 * Kepala didaftarin kosong -- HumanoidModel wajib nemu ketujuh nama itu
 * pas di-bake, walaupun jasnya gak nutupin kepala.
 */
public class LabCoatModel<T extends LivingEntity> extends HumanoidModel<T> {

    /** Ruang UV texture-nya. File PNG-nya boleh lebih gede (2x), asal proporsinya sama. */
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    /**
     * Setebal apa jasnya "ngembang" dari badan telanjang. Badan, lengan,
     * dan rok pakai angka yang SAMA -- itu yang bikin siluetnya lurus dari
     * bahu sampai bawah, bukan mengembang kayak rok.
     *
     * <p>0.3 itu tipis banget dan emang disengaja. Batas bawah teknisnya
     * ada di 0.25: itu ketebalan layer kedua skin pemain (jaket bawaan
     * skin). Kalau jasnya disamain atau lebih tipis dari itu, dua permukaan
     * bakal rebutan dan keliatan kedip-belang.
     */
    private static final CubeDeformation COAT_PADDING = new CubeDeformation(0.30F);

    /** Panjang bagian bawah jas, diukur dari pinggang (6 = sepaha). */
    private static final int SKIRT_LENGTH = 6;

    public LabCoatModel(ModelPart root) {
        super(root, RenderType::entityCutoutNoCull);
    }

    /**
     * @param slim skin lengan ramping (Alex) -- lengannya 3 px, bukan 4.
     *             Titik putarnya gak usah diurus di sini: itu ikut kesalin
     *             dari model pemain pas render.
     */
    public static LayerDefinition createLayer(boolean slim) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Kepala gak dipake, tapi wajib didaftarin -- constructor
        // HumanoidModel nyari nama-nama ini pas di-bake.
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        // Bagian bawah jas nempel di KAKI, bukan di badan -- jadi dia ngayun
        // ikut langkah dan kakinya gak akan pernah nembus keluar, beda sama
        // versi rok gantung sebelumnya.
        //
        // Sisi dalamnya sengaja gak dibikin nutup sendiri-sendiri: tiap
        // panel dilebarin sampai LEWAT garis tengah badan (0.3 px), jadi pas
        // kaki lagi rapat dua panelnya nyatu jadi satu permukaan lurus, bukan
        // dua pipa kayak celana. Bagian yang tumpang tindih ketutup satu sama
        // lain, jadi gak ada dinding dalam yang keliatan.
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, 0.0F, -2.0F, 4, SKIRT_LENGTH, 4, COAT_PADDING),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(16, 0).addBox(-2.0F, 0.0F, -2.0F, 4, SKIRT_LENGTH, 4, COAT_PADDING),
                PartPose.offset(1.9F, 12.0F, 0.0F));

        // Semua kotak di bawah sengaja digeser turun sampai ujung atasnya
        // (posisi Y dikurangi padding) TETAP di bawah garis bahu (y=0).
        //
        // Kenapa penting: jas ini lebih lebar dari kepala (8 + 2x padding vs
        // 8 px), jadi bagian mana pun yang naik melewati bahu bakal nongol
        // di samping leher sebagai rim putih -- persis kayak kerah chestplate
        // vanilla, yang emang naik 1 px ke atas bahu.
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0F, 0.35F, -2.0F, 8, 11.65F, 4, COAT_PADDING),
                PartPose.ZERO);

        // Lengan jas: berhenti sebelum pergelangan biar tangannya tetep
        // keliatan (jas lab emang gak nutupin telapak). Sama kayak badan,
        // ujung atasnya (-1.7 - 0.3) dipatok rata sama pundak, gak naik --
        // ujung bawahnya tetep di 8.0 kayak sebelumnya.
        if (slim) {
            root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                            .texOffs(0, 32).addBox(-2.0F, -1.7F, -2.0F, 3, 9.7F, 4, COAT_PADDING),
                    PartPose.offset(-5.0F, 2.5F, 0.0F));
            root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                            .texOffs(16, 32).addBox(-1.0F, -1.7F, -2.0F, 3, 9.7F, 4, COAT_PADDING),
                    PartPose.offset(5.0F, 2.5F, 0.0F));
        } else {
            root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                            .texOffs(40, 16).addBox(-3.0F, -1.7F, -2.0F, 4, 9.7F, 4, COAT_PADDING),
                    PartPose.offset(-5.0F, 2.0F, 0.0F));
            root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                            .texOffs(0, 16).addBox(-1.0F, -1.7F, -2.0F, 4, 9.7F, 4, COAT_PADDING),
                    PartPose.offset(5.0F, 2.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
