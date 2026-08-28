package com.igrium.worldexport.blockentity;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockModelAdapters {
    public interface Factory<T extends BlockEntity> {
        BlockModelAdapter<T, ?> get(BlockEntityRenderer<? super T, ?> renderer);
    }

    private static final BiMap<BlockEntityType<?>, Factory<?>> REGISTRY = HashBiMap.create();

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> Factory<T> getFactory(BlockEntityType<T> blockEntityType) {
        return (Factory<T>) REGISTRY.get(blockEntityType);
    }

    public static <T extends BlockEntity> BlockModelAdapter<T, ?> createModelAdapter(T blockEntity) {
        var factory = getFactory(getEntityType(blockEntity));
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        return factory != null ? factory.get(renderer) : new BasicBlockModelAdapter<>(renderer);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityType<T> getEntityType(T entity) {
        return (BlockEntityType<T>) entity.getType();
    }
}
