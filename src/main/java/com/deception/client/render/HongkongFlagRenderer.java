package com.deception.client.render;

import com.deception.DeceptionMod;
import com.deception.block.HongkongFlagBlock;
import com.deception.block.HongkongFlagBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Gambar kain bendera Hong Kong-nya. Kainnya BUKAN block model: dia mesh
 * yang dibangun ulang tiap frame (grid COLS x ROWS quad) terus tiap titik
 * digeser pake gelombang sinus, jadi keliatan berkibar ketiup angin.
 *
 * Kenapa gak pake block model + animasi texture: model JSON itu statis,
 * gak bisa dibengkokin, dan kain 3x2 block gak muat di dalam 1 block.
 *
 * Bentuk gelombangnya: amplitudo nol pas di tiang terus naik makin ke
 * ujung (u^1.5), soalnya sisi yang keiket tiang emang gak bisa gerak.
 */
public class HongkongFlagRenderer implements BlockEntityRenderer<HongkongFlagBlockEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(DeceptionMod.MOD_ID, "textures/entity/flag_hongkong.png");

    // Kerapatan mesh. Makin gede makin mulus lengkungannya, tapi makin
    // banyak quad yang dikirim tiap frame -- 24x10 udah mulus banget buat
    // kain 3x2 block.
    private static final int COLS = 24;
    private static final int ROWS = 10;

    private static final float WAVE_SPEED = 0.16F;      // radian per tick
    private static final float WAVE_LENGTH = 5.0F;      // jumlah gelombang sepanjang kain
    private static final float WAVE_AMPLITUDE = 0.42F;  // simpangan max (block) di ujung kain
    private static final float SAG = 0.10F;             // kain ngegantung dikit ke bawah di ujung

    // Posisi tiap titik grid (x,y,z) + normal-nya, dipake ulang tiap frame
    // biar gak bikin sampah buat GC. Aman karena BER selalu dipanggil dari
    // render thread satu-satu.
    private final float[] px = new float[(COLS + 1) * (ROWS + 1)];
    private final float[] py = new float[(COLS + 1) * (ROWS + 1)];
    private final float[] pz = new float[(COLS + 1) * (ROWS + 1)];
    private final float[] nx = new float[(COLS + 1) * (ROWS + 1)];
    private final float[] ny = new float[(COLS + 1) * (ROWS + 1)];
    private final float[] nz = new float[(COLS + 1) * (ROWS + 1)];
    private final int[] colLight = new int[COLS + 1];

    public HongkongFlagRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HongkongFlagBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) return;

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof HongkongFlagBlock)) return;

        Direction facing = state.getValue(HongkongFlagBlock.FACING);
        float dirX = facing.getStepX();
        float dirZ = facing.getStepZ();
        // arah tegak lurus kain (normal pas kain lagi rata) = FACING diputar 90 derajat
        float perpX = -dirZ;
        float perpZ = dirX;

        float time = (level.getGameTime() % 24000L + partialTick) * WAVE_SPEED;

        buildMesh(dirX, dirZ, perpX, perpZ, time);
        sampleLight(level, be.getBlockPos(), dirX, dirZ, packedLight);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        for (int i = 0; i < COLS; i++) {
            float u0 = (float) i / COLS;
            float u1 = (float) (i + 1) / COLS;
            int lightL = colLight[i];
            int lightR = colLight[i + 1];

            for (int j = 0; j < ROWS; j++) {
                float v0 = (float) j / ROWS;
                float v1 = (float) (j + 1) / ROWS;

                int a = idx(i, j);
                int b = idx(i, j + 1);
                int c = idx(i + 1, j + 1);
                int d = idx(i + 1, j);

                // muka depan
                vertex(consumer, matrix, normalMatrix, a, u0, v0, lightL, 1F);
                vertex(consumer, matrix, normalMatrix, b, u0, v1, lightL, 1F);
                vertex(consumer, matrix, normalMatrix, c, u1, v1, lightR, 1F);
                vertex(consumer, matrix, normalMatrix, d, u1, v0, lightR, 1F);

                // muka belakang: urutan dibalik + normal dibalik, biar sisi
                // belakangnya kena cahaya bener (bukan gelap) walaupun
                // render type-nya udah no-cull
                vertex(consumer, matrix, normalMatrix, d, u1, v0, lightR, -1F);
                vertex(consumer, matrix, normalMatrix, c, u1, v1, lightR, -1F);
                vertex(consumer, matrix, normalMatrix, b, u0, v1, lightL, -1F);
                vertex(consumer, matrix, normalMatrix, a, u0, v0, lightL, -1F);
            }
        }
    }

    /** Hitung posisi + normal tiap titik grid buat frame ini. */
    private void buildMesh(float dirX, float dirZ, float perpX, float perpZ, float time) {
        float width = HongkongFlagBlock.CLOTH_WIDTH;
        float height = HongkongFlagBlock.CLOTH_HEIGHT;
        float baseX = 0.5F + dirX * HongkongFlagBlock.POLE_RADIUS;
        float baseZ = 0.5F + dirZ * HongkongFlagBlock.POLE_RADIUS;

        for (int i = 0; i <= COLS; i++) {
            float u = (float) i / COLS;                     // 0 di tiang, 1 di ujung kain
            float s = u * width;
            float amp = WAVE_AMPLITUDE * u * (float) Math.sqrt(u);

            for (int j = 0; j <= ROWS; j++) {
                float v = (float) j / ROWS;                 // 0 di atas, 1 di bawah
                float phase = u * WAVE_LENGTH - time + v * 0.8F;
                float swing = amp * (float) Math.sin(phase);
                // ujung kain ngegantung + ikut naik-turun dikit, biar
                // gerakannya gak cuma geser kiri-kanan doang
                float lift = -SAG * u * u + 0.08F * u * u * (float) Math.sin(phase * 0.7F + 0.9F);

                int k = idx(i, j);
                px[k] = baseX + dirX * s + perpX * swing;
                py[k] = HongkongFlagBlock.CLOTH_TOP - v * height + lift;
                pz[k] = baseZ + dirZ * s + perpZ * swing;
            }
        }

        for (int i = 0; i <= COLS; i++) {
            for (int j = 0; j <= ROWS; j++) {
                int k = idx(i, j);
                int kU0 = idx(Math.max(i - 1, 0), j);
                int kU1 = idx(Math.min(i + 1, COLS), j);
                int kV0 = idx(i, Math.max(j - 1, 0));
                int kV1 = idx(i, Math.min(j + 1, ROWS));

                float tux = px[kU1] - px[kU0], tuy = py[kU1] - py[kU0], tuz = pz[kU1] - pz[kU0];
                float tvx = px[kV1] - px[kV0], tvy = py[kV1] - py[kV0], tvz = pz[kV1] - pz[kV0];

                float cx = tvy * tuz - tvz * tuy;
                float cy = tvz * tux - tvx * tuz;
                float cz = tvx * tuy - tvy * tux;
                float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
                if (len < 1.0E-5F) {
                    nx[k] = 0F; ny[k] = 1F; nz[k] = 0F;
                } else {
                    nx[k] = cx / len; ny[k] = cy / len; nz[k] = cz / len;
                }
            }
        }
    }

    /**
     * Ambil level cahaya per kolom kain (bukan per titik) -- kainnya
     * menjulur sampai 3 block dari tiangnya, jadi kalo semuanya dikasih
     * cahaya block tiang doang, ujung yang lagi di bawah bayangan bakal
     * keliatan salah terang.
     */
    private void sampleLight(Level level, BlockPos pos, float dirX, float dirZ, int fallback) {
        double midY = pos.getY() + HongkongFlagBlock.CLOTH_TOP - HongkongFlagBlock.CLOTH_HEIGHT * 0.5D;
        for (int i = 0; i <= COLS; i++) {
            float s = (float) i / COLS * HongkongFlagBlock.CLOTH_WIDTH;
            BlockPos sample = BlockPos.containing(
                    pos.getX() + 0.5D + dirX * s,
                    midY,
                    pos.getZ() + 0.5D + dirZ * s);
            colLight[i] = level.isLoaded(sample) ? LevelRenderer.getLightColor(level, sample) : fallback;
        }
    }

    private void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normalMatrix,
                        int k, float u, float v, int light, float normalSign) {
        consumer.vertex(matrix, px[k], py[k], pz[k])
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normalMatrix, nx[k] * normalSign, ny[k] * normalSign, nz[k] * normalSign)
                .endVertex();
    }

    private static int idx(int i, int j) {
        return i * (ROWS + 1) + j;
    }

    /** Bendera segede ini harus keliatan dari jauh, bukan cuma 64 block. */
    @Override
    public int getViewDistance() {
        return 160;
    }

    /** Kainnya sering nongol duluan sebelum block tiangnya masuk layar. */
    @Override
    public boolean shouldRenderOffScreen(HongkongFlagBlockEntity be) {
        return true;
    }
}
