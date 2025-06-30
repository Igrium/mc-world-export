package com.igrium.worldexport.mesh;

import com.igrium.worldexport.util.FutureUtils;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Renders blocks into obj meshes.
 */
public class BlockMeshBuilder {

    /**
     * Build a select number of blocks into a mesh.
     *
     * @param targetMesh      Mesh to add faces to.
     * @param blocks          Blocks to build.
     * @param world           World to read blocks from.
     * @param splitBlocks     If true, blocks will be split into groups based on their type.
     * @param materialFactory Function to assign material names to blocks.
     * @param random          Random to use when drawing blocks.
     */
    public static void buildBlocks(Obj targetMesh, Iterable<? extends BlockPos> blocks, BlockRenderView world,
                                   boolean splitBlocks, Function<BlockState, String> materialFactory, Random random) {
        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
        MatrixStack matrixStack = new MatrixStack();

        ObjVertexConsumer vertexConsumer = new ObjVertexConsumer(targetMesh);

        for (BlockPos pos : blocks) {
            BlockState state = world.getBlockState(pos);
            FluidState fluidState = state.getFluidState();

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
                targetMesh.setActiveGroupNames(Collections.singleton(id.toString()));
            }

            matrixStack.push();
            matrixStack.translate(pos.getX(), pos.getY(), pos.getZ());
            blockRenderManager.renderBlock(state, pos, world, matrixStack, vertexConsumer, true, random);
            matrixStack.pop();
        }
    }

    /**
     * Build a chunk section into a mesh.
     *
     * @param targetMesh      Mesh to add faces to.
     * @param section         Section coordinate to build.
     * @param world           World to read blocks from.
     * @param splitBlocks     If true, blocks will be split into groups based on their type.
     * @param materialFactory Function to assign material names to blocks.
     * @param random          Random to use when drawing block.
     */
    public static void buildSection(Obj targetMesh, ChunkSectionPos section, BlockRenderView world, boolean splitBlocks,
                                    Function<BlockState, String> materialFactory, Random random) {
        buildBlocks(targetMesh, BlockPos.iterate(section.getMinPos(), section.getMinPos().add(15, 15, 15)),
                world, splitBlocks, materialFactory, random);

    }

    public interface MeshBuildCallback {
        void accept(ChunkSectionPos pos, Obj mesh, int index);
    }

    /**
     * Build a selection of chunk sections using multithreading.
     *
     * @param executor        Executor to use.
     * @param sections        All sections to build.
     * @param world           World to read blocks from. <b>Reading must be thread-safe.</b>
     * @param splitBlocks     If true, blocks will be split into groups based on their type.
     * @param materialFactory Function to assign material names to blocks. <b>Must be thread safe.</b>
     * @param maxThreads      Maximum number of concurrent meshing operations. Use to avoid starving the executor.
     * @param callback        Called whenever a section mesh has finished building.
     * @return A future that completes with all compiled objs once the operation is complete.
     */
    public static CompletableFuture<Map<ChunkSectionPos, Obj>> buildSectionsThreaded(Executor executor, Collection<? extends ChunkSectionPos> sections, BlockRenderView world,
                                                                 boolean splitBlocks, Function<BlockState, String> materialFactory, int maxThreads, @Nullable MeshBuildCallback callback) {
        List<Runnable> operations = new ArrayList<>(sections.size());

        ThreadLocal<Random> randoms = ThreadLocal.withInitial(Random::createLocal);

        ConcurrentHashMap<ChunkSectionPos, Obj> results = new ConcurrentHashMap<>();

        AtomicInteger currentIndex = new AtomicInteger();

        for (var sectionPos : sections) {
            operations.add(() -> {
               Obj obj = Objs.create();
               buildSection(obj, sectionPos, world, splitBlocks, materialFactory, randoms.get());
               results.put(sectionPos, obj);
               if (callback != null) {
                   callback.accept(sectionPos, obj, currentIndex.getAndIncrement());
               }
            });
        }

        return CompletableFuture.allOf(FutureUtils.runAllAsync(operations, executor, maxThreads))
                .thenApply(n -> results);
    }
}
