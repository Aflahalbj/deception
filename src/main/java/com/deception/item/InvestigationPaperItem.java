package com.deception.item;

import com.deception.block.ClueBlockItem;
import com.deception.block.InvestigationPaperBlock;
import com.deception.block.InvestigationPaperBlockEntity;
import com.deception.game.ForensicPaperData;
import com.deception.game.GameManager;
import com.deception.game.PresentationManager;
import com.deception.init.ModBlocks;
import com.deception.network.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

        // Gate ronde 2/3 "gabisa naro sebelum hapus tile lama" ini SENGAJA
        // dibaca dari NBT stack-nya sendiri (bukan query state server-only
        // kayak PresentationManager langsung), soalnya method place() ini
        // jalan di client DAN server buat prediksi -- kalo sumber gate-nya
        // cuma ada di server, client bakal salah predict "boleh" & itemnya
        // keliatan abis dipake padahal server nolak taro (nyangkut ilang,
        // gak balik ke inventory).
        if (tag != null && tag.getBoolean(PresentationManager.PLACEMENT_LOCKED_TAG)) {
            if (context.getPlayer() instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal(
                        "Hapus salah satu scene tile dulu (pake Penghapus Scene Tile) sebelum naro clue baru.")
                        .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }

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
                if (context.getPlayer() instanceof ServerPlayer placer) {
                    PresentationManager.get().onForensicPaperPlaced(placer, serverLevel.getServer());
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
