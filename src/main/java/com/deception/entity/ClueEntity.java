package com.deception.entity;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Entity dekoratif buat clue/means. Ganti dari sistem block lama supaya:
 * - bisa di-resize (scale) lewat /resizeclue
 * - icon di inventory flat 2D (item/generated), bukan render block 3D
 * Nempel di floor/wall/ceiling tergantung face yang di-klik pas ditaruh.
 */
public class ClueEntity extends Entity {

    // 0 = floor, 1 = wall, 2 = ceiling
    private static final EntityDataAccessor<String> CLUE_ID =
            SynchedEntityData.defineId(ClueEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(ClueEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> FACE =
            SynchedEntityData.defineId(ClueEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> FACING =
            SynchedEntityData.defineId(ClueEntity.class, EntityDataSerializers.BYTE);

    public ClueEntity(EntityType<? extends ClueEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(CLUE_ID, "");
        this.entityData.define(SCALE, 1.0F);
        this.entityData.define(FACE, (byte) 0);
        this.entityData.define(FACING, (byte) 0);
    }

    public void setClueId(String id) {
        this.entityData.set(CLUE_ID, id);
    }

    public String getClueId() {
        return this.entityData.get(CLUE_ID);
    }

    public void setClueScale(float scale) {
        this.entityData.set(SCALE, scale);
    }

    public float getClueScale() {
        return this.entityData.get(SCALE);
    }

    public void setFace(int face) {
        this.entityData.set(FACE, (byte) face);
    }

    public int getFace() {
        return this.entityData.get(FACE);
    }

    public void setFacing(Direction dir) {
        this.entityData.set(FACING, (byte) dir.get2DDataValue());
    }

    public Direction getFacing() {
        return Direction.from2DDataValue(this.entityData.get(FACING));
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("ClueId", getClueId());
        tag.putFloat("ClueScale", getClueScale());
        tag.putByte("Face", (byte) getFace());
        tag.putByte("Facing", (byte) getFacing().get2DDataValue());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setClueId(tag.getString("ClueId"));
        setClueScale(tag.contains("ClueScale") ? tag.getFloat("ClueScale") : 1.0F);
        setFace(tag.getByte("Face"));
        setFacing(Direction.from2DDataValue(tag.getByte("Facing")));
    }
}
