package com.deception.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Item buat taruh 1 jenis clue/means tertentu (clueId) sebagai ClueEntity
 * di floor/wall/ceiling, tergantung sisi block yang di-klik.
 */
public class ClueItem extends Item {

    private final String clueId;

    public ClueItem(String clueId, Properties properties) {
        super(properties);
        this.clueId = clueId;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockPos spawnPos = clickedPos.relative(clickedFace);

        int face;
        Direction facing;
        if (clickedFace == Direction.UP) {
            face = 0; // floor
            facing = context.getHorizontalDirection().getOpposite();
        } else if (clickedFace == Direction.DOWN) {
            face = 2; // ceiling
            facing = context.getHorizontalDirection().getOpposite();
        } else {
            face = 1; // wall
            facing = clickedFace;
        }

        ClueEntity entity = new ClueEntity(ModEntities.CLUE_ENTITY.get(), level);
        entity.setClueId(clueId);
        entity.setClueScale(1.0F);
        entity.setFace(face);
        entity.setFacing(facing);

        double x = spawnPos.getX() + 0.5;
        double y = spawnPos.getY();
        double z = spawnPos.getZ() + 0.5;
        if (face == 0) y = spawnPos.getY() + 0.02;
        if (face == 2) y = spawnPos.getY() + 0.98;
        entity.setPos(x, y, z);

        level.addFreshEntity(entity);

        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.CONSUME;
    }
}
