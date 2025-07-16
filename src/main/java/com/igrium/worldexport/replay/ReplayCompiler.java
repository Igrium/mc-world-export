package com.igrium.worldexport.replay;

import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Compiles a replay into a "serialized" state that can be saved to disk.
 */
public class ReplayCompiler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayCompiler.class);

    private final ReplayCapture replayCapture;

    public ReplayCompiler(ReplayCapture replayCapture) {
        this.replayCapture = replayCapture;
    }

    public CompletableFuture<CompiledReplay> compile() {
        LOGGER.info("Compiling replay export");
        if (replayCapture.getState() != ReplayCapture.ReplayCaptureState.FINISHED) {
            LOGGER.warn("Replay capture is not finished. Compilation could exhibit unwanted behavior.");
        }

        long startTime = Util.getMeasuringTimeMs();
        CompiledReplay replay = new CompiledReplay();
        return compileWorld(replay)
                .thenApply(this::compileEntities)
                .thenCompose(this::compileTextures).thenApply(r -> {
            LOGGER.info("Compiled replay export in {}ms", Util.getMeasuringTimeMs() - startTime);
            return r;
        });
    }

    private CompletableFuture<CompiledReplay> compileWorld(CompiledReplay replay) {
        LOGGER.info("Tessellating block world...");
        return replayCapture.getWorldTessellator().tessellateAllMeshes(null).thenApply(meshes -> {
            replay.getWorldMeshes().addAll(meshes);
            return replay;
        });
    }

    private CompiledReplay compileEntities(CompiledReplay replay) {
        for (var entEntry : replayCapture.getEntityCapture().getEntities().entrySet()) {
            String name = getUniqueName(entEntry.getKey().getName().getString(), replay.getEntities().keySet());
            replay.getEntities().put(name, entEntry.getValue());
        }
        return replay;
    }

    private CompletableFuture<CompiledReplay> compileTextures(CompiledReplay replay) {
        LOGGER.info("Extracting textures...");
        return replayCapture.getAllTextures().thenApply(map -> {
            replay.getTextures().putAll(map);
            replay.getMtlLibs().putAll(replayCapture.getMtlLibs());
            return replay;
        });
    }


    public static String getUniqueName(String baseName, Collection<? extends String> existing) {
        String name = baseName;
        int conflictIndex = 1;
        while (existing.contains(name)) {
            name = baseName + String.format("%03d", conflictIndex);
            conflictIndex++;
        }
        return name;
    }
}
