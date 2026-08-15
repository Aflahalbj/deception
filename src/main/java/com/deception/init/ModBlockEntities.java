package com.deception.init;

import com.deception.DeceptionMod;
import com.deception.block.HongkongFlagBlockEntity;
import com.deception.block.InvestigationPaperBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DeceptionMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<InvestigationPaperBlockEntity>> INVESTIGATION_PAPER =
            BLOCK_ENTITIES.register("investigation_paper", () -> BlockEntityType.Builder.of(
                    InvestigationPaperBlockEntity::new, ModBlocks.INVESTIGATION_PAPER.get()).build(null));

    // Cuma buat mancing HongkongFlagRenderer -- block entity-nya sendiri
    // kosong, lihat HongkongFlagBlockEntity.
    public static final RegistryObject<BlockEntityType<HongkongFlagBlockEntity>> HONGKONG_FLAG =
            BLOCK_ENTITIES.register("hongkong_flag", () -> BlockEntityType.Builder.of(
                    HongkongFlagBlockEntity::new, ModBlocks.HONGKONG_FLAG.get()).build(null));
}
