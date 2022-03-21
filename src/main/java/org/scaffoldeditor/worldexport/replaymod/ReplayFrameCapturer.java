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

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.rendering.Channel;
import com.replaymod.render.rendering.FrameCapturer;
import com.replaymod.render.utils.ByteBufferPool;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.ClientBlockPlaceCallback;
import org.scaffoldeditor.worldexport.ReplayExportMod;
import org.scaffoldeditor.worldexport.replay.ReplayEntity;
import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModelAdapter.ModelNotFoundException;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class ReplayFrameCapturer implements FrameCapturer<BitmapFrame> {

    protected int framesDone;
    protected RenderInfo renderInfo;
    protected ReplayFile exporter;

    protected Map<BlockPos, BlockState> blockUpdateCache = new HashMap<>();
    protected Map<Entity, ReplayEntity<?>> entityCache = new HashMap<>();
    protected Set<Entity> skippedEnts = new HashSet<>();

    private float tickDelta = 0;
    private MinecraftClient client = MinecraftClient.getInstance();

    public ReplayFrameCapturer(RenderInfo renderInfo) {
        this.renderInfo = renderInfo;
    }

    protected ClientBlockPlaceCallback blockUpdateListener = new ClientBlockPlaceCallback() {

        @Override
        public void place(BlockPos pos, BlockState state, World world) {
            blockUpdateCache.put(pos, state);
        }
        
    };

    public ReplayFile getExporter() {
        return exporter;
    }

    public RenderInfo getRenderInfo() {
        return renderInfo;
    }
    

    protected void setup() {
        if (exporter == null) {
            int viewDistance = client.options.viewDistance;
            ChunkPos centerPos = client.getCameraEntity().getChunkPos();
            exporter = new ReplayFile(client.world, 
                    new ChunkPos(centerPos.x - viewDistance, centerPos.z - viewDistance),
                    new ChunkPos(centerPos.x + viewDistance, centerPos.z + viewDistance));
        }
        
        exporter.setFps(renderInfo.getRenderSettings().getFramesPerSecond());
        exporter.getWorldExporter().getSettings().exportFluids(false).setLowerDepth(0);
        LogManager.getLogger().info("Capturing initial world");
        exporter.getWorldExporter().captureIFrame(0);
        ReplayExportMod.getInstance().onBlockUpdated(blockUpdateListener);
    }

    @Override
    public boolean isDone() {
        return framesDone >= renderInfo.getTotalFrames();
    }

    @Override
    public Map<Channel, BitmapFrame> process() {
        tickDelta = renderInfo.updateForNextFrame();
        if (framesDone == 0) {
            setup();
        }

        double time = framesDone / (double) renderInfo.getRenderSettings().getFramesPerSecond();
        if (blockUpdateCache.size() > 0) {
            exporter.getWorldExporter().capturePFrame(time, blockUpdateCache.keySet(), client.world);
            blockUpdateCache.clear();
        }

        client.world.getEntities().forEach(this::captureEntity);

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
        
        FileOutputStream out = new FileOutputStream(target);
        exporter.save(out);
        out.close();
    }

    /**
     * Close this capturer without saving it to file.
     */
    public void cleanUp() {
        ReplayExportMod.getInstance().removeOnBlockUpdated(blockUpdateListener);
    }
    
}
