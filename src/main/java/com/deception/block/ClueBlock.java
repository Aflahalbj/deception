package com.deception.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * Block dekoratif buat 1 jenis clue/means (lempengan tipis, cuma nempel di
 * floor/wall/ceiling — polanya sama persis kayak ButtonBlock/LeverBlock
 * vanilla). Satu instance per clueId (lihat ModBlocks), texture/shape-nya
 * di-define di file JSON (blockstates + models), BUKAN kode Java, jadi gak
 * ada render code custom sama sekali.
 *
 * Catatan: model buat state WALL pakai file model TERPISAH (mis.
 * "clue_xxx_wall") yang udah di-mirror di sumbu Z dibanding model
 * floor/ceiling biasa. Ini buat nge-counter efek rotasi "x:90" bawaan
 * blockstate yang bikin gambar kebalik (nunduk) pas ditegakkan ke dinding —
 * lihat blockstates/*.json.
 */
public class ClueBlock extends Block {

    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // ketebalan lempengan dalam satuan block (3 pixel dari grid 16px)
    private static final float THICKNESS = 3F / 16F;

    private static final VoxelShape FLOOR_SHAPE = Block.box(0, 0, 0, 16, THICKNESS * 16, 16);
    private static final VoxelShape CEILING_SHAPE = Block.box(0, 16 - THICKNESS * 16, 0, 16, 16, 16);
    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 16 - THICKNESS * 16, 16, 16, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, THICKNESS * 16);
    private static final VoxelShape WEST_SHAPE = Block.box(16 - THICKNESS * 16, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(0, 0, 0, THICKNESS * 16, 16, 16);

    public ClueBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        AttachFace face = state.getValue(FACE);
        if (face == AttachFace.FLOOR) return FLOOR_SHAPE;
        if (face == AttachFace.CEILING) return CEILING_SHAPE;
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> FLOOR_SHAPE;
        };
    }

    // dekoratif doang, gak ada collision fisik (biar player bisa jalan
    // nembus, sama kayak karpet/karya seni di dinding)
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    /**
     * Placement ngikutin sisi yang di-klik PERSIS (bukan nyari nearest-look-
     * direction kayak button vanilla) — klik atas = floor, bawah = ceiling,
     * samping = wall nempel ke arah situ.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction playerFacing = context.getHorizontalDirection();
        if (clickedFace == Direction.UP) {
            return this.defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, playerFacing);
        } else if (clickedFace == Direction.DOWN) {
            return this.defaultBlockState().setValue(FACE, AttachFace.CEILING).setValue(FACING, playerFacing);
        } else {
            return this.defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, clickedFace);
        }
    }

    /**
     * Copot otomatis (drop jadi item) kalau block yang jadi tumpuannya
     * (lantai/dinding/langit-langit) hilang — sama kayak button/lever nempel.
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction attachedDir = getAttachedDirection(state).getOpposite();
        if (direction == attachedDir && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction attachedDir = getAttachedDirection(state).getOpposite();
        BlockPos supportPos = pos.relative(attachedDir);
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, attachedDir.getOpposite());
    }

    private Direction getAttachedDirection(BlockState state) {
        return switch (state.getValue(FACE)) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            default -> state.getValue(FACING);
        };
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
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }
}
