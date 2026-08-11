package com.deception.init;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Gambar icon police_badge di sebelah KIRI nametag player yang masih megang
 * badge-nya. Transform-nya niru PERSIS teknik Simple Voice Chat (lihat
 * de.maxhenkel.voicechat.voice.client.RenderEvents#renderPlayerIcon di
 * source SVC) -- event.getPoseStack() di RenderNameTagEvent itu BELUM ada
 * transform nametag vanilla sama sekali (translate/billboard/scale-nya
 * kudu dibikin sendiri dari nol), beda dari asumsi awal.
 */
public class PoliceBadgeNameTagRenderer {

    private static final ResourceLocation BADGE_TEXTURE =
            new ResourceLocation("deception", "textures/item/police_badge.png");

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        if (!PoliceBadgeClientState.hasBadge(entity.getUUID())) return;

        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();

        poseStack.pushPose();
        poseStack.translate(0D, player.getBbHeight() + 0.5D, 0D);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        poseStack.translate(0D, -1D, 0D);

        // Sisi kiri nametag: tepi kanan icon nempel ~2 unit di kiri tepi
        // kiri teks (teks di-draw center-origin sama vanilla).
        float rightEdge = -(mc.font.width(event.getContent()) / 2F) - 2F;
        float leftEdge = rightEdge - 10F;

        VertexConsumer consumer = buffer.getBuffer(RenderType.text(BADGE_TEXTURE));
        vertex(consumer, poseStack, leftEdge, 10F, 0F, 1F, light);
        vertex(consumer, poseStack, rightEdge, 10F, 1F, 1F, light);
        vertex(consumer, poseStack, rightEdge, 0F, 1F, 0F, light);
        vertex(consumer, poseStack, leftEdge, 0F, 0F, 0F, light);

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer builder, PoseStack poseStack, float x, float y, float u, float v, int light) {
        PoseStack.Pose pose = poseStack.last();
        builder.vertex(pose.pose(), x, y, 0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0F, 0F, -1F)
                .endVertex();
    }
}
