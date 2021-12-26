package org.scaffoldeditor.worldexport.mixins;

import com.replaymod.core.ReplayMod;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.render.gui.GuiRenderSettings;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.replaymod.Pipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.util.crash.CrashReport;

import static com.replaymod.core.utils.Utils.error;


@Mixin(value = GuiRenderSettings.class, remap = false)
public class GuiRenderSettingsMixin {
    @Shadow
    private ReplayHandler replayHandler;

    @Shadow
    private Timeline timeline;

    public final GuiButton exportButton = new GuiButton(((GuiRenderSettings)(Object) this).buttonPanel).onClick(() -> ReplayMod.instance.runLaterWithoutLock(() -> {
        ((GuiRenderSettings)(Object) this).close();
        try {
            VideoRenderer videoRenderer = new VideoRenderer(((GuiRenderSettings)(Object) this).save(false), replayHandler, timeline);
            ((VideoRendererAccessor) videoRenderer).setRenderingPipeline(Pipelines.newReplayPipeline(videoRenderer));
            videoRenderer.renderVideo();
        } catch (Throwable e) {
            error(LogManager.getLogger(), (GuiRenderSettings)(Object) this, CrashReport.create(e, "Exporting replay"), () -> {});
            ((GuiRenderSettings)(Object) this).getScreen().display();
        }
    })).setSize(100, 20).setLabel("Export replay file");


}
