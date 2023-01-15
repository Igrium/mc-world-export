package org.scaffoldeditor.worldexport.vcap.model;

import java.util.Optional;

import org.scaffoldeditor.worldexport.vcap.MeshWriter;
import org.scaffoldeditor.worldexport.vcap.BlockModelEntry;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.random.Random;

/**
 * A model provider based on params from a model entry.
 */
public class BlockModelProvider implements ModelProvider {

    private final BlockModelEntry entry;
    Random random = Random.create();

    public BlockModelProvider(BlockModelEntry entry) {
        this.entry = entry;
    }

    public final BlockModelEntry getEntry() {
        return entry;
    }

    @Override
    public ModelInfo writeMesh() {
        return MeshWriter.writeBlockMesh(entry, random);
    }
    
    @Override
    public Optional<BlockState> getBlockstate() {
        return Optional.of(entry.blockState());
    }
}
