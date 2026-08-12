package com.igrium.worldexport.mesh;

import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;

/**
 * Enables overriding models for export at runtime
 */
public class ExportModels {
    public static final Identifier GRASS_BLOCK = Identifier.parse("worldexport:block/export_grass_block");
    public static final ExtraModelKey<BlockStateModel> GRASS_BLOCK_KEY = ExtraModelKey.create(GRASS_BLOCK::toString);

    public static void register() {
        ModelLoadingPlugin.register(ctx ->
                ctx.addModel(GRASS_BLOCK_KEY, SimpleUnbakedExtraModel.blockStateModel(GRASS_BLOCK)));
    }
}
