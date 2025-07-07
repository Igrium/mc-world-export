package com.igrium.worldexport.v1.mesh;

import com.igrium.worldexport.util.FutureUtils;
import com.igrium.worldexport.v1.world.SimpleColumnRendererRegion;
import com.igrium.worldexport.v1.world.SimpleSectionWorld;
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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.chunk.ReadableContainer;
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

    public static Obj[] buildChunk(
            ChunkPos chunkPos, SimpleSectionWorld<? extends ReadableContainer<BlockState>> world,
            BlockRenderView baseWorld, boolean splitBlocks, Function<BlockState, String> materialFactory, Random random) {

        var chunk = world.getChunks().get(chunkPos);
        if (chunk == null)
            return new Obj[0];

        SimpleColumnRendererRegion rendererRegion = SimpleColumnRendererRegion.create(baseWorld, chunkPos, world);

        Obj[] result = new Obj[chunk.countVerticalSections()];

        for (int i = 0; i < result.length; i++) {
            if (chunk.getSection(i) == null)
                continue;

            ChunkSectionPos sPos = ChunkSectionPos.from(chunkPos, chunk.sectionIndexToCoord(i));
            Obj obj = Objs.create();
            buildSection(obj, sPos, rendererRegion, splitBlocks, materialFactory, random);
            result[i] = obj;
        }

        return result;
    }

    public interface MeshBuildCallback {
        void accept(ChunkPos pos, Obj[] meshes, int index);
    }

    public static CompletableFuture<Map<ChunkPos, Obj[]>> buildChunksThreaded(
            Executor executor, SimpleSectionWorld<? extends ReadableContainer<BlockState>> world, BlockRenderView baseWorld,
            boolean splitBlocks, Function<BlockState, String> materialFactory, int maxThreads, @Nullable MeshBuildCallback callback) {

        List<Runnable> operations = new ArrayList<>(world.countChunks());
        ThreadLocal<Random> randoms = ThreadLocal.withInitial(Random::createLocal);
        Map<ChunkPos, Obj[]> results = new ConcurrentHashMap<>();
        AtomicInteger currentIndex = new AtomicInteger();

        for (var cPos : world.getChunks().keySet()) {
            operations.add(() -> {
                int index = currentIndex.getAndIncrement();
                Obj[] result = buildChunk(cPos, world, baseWorld, splitBlocks, materialFactory, randoms.get());
                results.put(cPos, result);
                if (callback != null) {
                    callback.accept(cPos, result, index);
                }
            });
        }

        return CompletableFuture.allOf(FutureUtils.runAllAsync(operations, executor, maxThreads))
                .thenApply(v -> results);
    }

//
    public record PositionedObj(ChunkSectionPos pos, Obj obj) {};
//
//    @FunctionalInterface
//    public interface ChunkBuildCallback {
//        void accept(ChunkPos pos, List<PositionedObj> meshes, int index);
//    }
//
//    public static CompletableFuture<Map<ChunkPos, List<PositionedObj>>> buildChunksThreaded(Executor executor, Collection<? extends ChunkPos> chunks,
//                                                                                  BlockRenderView world, boolean splitBlocks, Function<BlockState, String> materialFactory,
//                                                                                  int maxThreads, @Nullable ChunkBuildCallback callback) {
//        List<Runnable> operations = new ArrayList<>(chunks.size());
//        ThreadLocal<Random> randoms = ThreadLocal.withInitial(Random::createLocal);
//        Map<ChunkPos, List<PositionedObj>> results = new ConcurrentHashMap<>();
//        AtomicInteger currentIndex = new AtomicInteger();
//
//        int min = world.getBottomSectionCoord();
//        int max = world.getTopSectionCoord();
//
//        for (var chunk : chunks) {
//            operations.add(() -> {
//                int index = currentIndex.getAndIncrement();
//                List<PositionedObj> list = new ArrayList<>(max - min);
//
//                for (int y = min; y < max; y++) {
//                    Obj obj = Objs.create();
//                    ChunkSectionPos pos = ChunkSectionPos.from(chunk, y);
//                    buildSection(obj, pos, world, splitBlocks, materialFactory, randoms.get());
//
//                    var positioned = new PositionedObj(pos, obj);
//                    list.add(positioned);
//                }
//                results.put(chunk, list);
//                if (callback != null)
//                    callback.accept(chunk, list, index);
//            });
//        }
//
//        return CompletableFuture.allOf(FutureUtils.runAllAsync(operations, executor, maxThreads))
//                .thenApply(n -> results);
//    }
}
