package com.igrium.worldexport.world;

import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.mesh.BlockMeshBuilder;
import com.igrium.worldexport.mesh.MeshUtils;
import com.igrium.worldexport.mesh.WorldMaterialFactory;
import com.igrium.worldexport.tex.NativeImageReplayTexture;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.ReplayTexture;
import com.igrium.worldexport.tex.TextureExtractor;
import de.javagl.obj.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages tasks regarding the conversion of the voxel world into meshes.
 */
public class WorldTessellator {

    public static final String WORLD = "world";
    public static final String WORLD_TRANS = "world_trans";
    public static final String GRASS_MAT = "grass_block";

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldTessellator.class);

    @Getter
    private final WorldCapture worldCapture;

    @Getter
    private final BlockRenderView baseWorld;

    @Getter @Setter @NonNull
    private WorldMaterialFactory materialFactory = this::getDefaultMaterialName;

    @Getter @Setter
    private @Nullable BlockPos offset;

    @Getter @Setter
    private boolean splitBlocks = false;

    @Getter @Setter
    private boolean mergeBaseMeshes;

    @Getter @Setter
    private boolean mergeDoubleVertices;

    @Getter @Setter @NonNull
    private Executor executor = Util.getMainWorkerExecutor();

    /**
     * Called once a chunk has finished tessellating. Must be thread-safe.
     */
    @Getter @Setter
    private @Nullable Consumer<ChunkPos> onChunkTessellated;

    public ChunkSectionBox getBounds() {
        return getWorldCapture().getBounds();
    }

    private final Map<ChunkSectionPos, Obj> baseWorldMeshes = new ConcurrentHashMap<>();
    private final Map<ChunkSectionPos, ReadableObj> baseWorldMeshesUnmodifiable = Collections.unmodifiableMap(baseWorldMeshes);

    private final Map<ChunkPos, CompletableFuture<?>> baseTessellationJobs = new ConcurrentHashMap<>();
    private final Map<ChunkPos, CompletableFuture<?>> baseTessellationJobsUnmodifiable = Collections.unmodifiableMap(baseTessellationJobs);

    public WorldTessellator(WorldCapture worldCapture, BlockRenderView baseWorld) {
        this.worldCapture = worldCapture;
        this.baseWorld = baseWorld;
    }

    /**
     * Get all the world meshes generated during base world tessellation.
     * @return An unmodifiable map of all base meshes.
     * @implNote Some meshes may be skipped for performance reasons if they received an update prior to tessellation.
     */
    public Map<ChunkSectionPos, ReadableObj> getBaseWorldMeshes() {
        return baseWorldMeshesUnmodifiable;
    }

    /**
     * Get a map of all current or previous base tessellation jobs.
     * @return An unmodifiable map of both completed and in-progress futures.
     */
    public Map<ChunkPos, CompletableFuture<?>> getBaseTessellationJobs() {
        return baseTessellationJobsUnmodifiable;
    }

    public String getDefaultMaterialName(BlockState state) {
        return state.isTransparent() ? WORLD_TRANS : WORLD;
    }

    /**
     * Get the materials that the world mesh will reference by default.
     * @return All default materials. Should be stored in <code>world/world.mtl</code>
     */
    public List<ReplayMtl> getDefaultWorldMtls() {
        List<ReplayMtl> mtls = new ArrayList<>(2);

        ReplayMtl world = new ReplayMtl(Mtls.create(WORLD));
        world.mtl().setMapKd("world.png");
        world.properties().put("vertexTint", ReplayMtl.Property.of(true));
        mtls.add(world);

        ReplayMtl worldTrans = new ReplayMtl(Mtls.create(WORLD_TRANS));
        worldTrans.mtl().setMapKd("world.png");
        worldTrans.mtl().setMapD("world.png");
        worldTrans.properties().put("vertexTint", ReplayMtl.Property.of(true));
        mtls.add(worldTrans);

        // Grass material
        ReplayMtl grassBlock = new ReplayMtl(Mtls.create(GRASS_MAT));
        grassBlock.mtl().setMapKd("world.png");
        grassBlock.properties().put("vertexTint", ReplayMtl.Property.of(true));

        Vector2f overlayOffset = getGrassOverlayOffset(new Vector2f());
        grassBlock.properties().put("grassOverlayU", ReplayMtl.Property.of(overlayOffset.x));
        grassBlock.properties().put("grassOverlayV", ReplayMtl.Property.of(overlayOffset.y));
        mtls.add(grassBlock);
        return mtls;
    }

    @SuppressWarnings("deprecation")
    private Vector2f getGrassOverlayOffset(Vector2f dest) {
        var atlas = MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        Sprite sideSprite = atlas.getSprite(Identifier.ofVanilla("block/grass_block_side"));
        Sprite overlaySprite = atlas.getSprite(Identifier.ofVanilla("block/grass_block_side_overlay"));

        return dest.set(overlaySprite.getMinU() - sideSprite.getMinU(), overlaySprite.getMinV() - sideSprite.getMinV());
    }

    /**
     * Get the atlas texture used for blocks.
     * @return The cpu-bound atlas texture. Should be saved at <code>world/world.png</code>
     */
    @SuppressWarnings("deprecation") // Not actually deprecated
    public CompletableFuture<NativeImageReplayTexture> getDefaultWorldTexture() {
        return TextureExtractor.pullAtlasTextureAsync(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                .thenApply(NativeImageReplayTexture::new);
    }

    private void tessellateBaseChunk(ChunkPos cPos, Random random) {
        if (!worldCapture.getCopiedBaseWorld().containsKey(cPos))
            return;

        SectionColumnRenderRegion renderRegion = SectionColumnRenderRegion.build(worldCapture.getCopiedBaseWorld(), cPos, baseWorld);

        for (int y = renderRegion.getBottomSectionCoord(); y < renderRegion.getTopSectionCoord(); y++) {
            ChunkSectionPos sPos = ChunkSectionPos.from(cPos, y);

            // Skip if the section already has updates by the time we get to tessellating it.
            // Sections may still have their base meshes discarded due to updates later on, but this is a
            // simple trick to avoid unnecessary work if we can.
            if (worldCapture.sectionHasUpdates(sPos))
                continue;

            Obj obj = Objs.create();
            BlockMeshBuilder.buildSection(obj, sPos, offset, renderRegion, splitBlocks, materialFactory, random, null);

            if (mergeDoubleVertices) {
                obj = MeshUtils.removeDoubles(obj);
            }

            baseWorldMeshes.put(sPos, obj);
        }
    }

    /**
     * Queue a chunk to be tessellated.
     * @param cPos Chunk to tessellate.
     * @return A future that completes once the tessellation has finished.
     */
    public CompletableFuture<?> queueBaseChunkTessellation(ChunkPos cPos) {
        return baseTessellationJobs.compute(cPos, (key, val) -> {
            if (val != null) {
                if (val.isDone()) {
                    LOGGER.warn("Chunk base {} has already been tessellated", cPos);
                } else {
                    LOGGER.error("Chunk base {} is already being tessellated!", cPos);
                    return val;
                }
            }
            return CompletableFuture.runAsync(() -> {
                tessellateBaseChunk(cPos, Random.createLocal());
                if (onChunkTessellated != null) {
                    onChunkTessellated.accept(cPos);
                }
            }, executor).exceptionally(e -> {
                LOGGER.error("Error tessellating base chunk {}: ", cPos, e);
                return null;
            });
        });
    }

    /**
     * Count how many tessellation jobs are currently running (not completed)
     */
    public int countTessellationJobs() {
        int count = 0;
        for (var job : baseTessellationJobs.values()) {
            if (!job.isDone())
                count++;
        }
        return count;
    }

    /**
     * Return a future that completes once all tessellation jobs are running and there are no more left.
     * @implNote If any tessellation jobs are added in the meantime, the future won't complete until those are finished as well.
     */
    public CompletableFuture<?> awaitBaseTessellationFinished() {
        if (countTessellationJobs() == 0)
            return CompletableFuture.completedFuture(null);

        CompletableFuture<?>[] futures = baseTessellationJobs.values().toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenCompose(v -> awaitBaseTessellationFinished());
    }


    /**
     * Queue every section within the bounds to be tessellated.
     */
    public void tessellateBaseWorld() {
        ThreadLocal<Random> randoms = ThreadLocal.withInitial(Random::createLocal);
        ChunkSectionBox bounds = worldCapture.getBounds();

        // Iterate over each chunk within the bounds
        for (int z = bounds.minZ(); z < bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x < bounds.maxX(); x++) {
                ChunkPos cPos = new ChunkPos(x, z);
                if (!worldCapture.getCopiedBaseWorld().containsKey(cPos))
                    continue;
                queueBaseChunkTessellation(cPos);
            }
        }
    }

    private void tessellateSectionFrame(int tick, Collection<BlockPos> blocks, BlockRenderView renderView, BlockUpdateCache cache,
                                        Random random, Consumer<WorldMesh> meshConsumer) {
        // blocks which need to be split 'cause they're replaced later
        Int2ObjectMap<Set<BlockPos>> overrideMap = new Int2ObjectAVLTreeMap<>();
        Set<BlockPos> blocksWithoutOverrides = new HashSet<>();

        // Split into groups based on the next time the block is updated.
        for (var bPos : blocks) {
            int nextKeyframe = cache.getNextBlockUpdate(bPos, tick);
            if (nextKeyframe >= 0) {
                overrideMap.computeIfAbsent(nextKeyframe, i -> new HashSet<>()).add(bPos);
            } else {
                blocksWithoutOverrides.add(bPos);
            }
        }

        for (var overrideEntry : overrideMap.int2ObjectEntrySet()) {
            Obj mesh = Objs.create();
            BlockMeshBuilder.build(mesh, overrideEntry.getValue(), offset, renderView, splitBlocks, materialFactory, random);
            meshConsumer.accept(new WorldMesh(mesh, tick, overrideEntry.getIntKey() - 1));
        }
        if (!blocksWithoutOverrides.isEmpty()) {
            Obj mesh = Objs.create();
            BlockMeshBuilder.build(mesh, blocksWithoutOverrides, offset, renderView, splitBlocks, materialFactory, random);
            meshConsumer.accept(new WorldMesh(mesh, tick, null));
        }
    }

    /**
     * Tessellate all the blocks within a given section.
     * @param sPos Section to tessellate.
     * @param cache The world's block update cache.
     * @param random Random to use during meshing.
     * @return A list of all the different meshes generated for various keyframes.
     * @implNote If the base mesh hasn't finished building, could have unexpected results.
     */
    public List<WorldMesh> tessellateSectionMeshes(ChunkSectionPos sPos, BlockUpdateCache cache, Random random) {
        Int2ObjectSortedMap<Set<BlockPos>> updates = cache.getSectionUpdates(sPos);
        if (updates.isEmpty()) {
            // If no updates, return pre-tessellated base mesh.
            Obj base = baseWorldMeshes.get(sPos);
            return base != null && base.getNumFaces() > 0 ?
                    List.of(new WorldMesh(base)) :
                    List.of();
        }
        // Re-usable map to store a given keyframe's overrides
        // (blocks which need to be split 'cause they're replaced later)
        Int2ObjectMap<Set<BlockPos>> overrideMap = new Int2ObjectAVLTreeMap<>();
        Set<BlockPos> blocksWithoutOverrides = new HashSet<>();

        // All the blocks that have a keyframe at some point in the file.
        Set<BlockPos> overwrittenBlocks = new HashSet<>();
        List<WorldMesh> list = new ArrayList<>();

        // For each update
        for (var updateEntry : updates.int2ObjectEntrySet()) {
            // This isn't the most efficient ever, but in theory, we're only calling it for updated blocks.
            SnapshotRenderView renderView = new SnapshotRenderView(worldCapture, baseWorld, updateEntry.getIntKey());
            tessellateSectionFrame(updateEntry.getIntKey(), updateEntry.getValue(), renderView, cache, random, list::add);
            overwrittenBlocks.addAll(updateEntry.getValue());
        }

        // Blocks present in the first frame that get updated later
        SectionColumnRenderRegion baseRenderView = SectionColumnRenderRegion.build(worldCapture.getCopiedBaseWorld(), sPos.toChunkPos(), baseWorld);
        if (!overwrittenBlocks.isEmpty()) {
            tessellateSectionFrame(0, overwrittenBlocks, baseRenderView, cache, random, list::add);
        }

        if (mergeDoubleVertices) {
            for (int i = 0; i < list.size(); i++) {
                WorldMesh oldMesh = list.get(i);
                list.set(i, new WorldMesh(MeshUtils.removeDoubles(oldMesh.obj()), oldMesh.meta()));
            }
        }

        // Tessellate base mesh.
        Obj baseMesh = Objs.create();
        BlockMeshBuilder.buildSection(baseMesh, sPos, offset, baseRenderView, splitBlocks, materialFactory, random, p -> !overwrittenBlocks.contains(p));

        if (baseMesh.getNumFaces() > 0) {
            list.add(new WorldMesh(baseMesh));
        }

        return list;
    }

    public List<WorldMesh> tessellateChunkMeshes(ChunkPos cPos, BlockUpdateCache cache) {
        List<WorldMesh> list = new ArrayList<>();
        Random random = Random.createLocal();
        for (int y = getBounds().minY(); y < getBounds().maxY(); y++) {
            ChunkSectionPos sPos = ChunkSectionPos.from(cPos, y);
            try {
                list.addAll(tessellateSectionMeshes(sPos, cache, random));
            } catch (Exception e) {
                LOGGER.error("Error tessellating chunk section {}", sPos, e);
            }
        }
        return list;
    }

    public CompletableFuture<List<WorldMesh>> tessellateChunkMeshesAsync(ChunkPos cPos, BlockUpdateCache cache) {
        CompletableFuture<?> baseTessellationFuture = baseTessellationJobs.get(cPos);
        if (baseTessellationFuture != null) {
            if (!baseTessellationFuture.isDone()) {
                LOGGER.warn("Base meshes for chunk {} are still being tessellated. Deferring mesh generation.", cPos);
            }
            return baseTessellationFuture.thenApplyAsync(v -> tessellateChunkMeshes(cPos, cache), executor);
        } else {
            return CompletableFuture.supplyAsync(() -> tessellateChunkMeshes(cPos, cache), executor);
        }
    }

    public interface ChunkTessellationCallback {
        /**
         * Called when a chunk has finished tessellating.
         *
         * @param pos    The position of the chunk.
         * @param meshes All the meshes that were generated.
         * @param index  The number of chunks that have finished before this.
         * @param total  The total number of chunks.
         */
        void accept(ChunkPos pos, List<WorldMesh> meshes, int index, int total);
    }

    /**
     * Asynchronously compile tessellate all meshes that will be in the final export.
     *
     * @param callback An optional callback to be notified when a chunk finishes tessellating. Must be thread-safe.
     * @return A future that completes once all chunks have finished tessellating.
     */
    public CompletableFuture<Map<String, WorldMesh>> tessellateAllMeshes(@Nullable ChunkTessellationCallback callback) {
        BlockUpdateCache cache = BlockUpdateCache.generate(worldCapture);
        List<CompletableFuture<?>> futures = new ArrayList<>();
//        List<WorldMesh> result = Collections.synchronizedList(new ArrayList<>());

        Map<String, WorldMesh> result = new ConcurrentHashMap<>();
        AtomicInteger index = new AtomicInteger();

        for (ChunkPos cPos : worldCapture.getCopiedBaseWorld().keySet()) {
            futures.add(tessellateChunkMeshesAsync(cPos, cache).thenAccept(list -> {
                int i = index.getAndIncrement();
                if (callback != null) {
                    callback.accept(cPos, list, i, worldCapture.getCopiedBaseWorld().size());
                }
                int localI = 0;
                for (var mesh : list) {
                    result.put("chunk" + i + ".mesh" + localI, mesh);
                    localI++;
                }
            }).exceptionally(e -> {
                LOGGER.error("Error tessellating chunk {}", cPos, e);
                return null;
            }));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(v -> {
            List<String> mtlLibs = List.of("world.mtl");
            for (var mesh : result.values()) {
                mesh.obj().setMtlFileNames(mtlLibs);
            }
            if (mergeBaseMeshes) {
                LOGGER.info("Merging base meshes");
                WorldMesh base = new WorldMesh(Objs.create());
                base.obj().setMtlFileNames(mtlLibs);
                Map<String, WorldMesh> finalResult = new HashMap<>(result.size() + 1);
                finalResult.put("world_base", base);

                for (var meshEntry : result.entrySet()) {
                    if (meshEntry.getValue().meta().isEmpty()) {
                        ObjUtils.add(meshEntry.getValue().obj(), base.obj());
                    } else {
                        finalResult.put(meshEntry.getKey(), meshEntry.getValue());
                    }
                }
                return finalResult;
            } else {
                return result;
            }
        });
    }
}
