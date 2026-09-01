package com.igrium.worldexport.blockentity;

import com.igrium.worldexport.blockentity.model_adapters.BellModelAdapter;
import com.igrium.worldexport.blockentity.model_adapters.ChestModelAdapter;
import com.igrium.worldexport.blockentity.model_adapters.EnchantTableModelAdapter;
import com.igrium.worldexport.blockentity.model_adapters.ShulkerBoxModelAdapter;
import com.igrium.worldexport.blockentity.model_adapters.SkullBlockModelAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

import java.util.HashMap;
import java.util.Map;

public class BlockModelAdapters {
    public interface Factory<T extends BlockEntity> {
        BlockModelAdapter<T, ?> get(BlockEntityRenderer<? super T, ?> renderer);
    }

    private static final Map<BlockEntityType<?>, Factory<?>> REGISTRY = new HashMap<>();

    public static <T extends BlockEntity> void register(BlockEntityType<T> blockEntityType, Factory<T> factory) {
        REGISTRY.put(blockEntityType, factory);
    }

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

    static {
        register(BlockEntityTypes.CHEST, ChestModelAdapter::fromRenderer);
        register(BlockEntityTypes.TRAPPED_CHEST, ChestModelAdapter::fromRenderer);
        register(BlockEntityTypes.ENDER_CHEST, ChestModelAdapter::fromRenderer);

        register(BlockEntityTypes.BELL, BellModelAdapter::fromRenderer);

        register(BlockEntityTypes.SHULKER_BOX, ShulkerBoxModelAdapter::fromRenderer);

        register(BlockEntityTypes.SKULL, SkullBlockModelAdapter::fromRenderer);

        register(BlockEntityTypes.ENCHANTING_TABLE, EnchantTableModelAdapter::fromRenderer);
    }
}
