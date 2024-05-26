package org.scaffoldeditor.worldexport.mixins;

import org.scaffoldeditor.worldexport.gui.GuiExportSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.replaymod.core.ReplayMod;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.render.gui.GuiRenderSettings;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;


@Mixin(value = GuiRenderSettings.class, remap = false)
public abstract class GuiRenderSettingsMixin {

    @Unique
    public GuiButton exportButton;

    @Shadow
    public abstract void close();
    
    @Shadow
    public abstract AbstractGuiScreen<?> getScreen();

    @Inject(method = "<init>", at = @At("RETURN"))
    public void initMixin(AbstractGuiScreen<?> container, ReplayHandler replayHandler, Timeline timeline, CallbackInfo ci) {
        exportButton = new GuiButton(((GuiRenderSettings)(Object) this).buttonPanel).onClick(() -> ReplayMod.instance.runLaterWithoutLock(() -> {

            GuiExportSettings screen = new GuiExportSettings(replayHandler, timeline);
            screen.prevScreen = getScreen();
            screen.display();

        })).setSize(100, 20).setLabel("Export replay file");
    }
}