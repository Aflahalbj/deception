package com.deception.entity;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Entity dekoratif buat clue/means. Extends LivingEntity (bukan Entity biasa)
 * SUPAYA bisa jadi target vanilla /effect, contoh:
 *   /effect give @e[type=deception:clue_entity] minecraft:glowing infinite 1 true
 * Nempel di floor/wall/ceiling tergantung face yang di-klik pas ditaruh.
 * Divisualisasikan sebagai lempengan tipis (bukan paper-thin) setebal
 * THICKNESS_BLOCKS supaya gak keliatan kegepengan.
 */
public class ClueEntity extends LivingEntity {

    // tebal fisik dasar (sebelum dikali scale) = 3 pixel dari grid 16px = 3/16 blok
    public static final float THICKNESS_BLOCKS = 3F / 16F;

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
        this.setNoGravity(true);
        this.setInvulnerable(true); // biar gak bisa mati/ke-damage vanilla
    }

    // attribute wajib buat LivingEntity, di-daftarin lewat EntityAttributeCreationEvent
    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
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
    public boolean isAffectedByFluids() {
        return false;
    }

    // ---- method abstrak wajib dari LivingEntity, gak butuh equipment beneran ----

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ClueId", getClueId());
        tag.putFloat("ClueScale", getClueScale());
        tag.putByte("Face", (byte) getFace());
        tag.putByte("Facing", (byte) getFacing().get2DDataValue());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setClueId(tag.getString("ClueId"));
        setClueScale(tag.contains("ClueScale") ? tag.getFloat("ClueScale") : 1.0F);
        setFace(tag.getByte("Face"));
        setFacing(Direction.from2DDataValue(tag.getByte("Facing")));
    }
}