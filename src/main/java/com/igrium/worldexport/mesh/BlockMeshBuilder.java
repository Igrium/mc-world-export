package com.igrium.worldexport.mesh;

import com.google.common.collect.AbstractIterator;
import de.javagl.obj.Obj;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 * Renders the block world into an OBJ.
 */
public class BlockMeshBuilder {

    /**
     * Render a collection of blocks into an OBJ.
     *
     * @param targetMesh      OBJ to render into.
     * @param blocks          All the blocks to build. Does not affect culling.
     * @param offset          Offset to apply to blocks in the OBJ.
     * @param world           World to render from.
     * @param splitBlocks     If true, blocks will be assigned OBJ groups based on their type.
     * @param materialFactory Generates material names from block types.
     * @param random          Random instance to pass to <code>BlockRenderManager</code>
     */
    public static void build(
            Obj targetMesh, Iterable<BlockPos> blocks, @Nullable BlockPos offset, BlockRenderView world,
            boolean splitBlocks, WorldMaterialFactory materialFactory, Random random) {
        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
        MatrixStack matrixStack = new MatrixStack();


        ObjVertexConsumer vertexConsumer = new ObjVertexConsumer(targetMesh);

        if (offset != null) {
            vertexConsumer.matrices.translate(-offset.getX(), -offset.getY(), -offset.getZ());
        }

        for (BlockPos pos : blocks) {
            BlockState state = world.getBlockState(pos);
            FluidState fluidState = state.getFluidState();

            vertexConsumer.setMaterial(materialFactory.getMaterial(state));

            if (!fluidState.isEmpty()) {
                vertexConsumer.matrices.push();
                vertexConsumer.matrices.translate(pos.getX() >> 4 << 4, pos.getY() >> 4 << 4, pos.getZ() >> 4 << 4);
                blockRenderManager.renderFluid(pos, world, vertexConsumer, state, fluidState);
                vertexConsumer.matrices.pop();
            }

            if (state.getRenderType() == BlockRenderType.INVISIBLE)
                continue;

            if (splitBlocks) {
                Identifier id = Registries.BLOCK.getId(state.getBlock());
                vertexConsumer.setActiveGroup(id.toString());
            }

            matrixStack.push();
            matrixStack.translate(pos.getX(), pos.getY(), pos.getZ());
            blockRenderManager.renderBlock(state, pos, world, matrixStack, vertexConsumer, true, random);
            matrixStack.pop();
        }
    }

    /**
     * Render box of blocks into an OBJ.
     *
     * @param targetMesh      OBJ to render into.
     * @param minPos          Minimum block position, inclusive.
     * @param maxPos          Maximum block position, inclusive.
     * @param offset          Offset to apply to blocks in the OBJ.
     * @param world           World to render from.
     * @param splitBlocks     If true, blocks will be assigned OBJ groups based on their type.
     * @param materialFactory Generates material names from block types.
     * @param random          Random instance to pass to <code>BlockRenderManager</code>
     * @param predicate       If set, only tessellate block positions that match this predicate (doesn't affect culling)
     */
    public static void buildRange(
            Obj targetMesh, BlockPos minPos, BlockPos maxPos, @Nullable BlockPos offset, BlockRenderView world,
            boolean splitBlocks, WorldMaterialFactory materialFactory, Random random, @Nullable Predicate<? super BlockPos> predicate) {

        Iterable<BlockPos> iter;
        if (predicate != null) {
            iter = filteredIterable(BlockPos.iterate(minPos, maxPos), predicate);
        } else {
            iter = BlockPos.iterate(minPos, maxPos);
        }

        build(targetMesh, iter, offset, world, splitBlocks, materialFactory, random);
    }


    /**
     * Render a section of blocks into an OBJ
     *
     * @param targetMesh      OBJ to render into.
     * @param section         Section to render.
     * @param offset          Offset to apply to blocks in the OBJ.
     * @param world           World to render from.
     * @param splitBlocks     If true, blocks will be assigned OBJ groups based on their type.
     * @param materialFactory Generates material names from block types.
     * @param random          Random instance to pass to <code>BlockRenderManager</code>
     * @param predicate       If set, only tessellate block positions that match this predicate (doesn't affect culling)
     */
    public static void buildSection(
            Obj targetMesh, ChunkSectionPos section, @Nullable BlockPos offset, BlockRenderView world,
            boolean splitBlocks, WorldMaterialFactory materialFactory, Random random, @Nullable Predicate<? super BlockPos> predicate) {

        int minX = section.getMinX();
        int minY = section.getMinY();
        int minZ = section.getMinZ();

        int maxX = section.getMaxX();
        int maxY = section.getMaxY();
        int maxZ = section.getMaxZ();


        buildRange(targetMesh, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), offset, world,
                splitBlocks, materialFactory, random, predicate);
    }

    private static <T> Iterable<T> filteredIterable(Iterable<T> in, Predicate<? super T> predicate) {
        return () -> filteredIterator(in.iterator(), predicate);
    }

    private static <T> Iterator<T> filteredIterator(Iterator<T> in, Predicate<? super T> predicate) {
        return new AbstractIterator<T>() {
            @Override
            protected T computeNext() {
                while (in.hasNext()) {
                    T val = in.next();
                    if (predicate.test(val)) {
                        return val;
                    }
                }
                return endOfData();
            };
        };
    }
}
