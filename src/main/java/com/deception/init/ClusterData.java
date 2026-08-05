package com.deception.init;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

public class ClusterData {

    public final UUID owner;
    public final String ownerName;
    public final BlockPos center;
    public final List<ItemStack> means;
    public final List<ItemStack> clues;

    public ClusterData(
            UUID owner,
            String ownerName,
            BlockPos center,
            List<ItemStack> means,
            List<ItemStack> clues
    ) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.center = center;
        this.means = means;
        this.clues = clues;
    }
}