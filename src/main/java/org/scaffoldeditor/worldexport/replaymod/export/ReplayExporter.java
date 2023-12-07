package org.scaffoldeditor.worldexport.replaymod.export;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.scaffoldeditor.worldexport.gui.GuiReplayExporter;
import org.scaffoldeditor.worldexport.replaymod.ReplayFrameCapturer;
import org.scaffoldeditor.worldexport.replaymod.util.ExportInfo;
import org.scaffoldeditor.worldexport.replaymod.util.ExportPhase;

import com.google.common.collect.Iterables;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.versions.MCVer;
import com.replaymod.core.versions.MCVer.MinecraftMethodAccessor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.pathing.player.AbstractTimelinePlayer;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.gui.progress.VirtualWindow;
import com.replaymod.render.hooks.ForceChunkLoadingHook;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.Timeline;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;

/**
 * An adaption of {@link VideoRenderer} designed for exporting replays.
 */
public class ReplayExporter implements RenderInfo {

    private static final Identifier SOUND_RENDER_SUCCESS = new Identifier("replaymod", "render_success");
    private final MinecraftClient client = MinecraftClient.getInstance();

    private static final int FPS = 20;

    private final ReplayExportSettings settings;
    private final ReplayHandler replayHandler;
    private final Timeline timeline;

    private CapturePipeline pipeline;

    private TimelinePlayer timelinePlayer;
    private Future<Void> timelinePlayerFuture;
    private ForceChunkLoadingHook forceChunkLoadingHook;

    private boolean mouseWasGrabbed;
    private Map<SoundCategory, Float> originalSoundLevels;

    private int framesDone;
    private int totalFrames;

    private final VirtualWindow guiWindow = new VirtualWindow(client);
    private final ExportInfo.Mutable exportInfo = new ExportInfo.Mutable();
    private final GuiReplayExporter gui;
    private boolean paused;
    private boolean cancelled;
    private volatile Throwable failureCause;
    

    public ReplayExporter(ReplayExportSettings settings, ReplayHandler replayHandler, Timeline timeline) {
        this.settings = settings;
        this.replayHandler = replayHandler;
        this.timeline = timeline;
        this.gui = new GuiReplayExporter(exportInfo);
        this.pipeline = new CapturePipeline(new ReplayFrameCapturer(this, FPS, settings), this);
    }

    /**
     * Export this replay.
     * @return <code>true</code> if export was successful; <code>false</code> if the user aborted export.
     * @throws Throwable If the export fails.
     */
    public boolean exportReplay() throws Throwable {
        setup();

        RenderTickCounter timer = ((MinecraftAccessor) client).getTimer();

        // Play up to one second before starting to export
        // This is necessary in order to ensure that all entities have at least two position packets
        // and their first position in the recording is correct.
        // Note that it is impossible to also get the interpolation between their latest position
        // and the one in the recording correct as there's no reliable way to tell when the server ticks
        // or when we should be done with the interpolation of the entity
        Optional<Integer> optionalVideoStartTime = timeline.getValue(TimestampProperty.PROPERTY, 0);
        int videoStart;
        if (optionalVideoStartTime.isPresent() && (videoStart = optionalVideoStartTime.get()) > 0) {
            int delta = Math.min(videoStart, 1000);
            int replayTime = videoStart - delta;
            timer.tickDelta = 0;

            while (replayTime < videoStart) {
                replayTime += 50;
                replayHandler.getReplaySender().sendPacketsTill(replayTime);
                client.tick();
            }
        } else {
            // Rewind to the beginning *before* replay exporter init.
            replayHandler.getReplaySender().sendPacketsTill(0);
            client.tick();
        }

        // Apply the timeline so that the export bounds are centered correctly.
        timeline.applyToGame(0, replayHandler);

        exportInfo.setPhase(ExportPhase.CAPTURE);
        pipeline.run(exportInfo);

        if (((MinecraftAccessor) client).getCrashReporter() != null) {
            throw new CrashException(((MinecraftAccessor) client).getCrashReporter().get());
        }

        finish();

        if (failureCause != null) throw failureCause;
        return !cancelled;
    }

    public void cancel() {
        this.cancelled = true;
        pipeline.cancel();
    }

    @Override
    public float updateForNextFrame() {
        // because the jGui lib uses Minecraft's displayWidth and displayHeight values, update these temporarily
        guiWindow.bind();

        while (drawGuiInternal() && paused) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        RenderTickCounter timer = ((MinecraftAccessor) client).getTimer();
        int elapsedTicks = timer.beginRenderTick(Util.getMeasuringTimeMs());

        executeTaskQueue();
        
        while (elapsedTicks-- > 0) {
            client.tick();
        }

        guiWindow.unbind();

        framesDone++;
        return timer.tickDelta;
    }

    private void setup() {
        timelinePlayer = new TimelinePlayer(replayHandler);
        timelinePlayerFuture = timelinePlayer.start(timeline);

        client.mouse.unlockCursor();

        // Mute all sounds except UI.
        originalSoundLevels = new EnumMap<>(SoundCategory.class);
        for (SoundCategory category : SoundCategory.values()) {
            if (category != SoundCategory.MASTER) {
                originalSoundLevels.put(category, client.options.getSoundVolume(category));
                client.options.getSoundVolumeOption(category).setValue(0d);
            }
        }

        // Calculate duration
        long duration = 0;
        for (Path path : timeline.getPaths()) {
            if (!path.isActive()) continue;

            path.updateAll();
            Collection<Keyframe> keyframes = path.getKeyframes();
            if (keyframes.size() > 0) {
                duration = Math.max(duration, Iterables.getLast(keyframes).getTime());
            }
        }

        totalFrames = (int) (duration * FPS / 1000);

        gui.toMinecraft().init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());

        // TODO: Do we need this if we're only exporting?
        forceChunkLoadingHook = new ForceChunkLoadingHook(client.worldRenderer);
    }
    
    private void finish() {
        if (!timelinePlayerFuture.isDone()) {
            timelinePlayerFuture.cancel(false);
        }
        // Tear down of the timeline player might only happen the next tick after it was cancelled
        timelinePlayer.onTick();

        guiWindow.close();


        if (mouseWasGrabbed) {
            client.mouse.lockCursor();
        }

        // originalSoundLevels.forEach((cat, val) -> client.options.setSoundVolume(cat, val));
        originalSoundLevels.forEach((cat, val) -> client.options.getSoundVolumeOption(cat).setValue((double) val));
        client.setScreen(null);
        forceChunkLoadingHook.uninstall();

        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(SOUND_RENDER_SUCCESS), 1));
        
        MCVer.resizeMainWindow(client, guiWindow.getFramebufferWidth(), guiWindow.getFramebufferHeight());
    }

    private void executeTaskQueue() {
        while (true) {
            while (client.getOverlay() != null) {
                drawGuiInternal();
                ((MinecraftMethodAccessor) client).replayModExecuteTaskQueue();
            }

            CompletableFuture<Void> resourceReloadFuture = ((MinecraftAccessor) client).getResourceReloadFuture();
            if (resourceReloadFuture != null) {
                ((MinecraftAccessor) client).setResourceReloadFuture(null);
                client.reloadResources().thenRun(() -> resourceReloadFuture.complete(null));
                continue;
            }
            break;
        }

        ((MinecraftMethodAccessor) client).replayModExecuteTaskQueue();
        client.currentScreen = gui.toMinecraft();
    }


    @Override
    public ReadableDimension getFrameSize() {
        return new Dimension();
    }

    @Override
    public int getFramesDone() {
        return framesDone;
    }

    @Override
    public RenderSettings getRenderSettings() {
        return new RenderSettings();
    }

    @Override
    public int getTotalFrames() {
        return totalFrames;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public ReplayFrameCapturer getCapturer() {
        return pipeline.getFrameCapture();
    }

    public CapturePipeline getPipeline() {
        return pipeline;
    }

    public ReplayExportSettings getSettings() {
        return settings;
    }

    /**
     * Get the current time on the replay timeline.
     * @return The current time in milliseconds.
     */
    public int getReplayTime() { return framesDone * 1000 / FPS; }

    public boolean drawGui() {
        return drawGuiInternal();
        // try {
        //     // because the jGui lib uses Minecraft's displayWidth and displayHeight values, update these temporarily
        //     guiWindow.bind();
        //     return drawGuiInternal();
        // } finally {
        //     guiWindow.unbind();
        // }
    }

    protected boolean drawGuiInternal() {
        Window window = client.getWindow();
        do {
            if (GLFW.glfwWindowShouldClose(window.getHandle()) || ((MinecraftAccessor) client).getCrashReporter() != null) {
                return false;
            }

            MCVer.pushMatrix();
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, false);
            guiWindow.beginWrite();

            RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
            RenderSystem.setProjectionMatrix(
                    com.replaymod.core.versions.MCVer.ortho(0,
                            (float) (window.getFramebufferWidth() / window.getScaleFactor()), 0,
                            (float) (window.getFramebufferHeight() / window.getScaleFactor()), 1000, 3000),
                    VertexSorter.BY_Z            );
            
            MatrixStack matrixStack = RenderSystem.getModelViewStack();
            matrixStack.loadIdentity();
            matrixStack.translate(0, 0, -2000);
            RenderSystem.applyModelViewMatrix();
            DiffuseLighting.enableGuiDepthLighting();

            gui.toMinecraft().init(client, window.getScaledWidth(), window.getScaledHeight());

            int mouseX = (int) client.mouse.getX() * window.getScaledWidth() / Math.max(window.getWidth(), 1);
            int mouseY = (int) client.mouse.getY() * window.getScaledHeight() / Math.max(window.getHeight(), 1);

            if (client.getOverlay() != null) {
                Screen orgScreen = client.currentScreen;
                try {
                    client.currentScreen = gui.toMinecraft();
                    client.getOverlay().render(
                            new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers()), 
                            mouseX, mouseY, 0);
                } finally {
                    client.currentScreen = orgScreen;
                }

            } else {
                gui.toMinecraft().tick();
                gui.toMinecraft().render(
                        new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers()),
                        mouseX, mouseY, 0);
            }

            guiWindow.endWrite();
            MCVer.popMatrix();
            MCVer.pushMatrix();
            guiWindow.flip();
            MCVer.popMatrix();

            if (client.mouse.isCursorLocked())
                client.mouse.unlockCursor();

            return failureCause == null && !cancelled;
        } while (true);
        
    }

    private class TimelinePlayer extends AbstractTimelinePlayer {

        public TimelinePlayer(ReplayHandler replayHandler) {
            super(replayHandler);
        }

        @Override
        public long getTimePassed() {
            return getReplayTime();
        }
    }
}
