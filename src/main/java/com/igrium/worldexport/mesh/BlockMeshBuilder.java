package com.igrium.worldexport.mesh;

import com.google.common.collect.AbstractIterator;
import com.igrium.worldexport.mesh.VertexConsumers.DuplicateCheckingVertexConsumer;
import com.igrium.worldexport.mesh.VertexConsumers.ObjVertexConsumer;
import de.javagl.obj.Obj;
import net.minecraft.client.renderer.block.*;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
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
            Obj targetMesh, Iterable<BlockPos> blocks, @Nullable BlockPos offset, BlockAndTintGetter world,
            boolean splitBlocks, WorldMaterialFactory materialFactory, RandomSource random) {

        Minecraft mc = Minecraft.getInstance();
        BlockStateModelSet blockModels = mc.getModelManager().getBlockStateModelSet();
        FluidStateModelSet fluidModels = mc.getModelManager().getFluidStateModelSet();

        ModelBlockRenderer renderer = new ModelBlockRenderer(true, true, mc.getBlockColors());
        FluidRenderer fluidRenderer = new FluidRenderer(fluidModels);

        var vertexConsumer = new ObjVertexConsumer(targetMesh);
        var duplicateChecker = new DuplicateCheckingVertexConsumer(vertexConsumer);
        // TODO: why do we need this? Presumably it's shade sharp.
        vertexConsumer.setEnableNormals(true);


//        BlockRenderDispatcher blockRenderManager = Minecraft.getInstance().getBlockRenderer();
//        PoseStack matrixStack = new PoseStack();
//
//
//        ObjVertexConsumer vertexConsumer = new ObjVertexConsumer(targetMesh);
//        DuplicateCheckingVertexConsumer duplicateChecker = new DuplicateCheckingVertexConsumer(vertexConsumer);
//
//        vertexConsumer.setEnableNormals(false);
//
        if (offset != null) {
            vertexConsumer.matrices.translate(offset.getX(), offset.getY(), offset.getZ());
            duplicateChecker.matrices.translate(offset.getX(), offset.getY(), offset.getZ());
        }

        for (BlockPos pos : blocks) {
            BlockState state = world.getBlockState(pos);
            FluidState fluidState = world.getFluidState(pos);

            if (!fluidState.isEmpty()) {
                targetMesh.setActiveMaterialGroupName(materialFactory.getMaterial(state));
                vertexConsumer.matrices.pushPose();
                vertexConsumer.matrices.translate(pos.getX() >> 4 << 4, pos.getY() >> 4 << 4, pos.getZ() >> 4 << 4);
                fluidRenderer.tesselate(world, pos, layer -> vertexConsumer, state, fluidState);
                vertexConsumer.matrices.popPose();
            }

            if (state.getRenderShape() == RenderShape.INVISIBLE || state.getBlock() == Blocks.AIR) continue;

            if (splitBlocks) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                targetMesh.setActiveGroupNames(List.of(id.toString()));
            }

            var vc = state.is(Blocks.GRASS_BLOCK) ? duplicateChecker : vertexConsumer;
            BlockQuadOutput quadOutput = vc::putBlockBakedQuad;
            targetMesh.setActiveMaterialGroupName(materialFactory.getMaterial(state));
            if (state.getRenderShape() == RenderShape.MODEL) {
                renderer.tesselateBlock(
                        quadOutput,
                        pos.getX(), pos.getY(), pos.getZ(),
                        world, pos, state,
                        blockModels.get(state),
                        state.getSeed(pos));
                vc.pushFace();
            }
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
            Obj targetMesh, BlockPos minPos, BlockPos maxPos, @Nullable BlockPos offset, BlockAndTintGetter world,
            boolean splitBlocks, WorldMaterialFactory materialFactory, RandomSource random, @Nullable Predicate<?
                    super BlockPos> predicate) {

        Iterable<BlockPos> iter;
        if (predicate != null) {
            iter = filteredIterable(BlockPos.betweenClosed(minPos, maxPos), predicate);
        } else {
            iter = BlockPos.betweenClosed(minPos, maxPos);
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
            Obj targetMesh, SectionPos section, @Nullable BlockPos offset, BlockAndTintGetter world,
            boolean splitBlocks, WorldMaterialFactory materialFactory, RandomSource random, @Nullable Predicate<?
                    super BlockPos> predicate) {

        int minX = section.minBlockX();
        int minY = section.minBlockY();
        int minZ = section.minBlockZ();

        int maxX = section.maxBlockX();
        int maxY = section.maxBlockY();
        int maxZ = section.maxBlockZ();


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
            }

            ;
        };
    }
}
