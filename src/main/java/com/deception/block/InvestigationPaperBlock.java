package com.deception.block;

import com.deception.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block dekoratif "kertas investigasi" (kertas + pin/benang, model 3D utuh
 * bikinan Blockbench -- di-render lewat vanilla block model biasa, lihat
 * models/block/investigation_paper.json).
 * Beda dari ClueBlock: block ini CUMA bisa nempel di TEMBOK (gak ada
 * floor/ceiling), soalnya modelnya emang didesain digantung tegak kayak di
 * papan detektif, bukan lempengan generik yang bisa segala arah.
 *
 * Orientasi model asalnya (tanpa rotasi tambahan) itu ngadep
 * FACING=SOUTH -- kertasnya nempel ke tembok di sisi UTARA block ini,
 * pin-nya nongol ke arah selatan (ke ruangan). Kalo pas dites in-game
 * gambarnya kebalik/muter salah, tinggal geser angka "y" per varian di
 * blockstates/investigation_paper.json (kelipatan 90).
 */
public class InvestigationPaperBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // ketebalan buat hitbox/outline doang (render aslinya full 3D obj, gak
    // kepengaruh sama shape ini -- collision-nya sendiri kosong, lihat di bawah)
    private static final float THICKNESS = 2F / 16F;

    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 16 - THICKNESS * 16, 16, 16, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, THICKNESS * 16);
    private static final VoxelShape WEST_SHAPE = Block.box(16 - THICKNESS * 16, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(0, 0, 0, THICKNESS * 16, 16, 16);

    public InvestigationPaperBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> SOUTH_SHAPE;
        };
    }

    // dekoratif doang, gak ada collision fisik -- sama kayak ClueBlock,
    // player bisa jalan nembus (kaya karpet/lukisan dinding)
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * Selalu nempel ke TEMBOK, gak peduli sisi mana yang diklik. Klik
     * atas/bawah (lantai/langit-langit) tetep dipasang ke tembok di
     * belakang player (arah horizontal player ngadep), bukan jadi
     * floor/ceiling kayak ClueBlock.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isHorizontal()) {
            return this.defaultBlockState().setValue(FACING, clickedFace);
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // Copot otomatis (drop jadi item) kalo tembok tumpuannya ilang
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction attachedDir = state.getValue(FACING).getOpposite();
        BlockPos supportPos = pos.relative(attachedDir);
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, attachedDir.getOpposite());
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

    // Bersihin TextDisplay hasil investigation paper (kalo ada) pas block-nya
    // beneran dicopot, biar gak ninggalin teks ngambang tanpa kertas.
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            GameManager.get().despawnInvestigationPaperText(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // Nyimpen kategori + jawaban (kalo ini paper "role" FS) buat
    // InvestigationPaperHoverOverlay -- lihat InvestigationPaperBlockEntity.
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InvestigationPaperBlockEntity(pos, state);
    }
}
