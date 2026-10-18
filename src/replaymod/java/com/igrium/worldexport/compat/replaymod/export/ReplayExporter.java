package com.igrium.worldexport.compat.replaymod.export;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.igrium.worldexport.compat.replaymod.ExportInfo;
import com.igrium.worldexport.compat.replaymod.gui.GuiReplayExporter;
import com.igrium.worldexport.replay.ExportPhase;
import com.igrium.worldexport.replay.ReplayExportSettings;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.core.mixin.BlockableEventLoopAccessor;
import com.replaymod.core.mixin.GuiAccessor;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.mixin.TimerAccessor;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.pathing.player.AbstractTimelinePlayer;
import com.replaymod.pathing.player.ReplayTimer;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.hooks.ForceChunkLoadingHook;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.Timeline;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class ReplayExporter implements RenderInfo {
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/ReplayExporter");

    private static final Identifier SOUND_RENDER_SUCCESS = Identifier.parse("replaymod:render_success");

    private static final int FPS = 20;

    private final Minecraft mc = Minecraft.getInstance();

    @Getter
    private final ReplayExportSettings settings;
    private final ReplayHandler replayHandler;
    private final Timeline timeline;

    @Getter
    private final CapturePipeline pipeline;

    private boolean mouseWasGrabbed;
    private boolean debugInfoWasShown;
    private Map<SoundSource, Float> originalSoundLevels;

    private TimelinePlayer timelinePlayer;
    private ListenableFuture<Void> timelinePLayerFuture;
    private ForceChunkLoadingHook forceChunkLoadingHook;

    private int framesDone;
    private int totalFrames;

    @Getter
    private final ExportInfo.Mutable exportInfo = new Info();
    private final GuiReplayExporter gui;

    @Getter @Setter @NonNull
    private String phase = ExportPhase.INIT;

    @Getter @Setter
    private boolean paused;
    @Getter
    private boolean cancelled;
    private volatile Throwable failureCause;

    public ReplayExporter(ReplayExportSettings settings, ReplayHandler replayHandler, Timeline timeline) {
        this.settings = settings;
        this.replayHandler = replayHandler;
        this.timeline = timeline;
        this.gui = new GuiReplayExporter(exportInfo);
        this.pipeline = new CapturePipeline(this);
    }

    public boolean exportReplay() throws Throwable {
        setup();
        drawGui();

        ReplayTimer timer = (ReplayTimer) ((MinecraftAccessor) mc).getTimer();

        Optional<Integer> optionalVideoStartTime = timeline.getValue(TimestampProperty.PROPERTY, 0);
        int videoStart;
        if (optionalVideoStartTime.isPresent() && (videoStart = optionalVideoStartTime.get()) > 0) {
            int delta = Math.min(videoStart, 1000);
            int replayTime = videoStart - delta;
            timer.tickDelta = 0;
            ((TimerAccessor) timer).setTickLength(Utils.DEFAULT_MS_PER_TICK);

            while (replayTime < videoStart) {
                replayTime += 50;
                replayHandler.getReplaySender().sendPacketsTill(replayTime);
                tick();
            }
        } else {
            replayHandler.getReplaySender().sendPacketsTill(0);
            tick();
        }

        // We need to snapshot the world BEFORE the frame loop starts
        timeline.applyToGame(0, replayHandler);

        setPhase(ExportPhase.CAPTURE);
        pipeline.run(exportInfo);

        Supplier<CrashReport> crashReport = ((BlockableEventLoopAccessor) mc).getDelayedCrash();
        if (crashReport != null) {
            throw new ReportedException(crashReport.get());
        }

        finish();

        if (failureCause != null) {
            throw failureCause;
        }

        return !cancelled;
    }

    @Override
    public float updateForNextFrame() {
        while (drawGui() && paused) {
            try {
                //noinspection BusyWait
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        ReplayTimer timer = (ReplayTimer) ((MinecraftAccessor) mc).getTimer();
        int elapsedTicks = timer.advanceGameTime(MCVer.milliTime());

        executeTaskQueue();

        while (elapsedTicks-- > 0) {
            tick();
        }

        if (mc.level != null) {
            mc.level.update();
        }

        framesDone++;
        return timer.tickDelta;
    }

    private void setup() {
        timelinePlayer = new TimelinePlayer(replayHandler);
        timelinePLayerFuture = timelinePlayer.start(timeline);

        if (mc.debugEntries.isOverlayVisible()) {
            mc.debugEntries.setOverlayVisible(false);
            debugInfoWasShown = true;
        }

        if (mc.mouseHandler.isMouseGrabbed()) {
            mouseWasGrabbed = true;
        }
        mc.mouseHandler.releaseMouse();

        originalSoundLevels = new EnumMap<>(SoundSource.class);
        for (var cat : SoundSource.values()) {
            if (cat != SoundSource.MASTER) {
                originalSoundLevels.put(cat, mc.options.getSoundSourceVolume(cat));
                mc.options.getSoundSourceOptionInstance(cat).set(0d);
            }
        }

        long duration = 0;
        for (Path path : timeline.getPaths()) {
            if (!path.isActive()) continue;

            path.updateAll();
            var keys = path.getKeyframes();
            if (!keys.isEmpty()) {
                duration = Math.max(duration, Iterables.getLast(keys).getTime());
            }
        }

        totalFrames = (int) ((duration * FPS) / 1000);

        gui.toMinecraft().init(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        forceChunkLoadingHook = new ForceChunkLoadingHook(mc.levelRenderer);
    }

    private void finish() {
        if (!timelinePLayerFuture.isDone()) {
            timelinePLayerFuture.cancel(true);
        }
        timelinePlayer.onTick();

        if (debugInfoWasShown) {
            mc.debugEntries.setOverlayVisible(true);
        }

        if (mouseWasGrabbed) {
            mc.mouseHandler.grabMouse();
        }

        for (Map.Entry<SoundSource, Float> entry : originalSoundLevels.entrySet()) {
            mc.options.getSoundSourceOptionInstance(entry.getKey()).set((double) entry.getValue());
        }

        // TODO: show finished screen
        //noinspection DataFlowIssue
        mc.setScreenAndShow(null);
        forceChunkLoadingHook.uninstall();

        mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(SOUND_RENDER_SUCCESS), 1));
    }

    private void executeTaskQueue() {
        while(true) {
            if (this.mc.gui.overlay() != null) {
                this.drawGui();
                ((MCVer.MinecraftMethodAccessor)this.mc).replayModExecuteTaskQueue();
                Overlay overlay = this.mc.gui.overlay();
                if (overlay != null) {
                    overlay.tick();
                }
            } else {
                CompletableFuture<Void> resourceReloadFuture = ((MinecraftAccessor)this.mc).getResourceReloadFuture();
                if (resourceReloadFuture == null) {
                    ((MCVer.MinecraftMethodAccessor)this.mc).replayModExecuteTaskQueue();
                    ((GuiAccessor)this.mc.gui).replaymod$setScreen(this.gui.toMinecraft());
                    return;
                }

                ((MinecraftAccessor)this.mc).setResourceReloadFuture(null);
                this.mc.reloadResourcePacks().thenRun(() -> resourceReloadFuture.complete(null));
            }
        }
    }

    public void runTaskQueue() {
        ((MCVer.MinecraftMethodAccessor) this.mc).replayModExecuteTaskQueue();
    }

    private void tick() {
        mc.getTextureManager().tick();
        mc.tick();
    }

    /**
     * Draw the progress GUI as a full frame. Mirrors {@code VideoRenderer#drawGui} on 26.3: the level is hidden so
     * {@link Minecraft#renderFrame} only draws the screen (and any overlay) to the window.
     */
    public boolean drawGui() {
        Window window = this.mc.getWindow();
        if (window.shouldClose() || ((BlockableEventLoopAccessor) this.mc).getDelayedCrash() != null) {
            return false;
        }
        RenderSystem.pollEvents(((MinecraftAccessor) this.mc).getSdlEventHandler());

        Screen orgScreen = this.mc.gui.screen();
        ClientLevel orgLevel = this.mc.level;
        try {
            ((GuiAccessor) this.mc.gui).replaymod$setScreen(this.gui.toMinecraft());
            this.mc.level = null;

            this.gui.toMinecraft().init(window.getGuiScaledWidth(), window.getGuiScaledHeight());
            this.gui.toMinecraft().tick();

            this.mc.renderFrame(false);
        } finally {
            ((GuiAccessor) this.mc.gui).replaymod$setScreen(orgScreen);
            this.mc.level = orgLevel;
        }

        if (this.mc.mouseHandler.isMouseGrabbed()) {
            this.mc.mouseHandler.releaseMouse();
        }
        return !this.hasFailed() && !this.cancelled;
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
    public int getTotalFrames() {
        return totalFrames;
    }

    @Override
    public RenderSettings getRenderSettings() {
        return new RenderSettings();
    }

    public int getReplayTime() {
        return framesDone * 1000 / FPS;
    }

    public void cancel() {
        this.cancelled = true;
        pipeline.cancel();
    }

    public boolean hasFailed() {
        return failureCause != null;
    }

    public synchronized void setFailure(Throwable cause) {
        if (this.failureCause != null) {
            LOGGER.error("Further failure during failed export: ", cause);
        } else {
            LOGGER.error("Failure during export: ", cause);
            this.failureCause = cause;
            cancel();
        }
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

    /**
     * Basically a complete mess that lets relevant parts of the code access relevant data and sometimes update
     */
    private class Info implements ExportInfo.Mutable {

        @Override
        public void setFramesDone(int framesDone) {
            ReplayExporter.this.framesDone = framesDone;
        }

        @Override
        public void setTotalFrames(int totalFrames) {
            ReplayExporter.this.totalFrames = totalFrames;
        }

        @Override
        public void setPhase(String phase) {
            ReplayExporter.this.setPhase(phase);
        }

        @Override
        public int getFramesDone() {
            return framesDone;
        }

        @Override
        public int getTotalFrames() {
            return totalFrames;
        }

        @Override
        public int getSectionsDone() {
            var cap = pipeline.getReplayCapture();
            return cap != null && cap.getWorldCapture() != null ? cap.getWorldCapture().getMesher().getFinishedSections() : 0;
        }

        @Override
        public int getTotalSections() {
            var cap = pipeline.getReplayCapture();
            return cap != null && cap.getWorldCapture() != null ? cap.getWorldCapture().getMesher().getTotalSections() : 0;
        }

        @Override
        public String getPhase() {
            return phase;
        }
    }
}
