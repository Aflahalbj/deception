package com.deception.entity;

import com.deception.DeceptionMod;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DeceptionMod.MOD_ID);

    public static final RegistryObject<EntityType<ClueEntity>> CLUE_ENTITY = ENTITY_TYPES.register("clue_entity",
            () -> EntityType.Builder.<ClueEntity>of(ClueEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.1F)
                    .clientTrackingRange(10)
                    .build("clue_entity"));
}
