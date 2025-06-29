package com.igrium.worldexport.world.mesh;

import com.igrium.worldexport.mesh.BlockMeshBuilder;
import com.igrium.worldexport.world.SectionSetBlockRenderView;
import com.igrium.worldexport.world.WorldCapture;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
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
    public CompletableFuture<?> buildAllBaseMeshes(@Nullable BiIntConsumer progressCallback) {
        var baseSections = worldCapture.getBaseSections();
        if (baseSections.isEmpty()) {
            LOGGER.warn("WorldCapture has no base sections. Make sure to call captureInitialWorld().");
            return CompletableFuture.completedFuture(null);
        }

        SectionSetBlockRenderView renderView = new SectionSetBlockRenderView(worldCapture.getBaseSections(), baseWorld);

        int count = baseSections.size();
        BlockMeshBuilder.MeshBuildCallback callback = (pos, mesh, index) -> {
            if (progressCallback != null) {
                progressCallback.accept(index, count);
            }
            baseSectionMeshes.put(pos, mesh);
        };

        long startTime = Util.getMeasuringTimeMs();
        return BlockMeshBuilder.buildSectionsThreaded(executor, baseSections.keySet(), renderView, splitBlocks, materialFactory, maxThreads, callback)
                .handle((o, e) -> {
                    if (e != null) {
                        LOGGER.error("World tessellation failed!", e);
                    } else {
                        LOGGER.info("Initial world tessellation completed in {}ms", Util.getMeasuringTimeMs() - startTime);
                    }
                    return o;
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
        SectionSetBlockRenderView renderView = new SectionSetBlockRenderView(worldCapture.getBaseSections(), baseWorld);
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

}
