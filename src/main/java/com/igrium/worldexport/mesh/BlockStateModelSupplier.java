package com.igrium.worldexport.mesh;

import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.level.block.state.BlockState;

public interface BlockStateModelSupplier {
    BlockStateModel get(BlockState blockState);
    BlockStateModel missingModel();
    default Material.Baked getParticleMaterial(BlockState blockState) {
        return get(blockState).particleMaterial();
    }

    static BlockStateModelSupplier of(BlockStateModelSet blockStateModelSet) {
        return new DefaultBlockStateModelSet(blockStateModelSet);
    }
}

class DefaultBlockStateModelSet implements BlockStateModelSupplier {
    private final BlockStateModelSet base;

    DefaultBlockStateModelSet(BlockStateModelSet base) {
        this.base = base;
    }

    @Override
    public BlockStateModel get(BlockState blockState) {
        return base.get(blockState);
    }

    @Override
    public BlockStateModel missingModel() {
        return base.missingModel();
    }

    @Override
    public Material.Baked getParticleMaterial(BlockState blockState) {
        return base.getParticleMaterial(blockState);
    }
}
