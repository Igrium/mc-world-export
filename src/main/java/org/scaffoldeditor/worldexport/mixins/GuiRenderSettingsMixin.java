package org.scaffoldeditor.worldexport.mixins;

import com.replaymod.core.ReplayMod;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.RenderSettings.RenderMethod;
import com.replaymod.render.gui.GuiRenderSettings;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.replaymod.RenderSettingsUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.crash.CrashReport;

import static com.replaymod.core.utils.Utils.error;


@Mixin(value = GuiRenderSettings.class, remap = false)
public class GuiRenderSettingsMixin {
    @Shadow
    private ReplayHandler replayHandler;

    @Shadow
    private Timeline timeline;

    public GuiButton exportButton;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void initMixin(AbstractGuiScreen<?> container, ReplayHandler replayHandler, Timeline timeline, CallbackInfo ci) {
        exportButton = new GuiButton(((GuiRenderSettings)(Object) this).buttonPanel).onClick(() -> ReplayMod.instance.runLaterWithoutLock(() -> {
            ((GuiRenderSettings)(Object) this).close();
            try {
                RenderSettings settings = ((GuiRenderSettings)(Object) this).save(false);
                settings = RenderSettingsUtils.withRenderMethod(settings, RenderMethod.BLEND);
                VideoRenderer videoRenderer = new VideoRenderer(settings, replayHandler, timeline);
                videoRenderer.renderVideo();
            } catch (Throwable e) {
                error(LogManager.getLogger(), (GuiRenderSettings)(Object) this, CrashReport.create(e, "Exporting replay"), () -> {});
                ((GuiRenderSettings)(Object) this).getScreen().display();
            }
        })).setSize(100, 20).setLabel("Export replay file");
    }

}
