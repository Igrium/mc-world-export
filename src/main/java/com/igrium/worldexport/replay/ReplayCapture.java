package com.igrium.worldexport.replay;

import com.igrium.worldexport.IgriumsReplayExporter;
import com.igrium.worldexport.concurrent.LimitedConcurrencyExecutor;
import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.world.WorldCapture;
import com.igrium.worldexport.world.WorldTessellator;
import lombok.Getter;
import net.minecraft.util.Util;
import net.minecraft.world.World;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Responsible for capturing a replay.
 */
public class ReplayCapture {

    private static final Logger LOGGER = IgriumsReplayExporter.LOGGER;

    @Getter
    private final World world;

    @Getter
    private final ReplaySettings settings;

    @Getter
    private final WorldCapture worldCapture;

    @Getter
    private final WorldTessellator worldTessellator;

    private final Executor executor;
    private boolean hasBegunCapture;

    private int gameTick;
    private int replayTick;

    public ReplayCapture(World world, ReplaySettings settings) {
        this.world = world;
        this.settings = settings;
//        executor = new LimitedConcurrencyExecutor(settings.getMaxThreads(), Util.getMainWorkerExecutor());
        executor = Util.getMainWorkerExecutor(); // limited concurrency doesn't work rn.

        worldCapture = new WorldCapture(settings.getBounds());

        worldTessellator = new WorldTessellator(worldCapture, world);
        worldTessellator.setExecutor(executor);
        worldTessellator.setOffset(settings.getOffset());
        worldTessellator.setSplitBlocks(settings.isSplitBlocks());

    }

    public ReplayCapture(World world, ReplaySettings.ReplaySettingsBuilder settings) {
        this(world, settings.build());
    }

    /**
     * Capture the base world and begin tessellating base meshes.
     */
    public void beginCapture() {
        if (hasBegunCapture) {
            LOGGER.warn("Capture has already begun.");
            return;
        }

        long captureStart = Util.getMeasuringTimeMs();
        worldCapture.captureBaseWorld(world);
        LOGGER.info("Cloned base world in {}ms", Util.getMeasuringTimeMs() - captureStart);

        long meshStartTime = Util.getMeasuringTimeMs();
        worldTessellator.tessellateBaseWorld();
        worldTessellator.awaitBaseTessellationFinished().thenRun(() -> {
            LOGGER.info("Finished tessellating base world in {}ms", Util.getMeasuringTimeMs() - meshStartTime);
        });
        gameTick = 0;
        hasBegunCapture = true;
    }

    public void onEndTick() {
        int stride = settings.getTickStride();
        if (gameTick % stride == 0) {
            // do tick logic
            replayTick++;
        }
        gameTick++;
    }

    public CompletableFuture<CapturedReplay> compile() {
        long startTime = Util.getMeasuringTimeMs();
        return worldTessellator.tessellateAllMeshes(null).thenApply(meshes -> {
            CapturedReplay replay = new CapturedReplay();
            replay.getWorldMeshes().addAll(Arrays.asList(meshes));
            LOGGER.info("Compiled replay in {}ms", Util.getMeasuringTimeMs() - startTime);
            return replay;
        });
    }
}
