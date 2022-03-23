package org.scaffoldeditor.worldexport.mixins;

import com.replaymod.core.ReplayMod;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.RenderSettings.RenderMethod;
import com.replaymod.render.gui.GuiRenderSettings;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.replaymod.RenderSettingsUtils;
import org.scaffoldeditor.worldexport.replaymod.gui.GuiExportSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.crash.CrashReport;

import static com.replaymod.core.utils.Utils.error;


@Mixin(value = GuiRenderSettings.class, remap = false)
public abstract class GuiRenderSettingsMixin {
    @Shadow
    private ReplayHandler replayHandler;

    @Shadow
    private Timeline timeline;

    public GuiButton exportButton;

    @Shadow
    public abstract void close();
    
    @Shadow
    public abstract AbstractGuiScreen<?> getScreen();

    @Inject(method = "<init>", at = @At("RETURN"))
    public void initMixin(AbstractGuiScreen<?> container, ReplayHandler replayHandler, Timeline timeline, CallbackInfo ci) {
        exportButton = new GuiButton(((GuiRenderSettings)(Object) this).buttonPanel).onClick(() -> ReplayMod.instance.runLaterWithoutLock(() -> {
            GuiScreen exportScreen = GuiExportSettings.createBaseScreen();
            GuiExportSettings settings = new GuiExportSettings(exportScreen, replayHandler, timeline) {
                @Override
                public void close() {
                    super.close();
                    getScreen().display();
                }
            };
            settings.open();
            exportScreen.display();
        })).setSize(100, 20).setLabel("Export replay file");
    }

}
