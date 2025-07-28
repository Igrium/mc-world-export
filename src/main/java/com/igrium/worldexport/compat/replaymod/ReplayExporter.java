package com.igrium.worldexport.compat.replaymod;

import com.google.common.collect.Iterables;
import com.igrium.worldexport.compat.replaymod.util.ExportPhase;
import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.replay.ReplaySettings;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.pathing.player.AbstractTimelinePlayer;
import com.replaymod.pathing.player.ReplayTimer;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.ReplayModRender;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.gui.progress.VirtualWindow;
import com.replaymod.render.hooks.ForceChunkLoadingHook;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.Timeline;
import lombok.Getter;
import lombok.Setter;
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
import net.minecraft.util.crash.CrashException;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * An adaption of {@link VideoRenderer} designed for exporting replays.
 */
public class ReplayExporter implements RenderInfo {
    private static final Identifier SOUND_RENDER_SUCCESS = Identifier.of("replaymod", "render_success");
    private final MinecraftClient client = MinecraftClient.getInstance();

    private static final int FPS = 20;

    @Getter
    private final ReplaySettings settings;
    private final ReplayHandler replayHandler;
    private final Timeline timeline;

    @Getter
    private final CapturePipeline pipeline;

    private TimelinePlayer timelinePlayer;
    private Future<Void> timelinePlayerFuture;
    private ForceChunkLoadingHook forceChunkLoadingHook;

    private Map<SoundCategory, Float> originalSoundLevels;

    private int framesDone;
    private int totalFrames;

    private final VirtualWindow guiWindow = new VirtualWindow(client);
    private final ExportInfo.Mutable exportInfo = new ExportInfo.Mutable();
    private final GuiReplayExporter gui;

    @Getter @Setter
    private boolean paused;
    private boolean cancelled;

    private volatile Throwable failureCause;

    public ReplayExporter(ReplaySettings settings, ReplayHandler replayHandler, Timeline timeline) {
        this.settings = settings;
        this.replayHandler = replayHandler;
        this.timeline = timeline;

        this.gui = new GuiReplayExporter(exportInfo);
        this.pipeline = new CapturePipeline(new ReplayCapture(null, settings));
    }

    /**
     * Export this replay.
     * @return <code>true</code> if export was successful; <code>false</code> if the user aborted export.
     * @throws Throwable If the export fails.
     */
    public boolean exportReplay() throws Throwable {
        setup();

        ReplayTimer timer = (ReplayTimer)((MinecraftAccessor)this.client).getTimer();

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

    public boolean hasFailed() {
        return this.failureCause != null;
    }

    public synchronized void setFailure(@Nullable Throwable cause) {
        if (this.failureCause != null) {
            ReplayModRender.LOGGER.error("Further failure during failed rendering: ", cause);
        } else {
            ReplayModRender.LOGGER.error("Failure during rendering: ", cause);
            this.failureCause = cause;
            this.cancel();
        }

    }

    @Override
    public float updateForNextFrame() {
        ReplayTimer timer;
        try {
            // because the jGui lib uses Minecraft's displayWidth and displayHeight values, update these temporarily
            guiWindow.bind();

            while (drawGui() && paused) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            timer = (ReplayTimer)((MinecraftAccessor)this.client).getTimer();
            int elapsedTicks = timer.beginRenderTick(com.replaymod.core.versions.MCVer.milliTime(), true);

            executeTaskQueue();

            while (elapsedTicks-- > 0) {
                client.tick();
            }

        } finally {
            guiWindow.unbind();
        }
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
            if (!keyframes.isEmpty()) {
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
                drawGui();
                ((MCVer.MinecraftMethodAccessor) client).replayModExecuteTaskQueue();
            }

            CompletableFuture<Void> resourceReloadFuture = ((MinecraftAccessor) client).getResourceReloadFuture();
            if (resourceReloadFuture != null) {
                ((MinecraftAccessor) client).setResourceReloadFuture(null);
                client.reloadResources().thenRun(() -> resourceReloadFuture.complete(null));
                continue;
            }
            break;
        }

        ((MCVer.MinecraftMethodAccessor) client).replayModExecuteTaskQueue();
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
        return new RenderSettings(); // Not used for replay export
    }

    @Override
    public int getTotalFrames() {
        return totalFrames;
    }

    /**
     * Get the current time on the replay timeline.
     * @return The current time in milliseconds.
     */
    public int getReplayTime() { return framesDone * 1000 / FPS; }

    public boolean drawGui() {
        Window window = this.client.getWindow();
        if (!GLFW.glfwWindowShouldClose(window.getHandle()) && ((MinecraftAccessor)this.client).getCrashReporter() == null) {
            com.replaymod.core.versions.MCVer.pushMatrix();
            RenderSystem.clear(16640);
            this.guiWindow.beginWrite();
            RenderSystem.clear(256);
            RenderSystem.setProjectionMatrix(com.replaymod.core.versions.MCVer.ortho(0.0F, (float)((double)window.getFramebufferWidth() / window.getScaleFactor()), 0.0F, (float)((double)window.getFramebufferHeight() / window.getScaleFactor()), 1000.0F, 3000.0F), ProjectionType.ORTHOGRAPHIC);
            Matrix4fStack matrixStack = RenderSystem.getModelViewStack();
            matrixStack.translation(0.0F, 0.0F, -2000.0F);
            DiffuseLighting.enableGuiDepthLighting();
            this.gui.toMinecraft().init(this.client, window.getScaledWidth(), window.getScaledHeight());
            int mouseX = (int)this.client.mouse.getX() * window.getScaledWidth() / Math.max(window.getWidth(), 1);
            int mouseY = (int)this.client.mouse.getY() * window.getScaledHeight() / Math.max(window.getHeight(), 1);
            DrawContext drawContext = new DrawContext(this.client, this.client.getBufferBuilders().getEntityVertexConsumers());
            if (this.client.getOverlay() != null) {
                Screen orgScreen = this.client.currentScreen;

                try {
                    this.client.currentScreen = this.gui.toMinecraft();
                    this.client.getOverlay().render(drawContext, mouseX, mouseY, 0.0F);
                } finally {
                    this.client.currentScreen = orgScreen;
                }
            } else {
                this.gui.toMinecraft().tick();
                this.gui.toMinecraft().render(new DrawContext(this.client, this.client.getBufferBuilders().getEntityVertexConsumers()), mouseX, mouseY, 0.0F);
            }

            drawContext.draw();
            this.guiWindow.endWrite();
            com.replaymod.core.versions.MCVer.popMatrix();
            com.replaymod.core.versions.MCVer.pushMatrix();
            this.guiWindow.flip();
            com.replaymod.core.versions.MCVer.popMatrix();
            if (this.client.mouse.isCursorLocked()) {
                this.client.mouse.unlockCursor();
            }

            return !this.hasFailed() && !this.cancelled;
        } else {
            return false;
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
}
