package com.deception.item;

import com.deception.block.ClueBlockItem;
import com.deception.block.InvestigationPaperBlock;
import com.deception.block.InvestigationPaperBlockEntity;
import com.deception.game.ForensicPaperData;
import com.deception.game.GameManager;
import com.deception.init.ModBlocks;
import com.deception.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockItem investigation_paper. Stack biasa (tanpa NBT "ForensicCategory")
 * tetep berperilaku dekoratif polos kayak biasa (langsung nempel ke tembok).
 * Stack "role paper" (dikasih ke Forensic Scientist lewat
 * GameManager#giveForensicScientistPapers, ditandain NBT "ForensicCategory")
 * malah buka ForensicPaperScreen dulu pas diklik kanan -- baru bisa
 * beneran ditempel ke tembok kalo udah ada NBT "ForensicChoice".
 */
public class InvestigationPaperItem extends ClueBlockItem {

    public InvestigationPaperItem(String clueId, Block block, Properties properties) {
        super(clueId, block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("ForensicCategory")) {
            if (!level.isClientSide) {
                openPicker(player, tag.getString("ForensicCategory"), hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("ForensicCategory") && !tag.contains("ForensicChoice")) {
            Player player = context.getPlayer();
            if (!context.getLevel().isClientSide && player != null) {
                openPicker(player, tag.getString("ForensicCategory"), context.getHand());
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }

    // BlockPlaceContext#getClickedPos() di sini itu posisi FINAL yang beneran
    // dipake buat naro block (udah di-resolve vanilla lewat constructor-nya --
    // geser ke clickedPos.relative(clickedFace) kalo block yang diklik gak
    // replaceable, atau tetep di clickedPos kalo replaceable kayak rumput).
    // Jangan itung ulang manual di useOn() -- gampang salah pas placement-nya
    // gak simpel (misal ngeklik deket paper lain yang udah ke-pasang).
    @Override
    public InteractionResult place(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getTag();
        String category = (tag != null) ? tag.getString("ForensicCategory") : null;
        String chosenText = (tag != null && tag.contains("ForensicChoice")) ? tag.getString("ForensicChoice") : null;

        InteractionResult result = super.place(context);
        if (chosenText != null && result.consumesAction() && context.getLevel() instanceof ServerLevel serverLevel) {
            BlockPos pos = context.getClickedPos();
            BlockState placedState = serverLevel.getBlockState(pos);
            if (placedState.is(ModBlocks.INVESTIGATION_PAPER.get())) {
                Direction facing = placedState.getValue(InvestigationPaperBlock.FACING);
                GameManager.get().spawnInvestigationPaperText(serverLevel, pos, facing, chosenText);
                if (serverLevel.getBlockEntity(pos) instanceof InvestigationPaperBlockEntity paperEntity) {
                    paperEntity.setCategoryAndChoice(category, chosenText);
                }
            }
        }
        return result;
    }

    private void openPicker(Player player, String category, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ForensicPaperData.Category data = ForensicPaperData.findByDisplayName(category);
        if (data == null) return;
        ModNetworking.sendOpenForensicPicker(serverPlayer, category, data.options(), hand);
    }
}
