package org.scaffoldeditor.worldexport.replaymod;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.rendering.Channel;
import com.replaymod.render.rendering.FrameCapturer;
import com.replaymod.render.utils.ByteBufferPool;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.ClientBlockPlaceCallback;
import org.scaffoldeditor.worldexport.WorldExportMod;
import org.scaffoldeditor.worldexport.replay.ReplayEntity;
import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelAdapter.ModelNotFoundException;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ReplayFrameCapturer implements FrameCapturer<BitmapFrame> {

    protected int framesDone;
    protected RenderInfo renderInfo;
    protected ReplayFile exporter;
    protected ClientWorld world;

    protected Map<BlockPos, BlockState> blockUpdateCache = new HashMap<>();
    protected Map<Entity, ReplayEntity<?>> entityCache = new HashMap<>();
    protected Set<Entity> skippedEnts = new HashSet<>();

    private float tickDelta = 0;
    private MinecraftClient client = MinecraftClient.getInstance();

    public ReplayFrameCapturer(RenderInfo renderInfo, ClientWorld world) {
        this.renderInfo = renderInfo;
        this.world = world;
    }

    protected ClientBlockPlaceCallback blockUpdateListener = new ClientBlockPlaceCallback() {

        @Override
        public void place(BlockPos pos, BlockState state, World world) {
            if (world == getWorld()) {
                blockUpdateCache.put(pos, state);
            } else {
                LogManager.getLogger().warn("Block update detected at "+pos+" in the wrong world.");
            }
        }
        
    };

    public ReplayFile getExporter() {
        return exporter;
    }

    public ClientWorld getWorld() {
        return world;
    }

    public RenderInfo getRenderInfo() {
        return renderInfo;
    }
    

    protected void setup() {
        exporter.setFps(renderInfo.getRenderSettings().getFramesPerSecond());
        exporter.getWorldExporter().captureIFrame(0);
        WorldExportMod.getInstance().onBlockUpdated(blockUpdateListener);
    }

    @Override
    public boolean isDone() {
        return framesDone >= renderInfo.getTotalFrames();
    }

    @Override
    public Map<Channel, BitmapFrame> process() {
        if (framesDone == 0) {
            setup();
        }
        tickDelta = client.getTickDelta();

        double time = framesDone / (double) renderInfo.getRenderSettings().getFramesPerSecond();
        if (blockUpdateCache.size() > 0) {
            exporter.getWorldExporter().capturePFrame(time, blockUpdateCache.keySet());
            blockUpdateCache.clear();
        }

        world.getEntities().forEach(this::captureEntity);

        // Bogus frame to satisfy encoder.
        BitmapFrame frame = new BitmapFrame(framesDone++, new Dimension(0, 0), 0, ByteBufferPool.allocate(0));
        return Collections.singletonMap(Channel.BRGA, frame);
    }

    protected void captureEntity(Entity ent) {
        if (skippedEnts.contains(ent)) {
            return;
        }

        ReplayEntity<?> rEnt = entityCache.get(ent);
        if (rEnt == null) {
            rEnt = new ReplayEntity<>(ent, exporter);
            try {
                rEnt.genAdapter();
            } catch (ModelNotFoundException e) {
                LogManager.getLogger().error("Entity " + ent.getEntityName() + " (" + EntityType.getId(ent.getType())
                        + ") does not have a model adapter. Will be missing from export!");
                skippedEnts.add(ent);
                return;
            }
            
            exporter.entities.add(rEnt);
            entityCache.put(ent, rEnt);
        }

        rEnt.capture(tickDelta);
    }

    /**
     * Close and save the replay out to file.
     */
    @Override
    public void close() throws IOException {
        cleanUp();

        File output = renderInfo.getRenderSettings().getOutputFile();
        Path folder = output.getParentFile().toPath();
        File target = folder.resolve(FilenameUtils.getBaseName(output.getName())+".replay").normalize().toFile();

        LogManager.getLogger().info("Saving replay file to "+target);
        CompletableFuture.runAsync(() -> {
            try {
                FileOutputStream out = new FileOutputStream(target);
                exporter.save(out);
                out.close();
            } catch (IOException e) {
                LogManager.getLogger("ReplayFrameCapturer").error("Error saving replay file.", e);
            }
        });
    }

    /**
     * Close this capturer without saving it to file.
     */
    public void cleanUp() {
        WorldExportMod.getInstance().removeOnBlockUpdated(blockUpdateListener);
    }
    
}
