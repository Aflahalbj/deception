package com.deception.entity;

import com.deception.DeceptionMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Gambar entity clue sebagai quad datar (2 sisi) memakai texture item-nya,
 * diposisikan menempel floor/wall/ceiling dan di-scale sesuai getClueScale().
 * NOTE: bagian ini paling berisiko beda signature antar mapping — kalau
 * gradle compile error di sini, kemungkinan besar cuma perlu benerin nama
 * method vertex builder (biasa beda dikit per versi Forge/MCP).
 */
public class ClueEntityRenderer extends EntityRenderer<ClueEntity> {

    public ClueEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ClueEntity entity) {
        return new ResourceLocation(DeceptionMod.MOD_ID, "textures/item/" + entity.getClueId() + ".png");
    }

    @Override
    public void render(ClueEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        float scale = entity.getClueScale();
        int face = entity.getFace();
        Direction facing = entity.getFacing();

        if (face == 0) {
            // floor: datar di atas lantai
            poseStack.translate(0.0, 0.02, 0.0);
        } else if (face == 2) {
            // ceiling: datar di bawah langit-langit, dibalik
            poseStack.translate(0.0, 0.98, 0.0);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
        } else {
            // wall: berdiri tegak nempel dinding, menghadap keluar sesuai facing
            poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            poseStack.translate(0.0, 0.5, -0.48);
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.translate(0.0, -0.5, 0.0);
        }

        poseStack.scale(scale, scale, scale);

        VertexConsumer buf = buffer.getBuffer(RenderType.entityCutout(getTextureLocation(entity)));
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMat = poseStack.last().normal();

        drawQuad(buf, matrix, normalMat, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void drawQuad(VertexConsumer buf, Matrix4f matrix, Matrix3f normalMat, int light) {
        float x0 = -0.5F, x1 = 0.5F, z0 = -0.5F, z1 = 0.5F;
        vertex(buf, matrix, normalMat, x0, 0, z1, 0, 1, light);
        vertex(buf, matrix, normalMat, x1, 0, z1, 1, 1, light);
        vertex(buf, matrix, normalMat, x1, 0, z0, 1, 0, light);
        vertex(buf, matrix, normalMat, x0, 0, z0, 0, 0, light);
    }

    private void vertex(VertexConsumer buf, Matrix4f matrix, Matrix3f normalMat, float x, float y, float z, float u, float v, int light) {
        buf.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normalMat, 0, 1, 0)
                .endVertex();
    }
}
