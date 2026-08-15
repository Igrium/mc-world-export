package com.igrium.worldexport.replay;

import com.google.common.collect.ImmutableList;
import com.igrium.worldexport.entity.EntityCapture;
import com.igrium.worldexport.math.ChunkSections;
import com.igrium.worldexport.mesh.WorldMesh;
import com.igrium.worldexport.mesh.WorldMesher;
import com.igrium.worldexport.mesh.postprocess.MeshUtils;
import com.igrium.worldexport.tex.ManagedNativeImage;
import com.igrium.worldexport.world.WorldCapture;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Responsible for capturing a replay.
 */
public class ReplayCapture {

    public enum ReplayCaptureState {
        /**
         * The ReplayCapture object has been created, but it hasn't begun capture yet.
         */
        NEW,
        /**
         * The ReplayCapture is currently recording.
         */
        RUNNING,
        /**
         * The ReplayCapture has finished recording.
         */
        FINISHED
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/ReplayCapture");

    private static final Set<ReplayCapture> activeCaptures = new HashSet<>();
    private static final Set<ReplayCapture> activeCapturesUnmodifiable = Collections.unmodifiableSet(activeCaptures);


    public static Set<ReplayCapture> getActiveCaptures() {
        return activeCapturesUnmodifiable;
    }

    /**
     * Event listener for <code>ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE</code>
     */
    public static void globalEndClientTick(Minecraft client) {
        // Duplicate to avoid concurrent modification if capture decides to end.
        for (var cap : activeCaptures.toArray(ReplayCapture[]::new)) {
            cap.onEndTick();
        }
    }

    /**
     * Event listener for <code>ClientBlockUpdatedEvent</code>
     */
    public static void globalClientBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, Level world) {
        for (var cap : activeCaptures) {
            cap.onUpdateBlock(pos, newState, world);
        }
    }

    /**
     * Event listener for <code>ClientChunkEvents.CHUNK_LOAD</code>
     */
    public static void globalClientChunkLoad(ClientLevel world, LevelChunk chunk) {
        for (var cap : activeCaptures) {
            cap.onLoadChunk(world, chunk);
        }
    }

    /**
     * Event listener for <code>ClientWorldEvents.SET_WORLD</code>
     */
    public static void globalClientWorldChange(Minecraft client, Level world) {
        for (var cap : activeCaptures.toArray(ReplayCapture[]::new)) {
            cap.finish();
        }
    }

    @Getter
    private final Level world;

    @Getter
    private final ReplayExportSettings settings;

    @Getter
    private final WorldCapture worldCapture;

    @Getter
    private final EntityCapture entityCapture;

    @Getter
    private final MaterialHolder materialHolder = new MaterialHolder();


    @Getter
    private final Executor executor;

    @Getter
    private ReplayCaptureState state = ReplayCaptureState.NEW;

    private int gameTick;
    private int replayTick;

    public ReplayCapture(ClientLevel world, ReplayExportSettings settings) {
        this.world = world;
        this.settings = settings;

        executor = Util.backgroundExecutor(); // Make our own as to not starve this.


        var bounds = settings.getWorldBounds();

        Predicate<BlockPos> updatePredicate = pos -> {
            var uBounds = this.settings.updateBounds();
            return uBounds.isInBounds(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
        };

        worldCapture = new WorldCapture(world, ChunkSections.getSet(bounds.minX(), bounds.minZ(),
                bounds.maxXInclusive(), bounds.maxZInclusive()), updatePredicate, bounds.minY(), bounds.sizeY());

        // WorldMesher owns its own worker threads, so it takes no executor.
        var worldMesher = worldCapture.getMesher();
        worldMesher.setOffset(settings.getOffset());
        worldMesher.setSplitBlocks(settings.isSplitBlocks());
        worldMesher.setMaxThreads(settings.getMaxThreads());
        // TODO: WorldMesher stores these but doesn't act on them yet.
        worldMesher.setMergeBaseMeshes(settings.isMergeBaseMeshes());
        worldMesher.setMergeDoubleVertices(settings.isMergeDoubleVertices());

        entityCapture = new EntityCapture(settings.entityBounds());
        entityCapture.setGlobalOffset(settings.getOffset());
        entityCapture.setMaterialHolder(materialHolder);
    }

    /**
     * The replay texture name of the world atlas texture.
     */
    public static String WORLD_TEX = "world.png";

    public static String ITEM_TEX = "item.png";

    /**
     * Capture the base world and begin tessellating base meshes.
     */
    public void beginCapture() {
        if (state != ReplayCaptureState.NEW) {
            LOGGER.warn("Capture has already begun.");
            return;
        }

        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("beginCapture can only be called from the primary client thread.");
        }

        long captureStart = Util.getMillis();

        worldCapture.captureBaseWorld();
        LOGGER.info("Cloned base world in {}ms", Util.getMillis() - captureStart);


        worldCapture.getMesher().startBase();
        gameTick = 0;


        materialHolder.getTextures().put(ITEM_TEX, WorldMesher.getDefaultItemTexture());
        materialHolder.getTextures().put(WORLD_TEX, WorldMesher.getDefaultWorldTexture());
        materialHolder.putMtlLib("world.mtl", WorldMesher.getDefaultWorldMtls());

        activeCaptures.add(this);
        state = ReplayCaptureState.RUNNING;
    }

    public void onEndTick() {
        int stride = settings.getTickStride();
        if (gameTick % stride == 0) {
            entityCapture.captureFrame(world, replayTick);
            replayTick++;
        }
        gameTick++;
    }

    public void onUpdateBlock(BlockPos globalPos, BlockState newBlock, Level world) {
        if (world == this.world) {
            worldCapture.addBlockUpdate(globalPos, newBlock, replayTick);
        }
    }

    public void onLoadChunk(Level world, LevelChunk chunk) {
        if (world == this.world) {
            worldCapture.onChunkLoaded(chunk, replayTick);
        }
    }

    /**
     * Wait for the world meshes to finish tessellating and assemble them into the final, named meshes.
     *
     * @return A future that completes with a map of mesh names and their meshes.
     */
    public CompletableFuture<Map<String, WorldMesh>> compileWorldMeshes() {
        var baseFuture = worldCapture.getMesher().finishBase();
        var deltaFuture = worldCapture.getMesher().tessellateDeltas(worldCapture);

        return baseFuture.thenCombine(deltaFuture, (sections, deltas) -> {
            Map<String, WorldMesh> meshes = new HashMap<>();
            List<String> mtlLibs = ImmutableList.of("world.mtl");

            // Base meshes
            WorldMesh base;
            if (getSettings().isMergeBaseMeshes()) {
                LOGGER.info("Merging base meshes...");
                base = new WorldMesh(Objs.create(), Vec3.atLowerCornerOf(settings.getOffset()));
                base.obj().setMtlFileNames(mtlLibs);
                for (var entry : sections.entrySet()) {
                    if (entry.getValue().getNumFaces() == 0) continue;
                    MeshUtils.merge(entry.getValue(), base.obj(), toVec3f(entry.getKey().origin()));
                }
                meshes.put("world", base);
            } else {
                base = null;
                for (var entry : sections.entrySet()) {
                    Obj obj = entry.getValue();
                    if (obj.getNumFaces() == 0) continue;

                    obj.setMtlFileNames(mtlLibs);

                    SectionPos sPos = entry.getKey();
                    BlockPos origin = sPos.origin().offset(settings.getOffset());
                    meshes.put(sectionName(sPos), new WorldMesh(obj, Vec3.atLowerCornerOf(origin)));
                }
            }

            // Deltas
            for (var entry : deltas.entrySet()) {
                SectionPos sPos = entry.getKey();
                BlockPos origin = sPos.origin().offset(settings.getOffset());
                Vec3 offset = Vec3.atLowerCornerOf(origin);

                int i = 0;
                for (WorldMesh mesh : entry.getValue()) {
                    if (mesh.obj().getNumFaces() == 0) continue;

                    // Blocks that aren't touched in chunks with deltas still get applied to base
                    // base != null when mergeBaseMeshes == true
                    if (base != null && !mesh.meta().isTickBounded()) {
                        MeshUtils.merge(mesh.obj(), base.obj(), toVec3f(entry.getKey().origin()));
                    } else {
                        mesh.obj().setMtlFileNames(mtlLibs);
                        mesh.meta().setOffset(offset);
                        meshes.put(sectionName(sPos) + "_" + i++, mesh);
                    }
                }
            }

            LOGGER.info("Wrote {} world meshes (base + deltas)", meshes.size());
            return meshes;
        });
    }

    private static String sectionName(SectionPos sPos) {
        return "section_" + sPos.getX() + "_" + sPos.getY() + "_" + sPos.getZ();
    }

    /**
     * Wait for all texture futures to complete and return their values.
     *
     * @return A map of all texture paths and their values.
     */
    public CompletableFuture<Map<String, ManagedNativeImage>> getAllTextures() {
        Map<String, ManagedNativeImage> result = new ConcurrentHashMap<>(materialHolder.getTextures().size());
        List<CompletableFuture<?>> futures = new ArrayList<>(materialHolder.getTextures().size());

        for (var entry : materialHolder.getTextures().entrySet()) {
            futures.add(entry.getValue()
                    .thenAccept(i -> result.put(entry.getKey(), i))
                    .exceptionally(e -> {
                        LOGGER.error("Error retrieving texture {}: {}", entry.getKey(), e);
                        return null;
                    }));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> result);
    }


    /**
     * Finish recording this replay.
     */
    public void finish() {
        if (state != ReplayCaptureState.RUNNING) {
            LOGGER.warn("Replay capture must be running to call finish()");
            return;
        }

        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("finish() can only be called on the primary client thread.");
        }

        activeCaptures.remove(this);
        state = ReplayCaptureState.FINISHED;
    }

    private static Vector3f toVec3f(Vec3i vec) {
        return new Vector3f(vec.getX(), vec.getY(), vec.getZ());
    }
}
