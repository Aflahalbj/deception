package com.deception.init;

import com.deception.block.ClueBlock;
import com.deception.game.GameManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HUD baru: pas player nge-hover (lihat) SALAH SATU block di sebuah cluster
 * means/clue milik player lain -- walaupun dari jauh (raytrace-nya sengaja
 * dibikin sampai {@link #MAX_HOVER_DISTANCE} block, jauh lebih panjang dari
 * reach distance normal, dan pakai outline shape jadi gak kepengaruh sama
 * noCollission() di ClueBlock) -- muncul panel di tengah atas layar:
 * <p>
 * baris paling atas = kartu semua MEANS milik cluster itu (bg merah),
 * di tengah = kepala + nama player pemilik cluster,
 * baris paling bawah = kartu semua CLUE milik cluster itu (bg biru).
 * <p>
 * Gak butuh data tambahan dari server: cluster "means row" & "clue row"
 * dideteksi dengan nyusurin block yang sama-sama instance ClueBlock secara
 * horizontal dari titik yang di-hover (berhenti begitu ketemu bukan
 * ClueBlock -- itu tandanya udah lewat dari cluster ini ke gap/cluster
 * sebelah), dan baris pasangannya (means<->clue) tinggal 1 block di atas
 * atau di bawahnya (lihat MEANS_Y/CLUE_Y di GameManager). Nama & kepala
 * pemilik didapat dari entity item_display/text_display yang udah dispawn
 * GameManager#spawnOwnerHead di posisi cluster itu (entity ini otomatis
 * ke-sync ke client kayak entity lain, gak perlu packet custom).
 */
public class ClusterHoverOverlay {

    private static final double MAX_HOVER_DISTANCE = 48.0;

    private static final int MEANS_BG = 0xCCB33A3A; // merah translucent
    private static final int CLUE_BG = 0xCC2F5FA8;  // biru translucent
    private static final int CARD_PADDING = 4;
    private static final int ICON_SIZE = 16;
    private static final int CARD_GAP = 6;

    /** Reverse lookup Block -> clueId, biar tau prefix "means_"/"clue_" dari block yang dihover. */
    private static final Map<Block, String> BLOCK_TO_ID = new HashMap<>();

    static {
        for (Map.Entry<String, RegistryObject<Block>> entry : ModBlocks.CLUE_BLOCKS.entrySet()) {
            BLOCK_TO_ID.put(entry.getValue().get(), entry.getKey());
        }
    }

    private record HoverInfo(String ownerName, ItemStack headStack,
                              List<ItemStack> meansStacks, List<ItemStack> clueStacks) {
    }

    // di-refresh tiap client tick (20x/detik), dibaca ulang tiap frame render
    // pas nge-render overlay -- biar raytrace + scan block gak jalan tiap
    // frame (bisa 60-240fps), cukup segampang tick server/client biasa.
    private static volatile HoverInfo cached = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        cached = computeHover();
    }

    private static HoverInfo computeHover() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        // fitur ini cuma relevan pas ada cluster kepasang di arena (DISCUSS/RUNNING);
        // di state lain gak ada ClueBlock di dunia jadi raytrace-nya otomatis miss.

        if (!(mc.hitResult instanceof BlockHitResult hit))
            return null;

        BlockPos hitPos = hit.getBlockPos();
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        System.out.println(hitPos);
        System.out.println(mc.level.getBlockState(hitPos).getBlock());
        Block hitBlock = mc.level.getBlockState(hitPos).getBlock();
        if (!(hitBlock instanceof ClueBlock)) return null;

        String hitId = BLOCK_TO_ID.get(hitBlock);
        if (hitId == null) return null;
        boolean hitIsMeans = hitId.startsWith("means_");

        // tentuin sumbu horizontal cluster (dinding kiri/kanan jalan di X,
        // dinding belakang jalan di Z) dengan ngecek tetangga mana yang juga
        // ClueBlock di baris yang sama.
        Direction axisPos;
        Direction axisNeg;

        if (isClueBlockAt(mc.level, hitPos.east()) ||
            isClueBlockAt(mc.level, hitPos.west())) {

            axisPos = Direction.EAST;
            axisNeg = Direction.WEST;

        } else {

            axisPos = Direction.SOUTH;
            axisNeg = Direction.NORTH;
        }

        BlockState hitState = mc.level.getBlockState(hitPos);
        Direction blockFacing = hitState.getValue(ClueBlock.FACING);

        List<BlockPos> hitRow = collectContiguousRow(
                mc.level,
                hitPos,
                axisPos,
                axisNeg,
                blockFacing
        );

        int meansY = hitIsMeans ? hitPos.getY() : hitPos.getY() + 1;
        int clueY = meansY - 1;

        List<BlockPos> meansPositions = new ArrayList<>();
        List<BlockPos> cluePositions = new ArrayList<>();
        for (BlockPos p : hitRow) {
            meansPositions.add(new BlockPos(p.getX(), meansY, p.getZ()));
            cluePositions.add(new BlockPos(p.getX(), clueY, p.getZ()));
        }

        List<ItemStack> meansStacks = toStacks(mc.level, meansPositions);
        List<ItemStack> clueStacks = toStacks(mc.level, cluePositions);
        if (meansStacks.isEmpty() && clueStacks.isEmpty()) return null;

        // titik tengah cluster (rata-rata X/Z baris yang di-scan), buat
        // nyari entity kepala + nama player pemilik yang paling deket.
        double sumX = 0;
        double sumZ = 0;
        for (BlockPos p : hitRow) {
            sumX += p.getX();
            sumZ += p.getZ();
        }
        double centerX = sumX / hitRow.size() + 0.5;
        double centerZ = sumZ / hitRow.size() + 0.5;
        double headY = meansY + 1;

        AABB searchBox = new AABB(centerX - 2.5, headY - 1.0, centerZ - 2.5, centerX + 2.5, headY + 2.0, centerZ + 2.5);

        ItemStack headStack = ItemStack.EMPTY;
        double bestHeadDist = Double.MAX_VALUE;

        for (Display.ItemDisplay itemDisplay : mc.level.getEntitiesOfClass(Display.ItemDisplay.class, searchBox)) {

            CompoundTag tag = itemDisplay.saveWithoutId(new CompoundTag());

            if (!tag.contains("item")) continue;

            CompoundTag itemTag = tag.getCompound("item");

            ItemStack stack = ItemStack.of(itemTag);

            if (!stack.is(Items.PLAYER_HEAD)) continue;

            double dist = itemDisplay.position().distanceToSqr(centerX, headY, centerZ);

            if (dist < bestHeadDist) {
                bestHeadDist = dist;
                headStack = stack;
            }
        }

        String ownerName = "";
        double bestNameDist = Double.MAX_VALUE;
        for (Display.TextDisplay display : mc.level.getEntitiesOfClass(Display.TextDisplay.class, searchBox)) {
            double dist = display.position().distanceToSqr(centerX, headY, centerZ);

            if (dist < bestNameDist) {
                bestNameDist = dist;

                CompoundTag tag = display.saveWithoutId(new CompoundTag());

                if (tag.contains("text")) {
                    String json = tag.getString("text");
                    Component comp = Component.Serializer.fromJson(json);

                    if (comp != null) {
                        ownerName = comp.getString();
                    }
                }
            }
        }
        System.out.println("Hover = " + ownerName +
            " means=" + meansStacks.size() +
            " clue=" + clueStacks.size());
        System.out.println(BLOCK_TO_ID.size());
        return new HoverInfo(ownerName, headStack, meansStacks, clueStacks);
    }

    private static boolean isClueBlockAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ClueBlock;
    }

    /** Susurin block ClueBlock yang nyambung (kiri & kanan) dari titik start, berhenti pas nemu bukan ClueBlock. */
    private static List<BlockPos> collectContiguousRow(
            Level level,
            BlockPos start,
            Direction posDir,
            Direction negDir,
            Direction facing) {

        List<BlockPos> list = new ArrayList<>();
        list.add(start);

        BlockPos cursor = start;

        while (isClueBlockAt(level, cursor.relative(posDir))) {
            cursor = cursor.relative(posDir);
            list.add(cursor);
        }

        cursor = start;

        while (isClueBlockAt(level, cursor.relative(negDir))) {
            cursor = cursor.relative(negDir);
            list.add(cursor);
        }

        list.sort((a, b) -> {
            if (posDir == Direction.EAST || posDir == Direction.WEST) {
                return Integer.compare(a.getX(), b.getX());
            } else {
                return Integer.compare(a.getZ(), b.getZ());
            }
        });


        if (facing == Direction.NORTH || facing == Direction.EAST) {
            java.util.Collections.reverse(list);
        }

        return list;
    }

    private static List<ItemStack> toStacks(Level level, List<BlockPos> positions) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockPos p : positions) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof ClueBlock clueBlock) {
                stacks.add(new ItemStack(clueBlock.asItem()));
            }
        }
        return stacks;
    }

    // ---------- Render ----------

    public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        HoverInfo info = cached;
        if (info == null) return;

        int centerX = screenWidth / 2;
        int y = 8;

        List<ItemStack> allItems = new ArrayList<>();

        // means dulu
        allItems.addAll(info.meansStacks());

        // kepala player di tengah
        if (!info.headStack().isEmpty()) {
            allItems.add(info.headStack());
        }

        // clue terakhir
        allItems.addAll(info.clueStacks());

        drawSingleRow(guiGraphics, mc, allItems, info.meansStacks().size(), centerX, y);
    };

    /** Gambar 1 baris kartu (icon + nama, bg warna) yang di-center secara horizontal di centerX. Return tinggi baris. */
    private static int drawCardRow(GuiGraphics gui, Minecraft mc, List<ItemStack> items, int bgColor, int centerX, int topY) {
        int count = items.size();
        List<Component> names = new ArrayList<>(count);
        int[] cardWidths = new int[count];

        for (int i = 0; i < count; i++) {
            Component name = items.get(i).getHoverName();
            names.add(name);
            int textWidth = mc.font.width(name);
            cardWidths[i] = Math.max(ICON_SIZE, textWidth) + CARD_PADDING * 2;
        }

        int totalWidth = (count - 1) * CARD_GAP;
        for (int w : cardWidths) totalWidth += w;

        int cardHeight = ICON_SIZE + mc.font.lineHeight + CARD_PADDING * 2 + 2;
        int x = centerX - totalWidth / 2;

        for (int i = 0; i < count; i++) {
            int w = cardWidths[i];

            gui.fill(x, topY, x + w, topY + cardHeight, bgColor);

            int iconX = x + (w - ICON_SIZE) / 2;
            int iconY = topY + CARD_PADDING;
            gui.renderItem(items.get(i), iconX, iconY);

            Component name = names.get(i);
            int textWidth = mc.font.width(name);
            int textX = x + (w - textWidth) / 2;
            int textY = iconY + ICON_SIZE + 2;
            gui.drawString(mc.font, name, textX, textY, 0xFFFFFF, true);

            x += w + CARD_GAP;
        }

        return cardHeight;
    }

    private static void drawSingleRow(
            GuiGraphics gui,
            Minecraft mc,
            List<ItemStack> items,
            int meansCount,
            int centerX,
            int y) {

        int cardSize = 45;
        int gap = 4;

        int totalWidth = items.size() * cardSize + (items.size() - 1) * gap;

        int x = centerX - totalWidth / 2;


        for (int i = 0; i < items.size(); i++) {

            ItemStack stack = items.get(i);

            boolean isHead = stack.is(Items.PLAYER_HEAD);

            int bg;

            if (isHead) {

                var pose = gui.pose();

                pose.pushPose();

                pose.translate(
                        x + cardSize / 2,
                        y + cardSize / 2,
                        0
                );

                pose.scale(2.5F, 2.5F, 1);

                gui.renderItem(
                        stack,
                        -8,
                        -8
                );

                pose.popPose();

                x += cardSize + gap;
                continue;
            }
            else if (i < meansCount) {
                bg = MEANS_BG;
            }
            else {
                bg = CLUE_BG;
            }


            // background card
            gui.fill(
                    x,
                    y,
                    x + cardSize,
                    y + cardSize,
                    bg
            );


            // icon
            gui.renderItem(
                    stack,
                    x + 14,
                    y + 4
            );


            // nama item
            String text = stack.getHoverName().getString()
                    .replace("Means: ", "")
                    .replace("Clue: ", "");

            String[] words = text.split(" ");

            int textY = y + 25;


            for (String word : words) {

                int textWidth = mc.font.width(word);

                gui.drawString(
                        mc.font,
                        word,
                        x + (cardSize - textWidth) / 2,
                        textY,
                        0xFFFFFF,
                        true
                );

                textY += 8;
            }


            x += cardSize + gap;
        }
    }

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("cluster_hover_panel", HUD);
    }
}