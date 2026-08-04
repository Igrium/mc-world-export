package com.igrium.worldexport.world;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.igrium.worldexport.mesh.BlockTessellator;
import com.igrium.worldexport.mesh.MeshMergeVerts;
import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.tex.NativeImageReplayTexture;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.TextureExtractor;
import com.igrium.worldexport.util.TaskManager;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WorldMesher {
    public static final String WORLD = "world";
    public static final String WORLD_TRANS = "world_trans";
    public static final String GRASS_MAT = "grass_block";

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/WorldMesher");

    /// === STATE & CONFIGURATION ===

    private final ClientLevel baseWorld;

    @Getter
    @Setter
    private @Nullable BlockPos offset;

    @Getter
    @Setter
    private boolean mergeBaseMeshes;

    @Getter
    @Setter
    private boolean mergeDoubleVertices;

    @Getter
    @Setter
    private boolean splitBlocks = true;

    /**
     * The maximum number of threads to use while meshing.
     */
    @Getter
    @Setter
    private int maxThreads = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);

    /**
     * Called once a section has finished tessellating. Must be thread-safe.
     */
    @Getter
    @Setter
    private @Nullable Consumer<SectionPos> onSectionTessellated;

    private final BlockTessellator tessellator;

    @Getter
    private final TaskManager<SectionPos, BlockAndTintGetter, Obj> taskManager;

    public WorldMesher(ClientLevel baseWorld) {
        this.baseWorld = baseWorld;

        var modelManager = Minecraft.getInstance().getModelManager();
        tessellator = BlockTessellator.builder()
                .blockColors(Minecraft.getInstance().getBlockColors())
                .blockModelSet(modelManager.getBlockStateModelSet())
                .fluidModelSet(modelManager.getFluidStateModelSet())
                .blockMatFactory(this::getDefaultMaterialName)
                .fluidMatFactory(this::getDefaultMaterialName)
                .splitBlocks(this.splitBlocks)
                .ambientOcclusion(false)
                .build();

        taskManager = new TaskManager<>(Runtime.getRuntime().availableProcessors() - 1, this::tessellateSection);
    }

    /// === MATERIALS & TEXTURES ===

    public String getDefaultMaterialName(BlockState state) {
        return state.propagatesSkylightDown() ? WORLD_TRANS : WORLD;
    }

    public String getDefaultMaterialName(FluidState state) {
        return WORLD_TRANS;
    }

    /**
     * Get the materials that the world mesh will reference by default.
     *
     * @return All default materials. Should be stored in <code>world/world.mtl</code>
     */
    public static List<ReplayMtl> getDefaultWorldMtls() {
        List<ReplayMtl> mtls = new ArrayList<>(2);

        ReplayMtl world = new ReplayMtl(Mtls.create(WORLD));
        world.mtl().setMapKd(ReplayCapture.WORLD_TEX);
        world.properties().put("vertexTint", ReplayMtl.Property.of(true));
        mtls.add(world);

        ReplayMtl worldTrans = new ReplayMtl(Mtls.create(WORLD_TRANS));
        worldTrans.mtl().setMapKd(ReplayCapture.WORLD_TEX);
        worldTrans.mtl().setMapD(ReplayCapture.WORLD_TEX);
        worldTrans.properties().put("vertexTint", ReplayMtl.Property.of(true));
        mtls.add(worldTrans);

        // Grass material
        ReplayMtl grassBlock = new ReplayMtl(Mtls.create(GRASS_MAT));
        grassBlock.mtl().setMapKd(ReplayCapture.WORLD_TEX);
        grassBlock.properties().put("vertexTint", ReplayMtl.Property.of(true));

        Vector2f overlayOffset = getGrassOverlayOffset(new Vector2f());
        grassBlock.properties().put("grassOverlayU", ReplayMtl.Property.of(overlayOffset.x));
        grassBlock.properties().put("grassOverlayV", ReplayMtl.Property.of(overlayOffset.y));
        mtls.add(grassBlock);
        return mtls;
    }

    private static Vector2f getGrassOverlayOffset(Vector2f dest) {
        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        TextureAtlasSprite sideSprite = atlas.getSprite(Identifier.withDefaultNamespace("block/grass_block_side"));
        TextureAtlasSprite overlaySprite = atlas.getSprite(Identifier.withDefaultNamespace("block" +
                "/grass_block_side_overlay"));

        return dest.set(overlaySprite.getU0() - sideSprite.getU0(), overlaySprite.getV0() - sideSprite.getV0());
    }

    /**
     * Get the atlas texture used for blocks.
     *
     * @return The cpu-bound atlas texture. Should be saved at <code>world/world.png</code>
     */
    public static CompletableFuture<NativeImageReplayTexture> getDefaultWorldTexture() {
        return TextureExtractor.pullAtlasTextureAsync(AtlasIds.BLOCKS)
                .thenApply(NativeImageReplayTexture::new);
    }

    /// === MESHING ===

    private Obj tessellateSection(SectionPos pos, BlockAndTintGetter region) {
        LOGGER.info("Compiling section {}", pos);
        Obj obj = tessellator.compileSection(pos, region);
        return mergeDoubleVertices ? MeshMergeVerts.mergeByDistance(obj, .001f) : obj;
    }

    public CompletableFuture<Map<SectionPos, List<WorldMesh>>> tessellateDeltas(WorldCapture capture) {
        BlockUpdateCache cache = BlockUpdateCache.generate(capture);
        Map<SectionPos, List<WorldMesh>> results = new ConcurrentHashMap<>();

        var dirtySections = capture.getDirtySections();
        var futures = new CompletableFuture<?>[dirtySections.size()];

        int i = 0;
        for (SectionPos sPos : dirtySections) {
            futures[i++] = CompletableFuture
                    .runAsync(() -> results.put(sPos, tessellateSectionMeshes(sPos, capture, cache)),
                            Util.backgroundExecutor())
                    .exceptionally(e -> {
                        LOGGER.error("Error meshing deltas for section {}", sPos, e);
                        return null;
                    });
        }

        return CompletableFuture.allOf(futures).thenApply(v -> results);
    }

    /**
     * Queue meshing tasks for every section of a chunk, backed by the given base world snapshot.
     *
     * @param cPos      The chunk to mesh.
     * @param baseWorld Base world snapshot to render the sections against.
     * @param minY      The chunk-coordinate of the lowest chunk section to mesh.
     * @param height    The number of chunk sections to mesh vertically.
     */
    public void queueChunk(ChunkPos cPos, Map<ChunkPos, SimpleSectionColumn> baseWorld, int minY, int height) {
        SectionColumnRenderRegion region = SectionColumnRenderRegion.build(baseWorld, cPos, this.baseWorld);
        Map<SectionPos, SectionColumnRenderRegion> sections = new HashMap<>(height);
        for (int i = minY; i < minY + height; i++) {
            sections.put(SectionPos.of(cPos, i), region);
        }
        taskManager.addTasks(sections);
    }

    /**
     * Cancel the meshing task for a given section, if one is queued or in progress.
     *
     * @param pos Section to cancel.
     */
    public void cancelSection(SectionPos pos) {
        taskManager.cancelTask(pos);
    }

    private void tessellateSectionFrame(SectionPos sPos, int tick, Collection<BlockPos> blocks,
                                        BlockAndTintGetter renderView, BlockUpdateCache cache,
                                        Consumer<WorldMesh> meshConsumer) {
        BlockPos origin = sPos.origin();
        Int2ObjectMap<Set<BlockPos>> overrideMap = new Int2ObjectOpenHashMap<>();
        Set<BlockPos> blocksWithoutOverrides = new HashSet<>();

        for (var bPos : blocks) {
            if (!sPos.equals(SectionPos.of(bPos))) continue;
            int nextKeyframe = cache.getNextBlockUpdate(bPos, tick);
            if (nextKeyframe >= 0) {
                overrideMap.computeIfAbsent(nextKeyframe, k -> new HashSet<>()).add(bPos);
            } else {
                blocksWithoutOverrides.add(bPos);
            }
        }

        for (var entry : overrideMap.int2ObjectEntrySet()) {
            Obj mesh = tessellator.compileBlocks(entry.getValue(), renderView, origin);
            meshConsumer.accept(new WorldMesh(mesh, tick, entry.getIntKey() - 1));
        }
        if (!blocksWithoutOverrides.isEmpty()) {
            Obj mesh = tessellator.compileBlocks(blocksWithoutOverrides, renderView, origin);
            meshConsumer.accept(new WorldMesh(mesh, tick, null));
        }
    }

    private List<WorldMesh> tessellateSectionMeshes(SectionPos sPos, WorldCapture capture, BlockUpdateCache cache) {
        Int2ObjectSortedMap<Set<BlockPos>> updates = cache.getSectionUpdates(sPos);
        List<WorldMesh> meshes = new ArrayList<>();
        Set<BlockPos> overwrittenBlocks = new HashSet<>();

        // Mesh per update tick
        for (var entry : updates.int2ObjectEntrySet()) {
            var renderView = new SnapshotRenderView(capture, baseWorld, entry.getIntKey());
            tessellateSectionFrame(sPos, entry.getIntKey(), entry.getValue(), renderView, cache, meshes::add);
            overwrittenBlocks.addAll(entry.getValue());
        }

        var baseRenderView = SectionColumnRenderRegion.build(capture.getCopiedBaseWorld(), sPos.chunk(), baseWorld);

        // Blocks present at tick 0 that are updated later
        if (!overwrittenBlocks.isEmpty()) {
            tessellateSectionFrame(sPos, 0, overwrittenBlocks, baseRenderView, cache, meshes::add);
        }

        // Everything that never changes
        BlockPos origin = sPos.origin();
        Iterable<BlockPos> staticBlocks = Iterables.filter(
                BlockPos.betweenClosed(origin, origin.offset(15, 15, 15)),
                p -> !overwrittenBlocks.contains(p)
        );

        Obj baseMesh = tessellator.compileBlocks(staticBlocks, baseRenderView, origin);
        if (baseMesh.getNumFaces() > 0) {
            meshes.add(new WorldMesh(baseMesh));
        }

        if (mergeDoubleVertices) {
            meshes.replaceAll(m -> new WorldMesh(MeshMergeVerts.mergeByDistance(m.obj(), .001f), m.meta()));
        }
        return meshes;
    }

    /**
     * Start the primary world tessellation
     */
    public void startBase() {
        taskManager.start();
    }

    /**
     * Wait for the base world tessellation to finish
     *
     * @return All the base section OBJs with their section positions
     */
    public CompletableFuture<Map<SectionPos, Obj>> finishBase() {
        return taskManager.finish();
    }
}