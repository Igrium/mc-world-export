package com.igrium.worldexport.replay;

import com.igrium.worldexport.entity.EntityCapture;
import com.igrium.worldexport.tex.ReplayTexture;
import com.igrium.worldexport.world.WorldCapture;
import com.igrium.worldexport.world.WorldMesh;
import com.igrium.worldexport.world.WorldMesher;
import de.javagl.obj.Obj;
import lombok.Getter;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayCapture.class);

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
    private final WorldMesher worldMesher;

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

        worldCapture = new WorldCapture(settings.getBounds());

        // WorldMesher owns its own worker threads, so it takes no executor.
        worldMesher = new WorldMesher(worldCapture, world, settings.getBounds().iterate());
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
        worldCapture.captureBaseWorld(world);
        LOGGER.info("Cloned base world in {}ms", Util.getMillis() - captureStart);

        long meshStartTime = Util.getMillis();
        worldMesher.tessellateBaseWorld().thenRun(() -> {
            LOGGER.info("Finished tessellating base world in {}ms", Util.getMillis() - meshStartTime);
        }).exceptionally(e -> {
            LOGGER.error("Error tessellating base world: ", e);
            return null;
        });
        gameTick = 0;


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
        // Disabled until WorldMesher can generate delta meshes; nothing consumes these updates.
//        if (world == this.world) {
//            worldCapture.addBlockUpdate(globalPos, newBlock, replayTick);
//        }
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
     * @implNote TODO: only the static base world is exported. Delta meshes (block updates over time)
     *           aren't implemented, so their capture is commented out in onUpdateBlock. Base mesh
     *           merging, double vertex removal and progress callbacks are missing too.
     */
    public CompletableFuture<Map<String, WorldMesh>> compileWorldMeshes() {
        // TODO: refactor this so that newly-loaded chunks get added, etc
        CompletableFuture<Map<SectionPos, Obj>> baseFuture = worldMesher.getTaskManager().getCompletionFuture();

        return baseFuture.thenApply(sections -> {
            Map<String, WorldMesh> meshes = new HashMap<>(sections.size());
            List<String> mtlLibs = List.of("world.mtl");

            for (var entry : sections.entrySet()) {
                Obj obj = entry.getValue();
                if (obj.getNumFaces() == 0)
                    continue;

                obj.setMtlFileNames(mtlLibs);

                // BlockTessellator emits geometry relative to the section origin, so each mesh
                // carries its world position (plus the export offset) in its metadata.
                SectionPos sPos = entry.getKey();
                BlockPos origin = sPos.origin().offset(settings.getOffset());
                String name = "section_" + sPos.getX() + "_" + sPos.getY() + "_" + sPos.getZ();

                meshes.put(name, new WorldMesh(obj, Vec3.atLowerCornerOf(origin)));
            }

            return meshes;
        });
    }

    /**
     * Wait for all texture futures to complete and return their values.
     * @return A map of all texture paths and their values.
     */
    public CompletableFuture<Map<String, ReplayTexture>> getAllTextures() {
        Map<String, ReplayTexture> result = new ConcurrentHashMap<>(materialHolder.getTextures().size());
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
}
