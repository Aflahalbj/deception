package com.deception.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * BlockItem yang nama tampilnya reuse translation key "item.deception.<id>"
 * yang udah ada (bukan "block.deception.<id>" default BlockItem), biar gak
 * perlu duplikasi 290 baris lang file cuma buat block.
 */
public class ClueBlockItem extends BlockItem {

    private final String clueId;

    public ClueBlockItem(String clueId, Block block, Properties properties) {
        super(block, properties);
        this.clueId = clueId;
    }

    @Override
    public String getDescriptionId() {
        return "item.deception." + clueId;
    }
}