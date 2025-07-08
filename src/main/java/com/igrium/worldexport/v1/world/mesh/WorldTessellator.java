package com.igrium.worldexport.v1.world.mesh;

import com.igrium.worldexport.v1.mesh.BlockMeshBuilder;
import com.igrium.worldexport.util.FutureUtils;
import com.igrium.worldexport.v1.world.SimpleColumnRendererRegion;
import com.igrium.worldexport.v1.world.SnapshotBlockRenderView;
import com.igrium.worldexport.v1.world.WorldCapture;
import com.igrium.worldexport.world.WorldMesh;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import it.unimi.dsi.fastutil.ints.*;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Converts the blocks in a WorldCapture into actual meshes.
 */
public class WorldTessellator {
    private static final int NUM_THREADS = Math.max(Runtime.getRuntime().availableProcessors() - 2, 1);

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldTessellator.class);

    private final Map<ChunkSectionPos, Obj> baseSectionMeshes = new ConcurrentHashMap<>();

    @Getter
    private final WorldCapture worldCapture;

    @Getter
    private final World baseWorld;

    @Getter
    @Setter
    private Function<BlockState, String> materialFactory = (b) -> "none";

    @Getter
    @Setter
    private boolean splitBlocks = false;

    @Getter
    @Setter
    private int maxThreads = NUM_THREADS;

    @Getter
    @Setter
    private Executor executor = Util.getMainWorkerExecutor();

    public WorldTessellator(WorldCapture worldCapture, World baseWorld) {
        this.worldCapture = worldCapture;
        this.baseWorld = baseWorld;
    }

    public interface BiIntConsumer {
        void accept(int i1, int i2);
    }

    /**
     * Build the meshes for all base sections.
     * @param progressCallback Called when a section has finished building.
     * @return A future that completes once all sections have finished building.
     */
    public CompletableFuture<?> buildAllBaseMeshes(@Nullable BiIntConsumer progressCallback, int maxThreads) {
        if (maxThreads <= 0)
            maxThreads = NUM_THREADS;

        int count = worldCapture.getCopiedWorld().countChunks();
        if (count == 0) {
            LOGGER.warn("WorldCapture has no base sections. Be sure to call captureInitialWorld().");
            return CompletableFuture.completedFuture(null);
        }

        var copiedWorld = worldCapture.getCopiedWorld();

//        SimpleSectionBlockRenderView renderView = new SimpleSectionBlockRenderView(worldCapture.getCopiedWorld(), baseWorld);

        BlockMeshBuilder.MeshBuildCallback callback = (pos, meshes, index) -> {
            if (progressCallback != null) {
                progressCallback.accept(index, count);
            }

            for (int i = 0; i < meshes.length; i++) {
                ChunkSectionPos sPos = ChunkSectionPos.from(pos, copiedWorld.sectionIndexToCoord(i));
                if (meshes[i] != null)
                    baseSectionMeshes.put(sPos, meshes[i]);
            }
        };

        long startTime = Util.getMeasuringTimeMs();

        return BlockMeshBuilder.buildChunksThreaded(executor, copiedWorld, baseWorld, splitBlocks, materialFactory, maxThreads, callback)
                .handle((v, e) -> {
                    if (e != null) {
                        LOGGER.error("Error tessellating base meshes", e);
                    }
                    LOGGER.info("Base mesh tessellation took {}ms", Util.getMeasuringTimeMs() - startTime);
                    return null;
                });
    }

    /**
     * Rebuild a section's base mesh with its current data. Called when a chunk that was previously unloaded gets loaded.
     *
     * @param pos Position of the section to build.
     * @return A future that completes once the mesh is built.
     * @apiNote Only meant to be used for sections that have never been seen before; it lumps them with the base sections.
     */
    public CompletableFuture<Obj> buildSectionBaseMesh(ChunkSectionPos pos) {

        SimpleColumnRendererRegion renderView = SimpleColumnRendererRegion.create(
                baseWorld, pos.toChunkPos(), worldCapture.getCopiedWorld());

        return CompletableFuture.supplyAsync(() -> {
            Obj obj = Objs.create();
            BlockMeshBuilder.buildSection(obj, pos, renderView, splitBlocks, materialFactory, Random.createLocal());
            baseSectionMeshes.put(pos, obj);
            return obj;
        }, executor).handle((o, e) -> {
            if (e != null) {
                LOGGER.error("Error tessellating section {}", pos, e);
            }
            return o;
        });

    }

    public CompletableFuture<Map<ChunkSectionPos, List<WorldMesh>>> tessellateAllMeshes(Executor executor, int maxThreads) {
        if (maxThreads <= 0)
            maxThreads = NUM_THREADS;

        CompiledBlockFrameSet frames = CompiledBlockFrameSet.compile(worldCapture);
        ThreadLocal<Random> random = ThreadLocal.withInitial(Random::createLocal);

        Map<ChunkSectionPos, List<WorldMesh>> result = new ConcurrentHashMap<>();

        List<Runnable> runnables = new ArrayList<>();

        worldCapture.getCopiedWorld().forEachSection((cPos, section) -> {
            runnables.add(() -> {
                List<WorldMesh> tessellated = tessellateSectionMeshes(cPos, frames, random.get());
                if (!tessellated.isEmpty()) {
                    result.put(cPos, tessellated);
                }
            });
        });

        return CompletableFuture.allOf(FutureUtils.runAllAsync(runnables, executor, maxThreads))
                .thenApply(r -> result);
    }

    /**
     * Tessellate all the blocks within a given section.
     * @param cPos Section to tessellate.
     * @param frames Compiled frame cache.
     * @param random Thread-local random to use during meshing.
     * @return A list of all the different meshes generated for various keyframes.
     * @implNote If the base mesh hasn't finished building, could have unexpected results.
     */
    public List<WorldMesh> tessellateSectionMeshes(ChunkSectionPos cPos, CompiledBlockFrameSet frames, Random random) {
        // TODO: Implement section keyframes.
        // TODO: (is that actually important?)
        Int2ObjectSortedMap<Set<BlockPos>> keyframes = frames.getSectionBlockKeyframes(cPos);

        Vec3d offset = new Vec3d(cPos.getMinPos());

        if (!keyframes.isEmpty()) {
            // Re-usable map to store a given keyframe's overrides
            // (blocks which need to be split 'cause they're replaced later)
            Int2ObjectMap<Set<BlockPos>> overrideMap = new Int2ObjectAVLTreeMap<>();
            Set<BlockPos> blocksWithoutOverrides = new HashSet<>();

            // All the blocks that have a keyframe at some point in the file.
            Set<BlockPos> keyframedBlocks = new HashSet<>();
            List<WorldMesh> list = new ArrayList<>();

            // For each keyframe
            for (var keyEntry : keyframes.int2ObjectEntrySet()) {
                overrideMap.clear();
                blocksWithoutOverrides.clear();

                SnapshotBlockRenderView renderView = new SnapshotBlockRenderView(worldCapture, baseWorld, keyEntry.getIntKey());

                // Find which blocks are updated again at some point in the future and split them so they can be toggled off.
                for (var bPos : keyEntry.getValue()) {
                    Integer nextKeyframe = frames.getNextBlockUpdate(bPos, keyEntry.getIntKey());
                    if (nextKeyframe != null) {
                        overrideMap.computeIfAbsent((int)nextKeyframe, i -> new HashSet<>()).add(bPos);
                    } else {
                        blocksWithoutOverrides.add(bPos);
                    }
                }

                for (var overrideEntry : overrideMap.int2ObjectEntrySet()) {
                    Obj mesh = Objs.create();
                    BlockMeshBuilder.buildBlocks(mesh, overrideEntry.getValue(), renderView,
                            splitBlocks, materialFactory, random);

                    list.add(new WorldMesh(mesh, keyEntry.getIntKey(), overrideEntry.getIntKey() - 1));
                }
                Obj noOverrideMesh = Objs.create();
                BlockMeshBuilder.buildBlocks(noOverrideMesh, blocksWithoutOverrides, renderView,
                        splitBlocks, materialFactory, random);
                list.add(new WorldMesh(noOverrideMesh, offset, keyEntry.getIntKey(), null));

                keyframedBlocks.addAll(keyEntry.getValue());
            }

            // Tessellate base mesh
            List<BlockPos> nonKeyframedBlocks = new ArrayList<>(16 * 16 * 16);
            for (BlockPos bPos : BlockPos.iterate(cPos.getMinPos(), cPos.getMinPos().add(15, 15, 15))) {
                if (!keyframedBlocks.contains(bPos))
                    nonKeyframedBlocks.add(bPos);
            }

            Obj baseMesh = Objs.create();
//            BlockRenderView baseRenderView = new SectionSetBlockRenderView(worldCapture.getBaseChunks(), baseWorld);
            BlockRenderView baseRenderView = SimpleColumnRendererRegion.create(baseWorld, cPos.toChunkPos(), worldCapture.getCopiedWorld());
//            BlockRenderView baseRenderView = new SimpleSectionBlockRenderView(worldCapture.getCopiedWorld(), baseWorld);

            BlockMeshBuilder.buildBlocks(baseMesh, nonKeyframedBlocks, baseRenderView,
                    splitBlocks, materialFactory, random);

            if (baseMesh.getNumFaces() > 0)
                list.add(new WorldMesh(baseMesh, offset));
            return list;

        } else {
            Obj base = baseSectionMeshes.get(cPos);
            return base != null && base.getNumFaces() > 0 ? List.of(new WorldMesh(base, offset)) : List.of();
        }

    }

}
