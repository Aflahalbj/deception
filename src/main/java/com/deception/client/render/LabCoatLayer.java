package com.deception.client.render;

import com.deception.DeceptionMod;
import com.deception.init.LabCoatClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Nempelin jas lab ke pemain yang lagi jadi Forensic Scientist. Daftar
 * siapa yang pake dikirim server (lihat network/LabCoatPacket) -- client
 * gak tau role siapa-siapa, dan emang gak boleh tau.
 *
 * <p>Bukan item armor, sengaja: kalo jasnya berupa item, dia harus dijagain
 * biar gak di-drop, gak dilepas, gak ke-swap di GUI, dan dibersihin pas game
 * selesai. Sebagai visual murni, gak satu pun dari itu perlu.
 */
public class LabCoatLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(DeceptionMod.MOD_ID, "textures/entity/lab_coat.png");

    private final LabCoatModel<AbstractClientPlayer> wideArms;
    private final LabCoatModel<AbstractClientPlayer> slimArms;

    public LabCoatLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                        EntityModelSet models) {
        super(parent);
        this.wideArms = new LabCoatModel<>(models.bakeLayer(ModRenderers.LAB_COAT));
        this.slimArms = new LabCoatModel<>(models.bakeLayer(ModRenderers.LAB_COAT_SLIM));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        if (!LabCoatClientState.isWearing(player.getUUID())) return;

        LabCoatModel<AbstractClientPlayer> model =
                "slim".equals(player.getModelName()) ? slimArms : wideArms;

        // Nyalin posisi, rotasi & skala tiap bagian dari model pemainnya --
        // termasuk titik putar lengan, yang beda antara skin normal & ramping.
        // Ini juga yang bikin jasnya gak butuh setupAnim sendiri.
        getParentModel().copyPropertiesTo(model);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
    }
}
