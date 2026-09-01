package com.igrium.worldexport.entity;

import com.igrium.worldexport.blockentity.BlockModelAdapter;
import com.igrium.worldexport.blockentity.BlockModelAdapters;
import com.igrium.worldexport.event.EntityCaptureEvents;
import com.igrium.worldexport.math.ChunkSections;
import com.igrium.worldexport.replay.MaterialHolder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Tolerate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Manages the capturing entity movements.
 */
public class EntityCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityCapture.class);

    private final Map<EntityType<?>, ModelAdapter<?, ?>> modelAdapters = new HashMap<>();

    private final Map<BlockEntityType<?>, BlockModelAdapter<?, ?>> blockModelAdapters = new HashMap<>();

    /**
     * The world-space bounds of the export.
     */
    @Getter
    @Setter
    @NonNull
    private AABB bounds;

    /**
     * A predicate to determine if any given entity should be exported.
     */
    @Getter
    @Setter
    @NonNull
    private Predicate<? super Entity> entityPredicate = e -> true;

    /**
     * A predicate to determine if any given block entity should be exported.
     * Only considers "dynamic" elements of the block entity. Static elements are meshed with the world.
     */
    @Getter
    @Setter
    @NonNull
    private Predicate<? super BlockEntity> blockEntityPredicate = e -> true;

    /**
     * An offset to apply to each entity after exporting the replay file.
     */
    @Getter
    @Setter
    @NonNull
    private Vec3 globalOffset = Vec3.ZERO;

    /**
     * Any textures that entities require will be added to this map.
     */
    @Getter
    @Setter
    @NonNull
    private MaterialHolder materialHolder = new MaterialHolder();

    @Tolerate
    public void setGlobalOffset(Vec3i offset) {
        this.globalOffset = new Vec3(offset.getX(), offset.getY(), offset.getZ());
    }

    /**
     * All entities that have been captured with their corresponding animation data.
     */
    @Getter
    private final Map<Entity, CapturedEntity> entities = new HashMap<>();

    /**
     * All block entities that have been captured with their corresponding animation data.
     */
    @Getter
    private final Map<BlockEntity, CapturedEntity> blockEntities = new HashMap<>();

    public EntityCapture(@NotNull AABB bounds) {
        this.bounds = bounds;
    }


    // TODO: Is there ANYTHING we can do about the generic shitshow that is model adapters?
    @SuppressWarnings("unchecked")
    public <T extends Entity> ModelAdapter<T, ?> getModelAdapter(T entity) {
        EntityType<?> type = entity.getType(); // EntityType<T> for unchecked
        return (ModelAdapter<T, ?>) modelAdapters.computeIfAbsent(type, t -> ModelAdapters.createModelAdapter(entity));
    }

    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockModelAdapter<T, ?> getBlockModelAdapter(T blockEntity) {
        BlockEntityType<?> type = blockEntity.getType();
        return (BlockModelAdapter<T, ?>) blockModelAdapters.computeIfAbsent(type,
                t -> BlockModelAdapters.createModelAdapter(blockEntity));
    }

    /**
     * Capture all entity poses for this frame.
     *
     * @param world World to get entities from.
     * @param tick  The frame index in the replay file.
     */
    public void captureFrame(Level world, int tick) {
        Minecraft client = Minecraft.getInstance();
        //noinspection DataFlowIssue
        client.getEntityRenderDispatcher().prepare(client.gameRenderer.mainCamera(), client.crosshairPickEntity);
        client.getBlockEntityRenderDispatcher().prepare(client.gameRenderer.mainCamera().position());

        var entities = world.getEntities((Entity) null, bounds, entityPredicate);
        for (var entity : entities) {
            try {
                captureEntity(entity, tick);
            } catch (Exception e) {
                LOGGER.error("Error capturing pose for entity {} on tick {}:", entity.getName(), tick, e);
            }
        }

        // BLOCK ENTITIES
        // TODO: can we get these from the game in a way more analogous to how the render engine does it rather than
        //  manually?
        int minChunkX = SectionPos.blockToSectionCoord(bounds.minX);
        int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ);
        int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX);
        int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ);

        for (ChunkPos cPos : ChunkSections.iterate(minChunkX, maxChunkX, minChunkZ, maxChunkZ)) {
            ChunkAccess cAccess = world.getChunk(cPos.x(), cPos.z(), ChunkStatus.FULL, false);
            if (!(cAccess instanceof LevelChunk chunk)) continue; // Also tests for null

            for (var entry : chunk.getBlockEntities().entrySet()) {
                BlockPos pos = entry.getKey();
                BlockEntity ent = entry.getValue();
                if (client.getBlockEntityRenderDispatcher().getRenderer(ent) == null) {
                    continue; // Indicates this block entity doesn't render dynamically.
                }
                if (!bounds.contains(Vec3.atCenterOf(pos))
                        || !blockEntityPredicate.test(ent)) continue;

                try {
                    captureBlockEntity(ent, tick);
                } catch (Exception e) {
                    LOGGER.error("Error capturing pose for block entity at {} ({}) on tick {}:", pos, ent, tick, e);
                }
            }
        }
    }

    private <T extends Entity> void captureEntity(T entity, int tick) {
        if (!EntityCaptureEvents.BEFORE_CAPTURE_ENTITY.invoker().beforeCaptureEntity(
                this, entity, tick)) {
            return;
        }
        var modelAdapter = getModelAdapter(entity);
        captureModelAdapter(modelAdapter, entity, tick);
    }

    private <T extends Entity, S extends EntityRenderState> void captureModelAdapter(ModelAdapter<T, S> modelAdapter,
                                                                                     T entity, int tick) {
        S state = modelAdapter.getAndUpdateRenderState(entity);
        CapturedEntity capture = entities.computeIfAbsent(entity, e -> new CapturedEntity());
        modelAdapter.capture(entity, state, capture, materialHolder, globalOffset, tick);
    }

    private <T extends BlockEntity> void captureBlockEntity(T blockEntity, int tick) {
        if (!EntityCaptureEvents.BEFORE_CAPTURE_BLOCK_ENTITY.invoker().beforeCaptureBlockEntity(
                this, blockEntity, tick)) {
            return;
        }
        var modelAdapter = getBlockModelAdapter(blockEntity);
        captureBlockModelAdapter(modelAdapter, blockEntity, tick);
    }

    private <T extends BlockEntity, S extends BlockEntityRenderState> void captureBlockModelAdapter(BlockModelAdapter<T, S> modelAdapter,
                                                                                                    T entity, int tick) {
        S state = modelAdapter.getAndUpdateRenderState(entity, Minecraft.getInstance().gameRenderer.mainCamera().position());
        CapturedEntity capture = blockEntities.computeIfAbsent(entity, e -> new CapturedEntity());
        modelAdapter.capture(entity, state, capture, materialHolder, globalOffset, tick);
    }
}
