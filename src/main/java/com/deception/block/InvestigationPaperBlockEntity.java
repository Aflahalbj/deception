package com.deception.block;

import com.deception.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Nyimpen kategori + jawaban yang udah dipilih FS buat investigation paper
 * yang UDAH DITEMPEL (kertas kosong yang masih di tangan cuma nyimpen ini
 * di NBT item, belum ada block entity). Dipake InvestigationPaperHoverOverlay
 * buat nampilin "Kategori: Jawaban" pas player ngeliat block-nya.
 */
public class InvestigationPaperBlockEntity extends BlockEntity {

    private String category = "";
    private String choice = "";

    public InvestigationPaperBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INVESTIGATION_PAPER.get(), pos, state);
    }

    public void setCategoryAndChoice(String category, String choice) {
        this.category = category;
        this.choice = choice;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public String getCategory() {
        return category;
    }

    public String getChoice() {
        return choice;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Category", category);
        tag.putString("Choice", choice);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        category = tag.getString("Category");
        choice = tag.getString("Choice");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
