package com.igrium.worldexport.world;

import com.igrium.worldexport.mesh.BlockMaterialFactory;
import com.igrium.worldexport.mesh.BlockTessellator;
import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.tex.NativeImageReplayTexture;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.TextureExtractor;
import com.igrium.worldexport.util.TaskExecutor;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WorldMesher {
    public static final String WORLD = "world";
    public static final String WORLD_TRANS = "world_trans";
    public static final String GRASS_MAT = "grass_block";

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/WorldMesher");

    private final WorldCapture worldCapture;

    private final ClientLevel baseWorld;

    private final Iterable<? extends SectionPos> sections;

    private BlockMaterialFactory materialFactory = this::getDefaultMaterialName;

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

    private @Nullable TaskExecutor<SectionPos, SectionColumnRenderRegion, Obj> taskExecutor;

    /**
     * The future returned by the last call to {@link #tessellateBaseWorld()}.
     */
    @Getter
    private @Nullable CompletableFuture<Map<SectionPos, Obj>> baseWorldFuture;

    public WorldMesher(WorldCapture worldCapture, ClientLevel baseWorld, Iterable<? extends SectionPos> sections) {
        this.worldCapture = worldCapture;
        this.baseWorld = baseWorld;
        this.sections = sections;
    }

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
     * @return The cpu-bound atlas texture. Should be saved at <code>world/world.png</code>
     */
    public static CompletableFuture<NativeImageReplayTexture> getDefaultWorldTexture() {
        return TextureExtractor.pullAtlasTextureAsync(AtlasIds.BLOCKS)
                .thenApply(NativeImageReplayTexture::new);
    }

    public CompletableFuture<Map<SectionPos, Obj>> tessellateBaseWorld() {
        if (taskExecutor != null && !taskExecutor.isFinished()) {
            throw new IllegalStateException("TaskExecutor has already been started");
        }

        var modelManager = Minecraft.getInstance().getModelManager();

        BlockTessellator tessellator = BlockTessellator.builder()
                .blockColors(Minecraft.getInstance().getBlockColors())
                .blockModelSet(modelManager.getBlockStateModelSet())
                .fluidModelSet(modelManager.getFluidStateModelSet())
                .blockMatFactory(this::getDefaultMaterialName)
                .fluidMatFactory(this::getDefaultMaterialName)
                .splitBlocks(this.splitBlocks)
                .ambientOcclusion(false)
                .build();

        Map<ChunkPos, SimpleSectionColumn> columns = new HashMap<>();
        for (var sPos : this.sections) {
            columns.computeIfAbsent(sPos.chunk(), cPos -> {
                var chunk = baseWorld.getChunk(cPos.x(), cPos.z(), ChunkStatus.FULL, false);
                if (chunk != null) {
                    return SimpleSectionColumn.fromChunk(chunk);
                } else {
                    return null;
                }
            });
        }

        Map<ChunkPos, SectionColumnRenderRegion> regions = columns.keySet().stream()
                .collect(Collectors.toMap(c -> c,
                        c -> SectionColumnRenderRegion.build(columns, c, baseWorld)));

        Map<SectionPos, SectionColumnRenderRegion> queue = new HashMap<>();
        for (var section : this.sections) {
            var region = regions.get(section.chunk());
            if (region != null) {
                queue.put(section, region);
            }
        }

        taskExecutor = new TaskExecutor<>(queue, Math.max(maxThreads, 1), (pos, region) -> {
            LOGGER.info("Compiling section {}", pos);
            Obj obj = tessellator.compileSection(pos, region);
            if (onSectionTessellated != null) {
                onSectionTessellated.accept(pos);
            }
            return obj;
        });

        taskExecutor.start();
        baseWorldFuture = taskExecutor.getCompletionFuture();
        return baseWorldFuture;
    }
}
