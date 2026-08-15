package com.deception.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tiang bendera -- block dekoratif berdiri sendiri, dibikin buat DITUMPUK
 * setinggi apa pun. HongkongFlagBlock dipasang di ruas paling atas: model
 * batangnya sengaja dibikin sama persis (6..10 px, tengah block) biar
 * sambungannya mulus dan keliatan satu tiang utuh.
 */
public class FlagPoleBlock extends Block {

    // Batang 4x4 px di tengah block. Angka yang sama dipake
    // HongkongFlagBlock (POLE_RADIUS) buat naro pangkal kainnya.
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    public FlagPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
