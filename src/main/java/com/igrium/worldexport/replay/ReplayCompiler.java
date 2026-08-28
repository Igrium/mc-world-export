package com.igrium.worldexport.replay;

import lombok.Setter;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Compiles a replay into a "serialized" state that can be saved to disk.
 */
public class ReplayCompiler {
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/ReplayCompiler");

    private final ReplayCapture replayCapture;

    public ReplayCompiler(ReplayCapture replayCapture) {
        this.replayCapture = replayCapture;
    }

    @Setter
    private @Nullable Consumer<String> phaseListener;

    private void setPhase(String phase) {
        if (phaseListener != null) {
            phaseListener.accept(phase);
        }
    }

    public CompletableFuture<CompiledReplay> compile() {
        LOGGER.info("Compiling replay export");
        if (replayCapture.getState() != ReplayCapture.ReplayCaptureState.FINISHED) {
            LOGGER.warn("Replay capture is not finished. Compilation could exhibit unwanted behavior.");
        }

        long startTime = Util.getMillis();
        CompiledReplay replay = new CompiledReplay();
        return compileWorld(replay)
                .thenApply(this::compileEntities)
                .thenCompose(this::compileTextures)
                .thenApply(this::packMtls)
                .thenApply(this::writeMeta)
                .thenApply(r -> {
                    LOGGER.info("Compiled replay export in {}ms", Util.getMillis() - startTime);
                    return r;
                });
    }

    private CompletableFuture<CompiledReplay> compileWorld(CompiledReplay replay) {
        LOGGER.info("Tessellating block world...");
        setPhase(ExportPhase.WORLD);
        return replayCapture.compileWorldMeshes().thenApply(meshes -> {
            replay.getWorldMeshes().putAll(meshes);
            return replay;
        });
    }

    private CompiledReplay compileEntities(CompiledReplay replay) {
        var entCap = replayCapture.getEntityCapture();
        if (entCap == null) return replay;
        setPhase(ExportPhase.ENTITIES);
        for (var entEntry : entCap.getEntities().entrySet()) {
            String sanitized = sanitizeFilename(entEntry.getKey().getName().getString());
            String name = getUniqueName(sanitized, replay.getEntities().keySet());
            replay.getEntities().put(name, entEntry.getValue());
        }
        for (var bEntEntry : entCap.getBlockEntities().entrySet()) {
            String sanitized = sanitizeFilename(bEntEntry.getKey().typeHolder().getRegisteredName());
            String name = getUniqueName(sanitized, replay.getEntities().keySet());
            replay.getEntities().put(name, bEntEntry.getValue());
        }
        return replay;
    }

    private CompletableFuture<CompiledReplay> compileTextures(CompiledReplay replay) {
        LOGGER.info("Extracting textures...");
        setPhase(ExportPhase.MATERIALS);
        return replayCapture.getAllTextures().thenApply(map -> {
            replay.getTextures().putAll(map);
            return replay;
        });
    }

    private CompiledReplay packMtls(CompiledReplay replay) {
        for (var mtlLibEntry : replayCapture.getMaterialHolder().getMtlLibs().entrySet()) {
            replay.getMtlLibs().put(mtlLibEntry.getKey(), List.copyOf(mtlLibEntry.getValue().values()));
        }
        return replay;
    }

    private CompiledReplay writeMeta(CompiledReplay replay) {
        replay.getMeta().setOrigin(replayCapture.getSettings().getOffset().multiply(-1));
        return replay;
    }

    public static String sanitizeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public static String getUniqueName(String baseName, Collection<? extends String> existing) {
        String name = baseName;
        int conflictIndex = 1;
        while (existing.contains(name)) {
            name = baseName + "." + String.format("%03d", conflictIndex);
            conflictIndex++;
        }
        return name;
    }
}
