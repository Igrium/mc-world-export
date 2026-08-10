package com.igrium.worldexport.mesh;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.igrium.worldexport.mesh.postprocess.MeshMergeVerts;
import com.igrium.worldexport.mesh.tessellate.BlockModelOverride;
import com.igrium.worldexport.mesh.tessellate.BlockStateModelSupplier;
import com.igrium.worldexport.mesh.tessellate.BlockTessellator;
import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.tex.NativeImageReplayTexture;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.TextureExtractor;
import com.igrium.worldexport.util.TaskManager;
import com.igrium.worldexport.world.*;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import it.unimi.dsi.fastutil.ints.*;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    private final TaskManager<SectionPos, BlockAndTintGetter, Obj> taskManager;

    /**
     * Any block models which should be overwritten during export
     * <p>
     * Supplier is used so that these get re-calculated when resourcepacks are reloaded. Called for every block;
     * should not be expensive.
     */
    @Getter
    private final Map<BlockState, Supplier<BlockModelOverride>> modelOverrides = new HashMap<>();

    // Must be concurrently readable
    private @Nullable ImmutableMap<BlockState, BlockModelOverride> modelOverrideCache;

    // I don't know if this actually needs to be atomic, but better safe than sorry
    private final AtomicInteger totalSections = new AtomicInteger(0);

    public int getTotalSections() {
        return totalSections.get();
    }

    public int getFinishedSections() {
        return taskManager.getFinishedTasks();
    }

    private final ExportBlockColors blockColors;

    public WorldMesher(ClientLevel baseWorld) {
        this.baseWorld = baseWorld;

        var modelManager = Minecraft.getInstance().getModelManager();
        var modelSupplier = new OverrideBlockStateModelSupplier(modelManager.getBlockStateModelSet());
        blockColors = new ExportBlockColors(Minecraft.getInstance().getBlockColors());
        tessellator = BlockTessellator.builder()
                .blockColors(blockColors)
                .blockModelSet(modelSupplier)
                .fluidModelSet(modelManager.getFluidStateModelSet())
                .blockMatFactory(this::getMaterialName)
                .fluidMatFactory(this::getDefaultMaterialName)
                .splitBlocks(this.splitBlocks)
                .build();

        registerDefaultOverrides();
        taskManager = new TaskManager<>(Runtime.getRuntime().availableProcessors() - 1, this::tessellateSection);
    }

    /// === MATERIALS & TEXTURES ===

    public String getDefaultMaterialName(BlockState state) {
        return state.propagatesSkylightDown() ? WORLD_TRANS : WORLD;
    }

    public String getMaterialName(BlockState state) {
        var override = modelOverrideCache != null ? modelOverrideCache.get(state) : null;
        return override != null && override.material() != null ? override.material() : getDefaultMaterialName(state);
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

    public static CompletableFuture<NativeImageReplayTexture> getDefaultItemTexture() {
        return TextureExtractor.pullAtlasTextureAsync(AtlasIds.ITEMS)
                .thenApply(NativeImageReplayTexture::new);
    }

    /// === MESHING ===

    private Obj tessellateSection(SectionPos pos, BlockAndTintGetter region) {
        Obj obj = tessellator.compileSection(pos, region);
        return mergeDoubleVertices ? MeshMergeVerts.mergeByDistance(obj, .001f, true, .15f) : obj;
    }

    public CompletableFuture<Map<SectionPos, List<WorldMesh>>> tessellateDeltas(WorldCapture capture) {
        BlockUpdateCache cache = BlockUpdateCache.generate(capture);
        buildOverrideCache();
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
        totalSections.addAndGet(sections.size());
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
            meshes.replaceAll(m -> new WorldMesh(MeshMergeVerts.mergeByDistance(
                    m.obj(), .001f, true, 0f), m.meta()));
        }
        return meshes;
    }

    /**
     * Start the primary world tessellation
     */
    public void startBase() {
        buildOverrideCache();
        taskManager.start();
    }

    /**
     * Wait for the base world tessellation to finish
     *
     * @return All the base section OBJs with their section positions
     */
    public CompletableFuture<Map<SectionPos, Obj>> finishBase() {
        return taskManager.finish().whenComplete((_, _) -> {
            int tasks = taskManager.getCanceledTasks();
            if (tasks > 0) {
                LOGGER.warn("Computed {} redundant section meshes.", tasks);
            }
        });
    }

    public void registerDefaultOverrides() {
        registerBlockOverride(Blocks.GRASS_BLOCK, ExportModels.GRASS_BLOCK_KEY, null, Int2ObjectMaps.singleton(5, GRASS_MAT));
    }

    public void registerBlockOverride(Block block, ExtraModelKey<BlockStateModel> modelKey, @Nullable String material, @Nullable Int2ObjectMap<String> faceMats) {
        LOGGER.info("Registering model override for {}: {}", block, modelKey);
        Supplier<BlockModelOverride> supplier = () -> {
            BlockStateModel model = Minecraft.getInstance().getModelManager().getModel(modelKey);
            if (model == null) {
                LOGGER.warn("Export model {} was not baked; falling back to vanilla model.", modelKey);
                return null;
            }
            return new BlockModelOverride(model, material, faceMats);
        };
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            modelOverrides.put(state, supplier);
        }
    }

    private void buildOverrideCache() {
        var builder = ImmutableMap.<BlockState, BlockModelOverride>builder();
        for (var entry : modelOverrides.entrySet()) {
            var model = entry.getValue().get();
            if (model != null)
                builder.put(entry.getKey(), model);
        }
        modelOverrideCache = builder.build();
        blockColors.build();
    }

    private class OverrideBlockStateModelSupplier implements BlockStateModelSupplier {

        private final BlockStateModelSet base;

        private OverrideBlockStateModelSupplier(BlockStateModelSet base) {
            this.base = base;
        }

        @Override
        public BlockStateModel get(BlockState blockState) {
            BlockModelOverride override = modelOverrideCache != null ? modelOverrideCache.get(blockState) : null;
            return override != null ? override.model() : base.get(blockState);
        }

        @Override
        public BlockStateModel missingModel() {
            return base.missingModel();
        }
    }

    /**
     * Overrides block colors during rendering for models where tint idx is being used to pass data
     */
    private class ExportBlockColors extends BlockColors {

        private static final BlockTintSource BLANK = BlockTintSources.constant(-1);
        private final BlockColors base;

        private ImmutableMap<BlockState, List<BlockTintSource>> tintSources = ImmutableMap.of();

        private ExportBlockColors(BlockColors base) {
            this.base = base;
        }

        public void build() {
            var builder = ImmutableMap.<BlockState, List<BlockTintSource>>builder();
            var cache = modelOverrideCache;
            if (cache != null) {
                for (var entry : cache.entrySet()) {
                    BlockModelOverride override = entry.getValue();
                    if (override.faceMats() != null && !override.faceMats().isEmpty()) {
                        builder.put(entry.getKey(), compute(entry.getKey()));
                    }
                }
            }
            tintSources = builder.build();
        }

        @Override
        public @NonNull List<BlockTintSource> getTintSources(@NonNull BlockState state) {
            List<BlockTintSource> computed = tintSources.get(state);
            return computed != null ? computed : base.getTintSources(state);
        }

        private List<BlockTintSource> compute(BlockState state) {
            BlockModelOverride override = modelOverrideCache != null ? modelOverrideCache.get(state) : null;
            if (override == null || override.faceMats() == null || override.faceMats().isEmpty())
                return Collections.emptyList();

            int maxIdx = 0;
            IntIterator iter = override.faceMats().keySet().iterator();
            while (iter.hasNext()) {
                maxIdx = Math.max(maxIdx, iter.nextInt());
            }

            // Pad the list to the desired idx. Blank except for indices with overrides
            List<BlockTintSource> list = new ArrayList<>(base.getTintSources(state));
            BlockTintSource first = list.isEmpty() ? BLANK : list.getFirst();
            for (int i = list.size(); i <= maxIdx; i++) {
                list.add(override.faceMats().containsKey(i) ? first : BLANK);
            }
            return list;
        }

        @Override
        public void register(@NonNull List<BlockTintSource> layers, Block @NonNull ... blocks) {
            base.register(layers, blocks);
        }

        @Override
        public @NonNull Set<Property<?>> getColoringProperties(@NonNull Block block) {
            return base.getColoringProperties(block);
        }
    }
}