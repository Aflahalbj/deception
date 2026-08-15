package com.deception.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ujung tiang + bendera Hong Kong yang berkibar. Block-nya sendiri cuma 1
 * block (ruas tiang paling atas + kenop emas, lihat
 * models/block/hongkong_flag.json); KAIN-nya bukan block sama sekali --
 * digambar tiap frame sama HongkongFlagRenderer, ukuran 3 block lebar x 2
 * block tinggi, menggantung dari puncak tiang ke arah FACING.
 *
 * Dipasang di ruas paling atas FlagPoleBlock. Nempel di block lain juga
 * boleh (gak ada canSurvive) -- kainnya cuma visual, jadi gak ada yang
 * rusak kalo dipasang nempel tembok atau ngambang.
 *
 * FACING = arah kain memanjang. Pas dipasang, arahnya diputar searah jarum
 * jam dari arah pandang player, jadi MUKA bendera langsung ngadep ke orang
 * yang masang (bukan keliatan dari samping/tipis).
 */
public class HongkongFlagBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Ukuran kain dalam block -- dipake HongkongFlagRenderer. */
    public static final float CLOTH_WIDTH = 3.0F;
    public static final float CLOTH_HEIGHT = 2.0F;

    /** Setengah tebal batang tiang (4 px / 2 = 2 px), pangkal kain nempel di sini. */
    public static final float POLE_RADIUS = 2.0F / 16.0F;

    /** Tinggi pangkal kain, pas di bawah kenop emas (14 px, lihat model JSON-nya). */
    public static final float CLOTH_TOP = 14.0F / 16.0F;

    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

    public HongkongFlagBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HongkongFlagBlockEntity(pos, state);
    }
}
