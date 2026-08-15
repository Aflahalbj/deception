package com.deception.block;

import com.deception.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Block entity kosong -- gak nyimpen data apa pun, cuma "kail" biar
 * HongkongFlagRenderer kepanggil tiap frame buat gambar kainnya (block
 * model biasa gak bisa dianimasiin).
 */
public class HongkongFlagBlockEntity extends BlockEntity {

    public HongkongFlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HONGKONG_FLAG.get(), pos, state);
    }

    /**
     * Kainnya jauh lebih gede dari block-nya (3 block ke samping, 2 block ke
     * bawah). Tanpa kotak yang dilebarin, frustum culling bakal ngilangin
     * benderanya begitu block tiangnya sendiri keluar layar.
     */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(getBlockPos()).inflate(HongkongFlagBlock.CLOTH_WIDTH + 1.0D);
    }
}
