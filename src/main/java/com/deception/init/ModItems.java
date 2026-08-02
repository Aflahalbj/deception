package com.deception.init;

import com.deception.DeceptionMod;
import com.deception.block.ClueBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry item "pegangan" buat tiap block clue/means (BlockItem, bukan
 * item biasa) — daftar ID sumber-nya ada di ModBlocks.CLUE_IDS, satu block
 * satu item. Ini juga tempat 2 CreativeModeTab (Clue/Means).
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DeceptionMod.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DeceptionMod.MOD_ID);

    public static final Map<String, RegistryObject<Item>> CLUE_ITEMS = new LinkedHashMap<>();

    static {
        for (String id : ModBlocks.CLUE_IDS) {
            RegistryObject<Item> item = ITEMS.register(id, () -> new ClueBlockItem(id,
                    ModBlocks.CLUE_BLOCKS.get(id).get(), new Item.Properties()));
            CLUE_ITEMS.put(id, item);
        }
    }

    // tab "Clue" — cuma item yang nama-nya diawali "clue_"
    public static final RegistryObject<CreativeModeTab> CLUE_TAB = CREATIVE_TABS.register("clue_tab", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + DeceptionMod.MOD_ID + ".clue"))
                    .icon(() -> new ItemStack(CLUE_ITEMS.get("clue_abu_rokok").get()))
                    .displayItems((params, output) -> CLUE_ITEMS.forEach((id, item) -> {
                        if (id.startsWith("clue_")) output.accept(item.get());
                    }))
                    .build());

    // tab "Means" — cuma item yang nama-nya diawali "means_"
    public static final RegistryObject<CreativeModeTab> MEANS_TAB = CREATIVE_TABS.register("means_tab", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + DeceptionMod.MOD_ID + ".means"))
                    .icon(() -> new ItemStack(CLUE_ITEMS.get("means_pisau_dapur").get()))
                    .displayItems((params, output) -> CLUE_ITEMS.forEach((id, item) -> {
                        if (id.startsWith("means_")) output.accept(item.get());
                    }))
                    .build());
}