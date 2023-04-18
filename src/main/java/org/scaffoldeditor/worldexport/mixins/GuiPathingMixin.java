package org.scaffoldeditor.worldexport.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.Result;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.gui.overlay.GuiReplayOverlay;
import com.replaymod.replaystudio.pathing.path.Timeline;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import com.replaymod.simplepathing.gui.GuiPathing;

@Mixin(GuiPathing.class)
public class GuiPathingMixin {

    @Shadow(remap = false)
    private void abortPathPlayback() {
        throw new AssertionError();
    };

    @Shadow(remap = false)
    private Result<Timeline, String[]> preparePathsForPlayback(boolean ignoreTimeKeyframes) {
        throw new AssertionError();
    };

    @Shadow(remap = false)
    private ReplayHandler replayHandler;

    @Shadow(remap = false)
    public GuiReplayOverlay overlay;

    @Shadow(remap = false)
    public GuiPanel panel;

    public GuiButton exportButton;
    
    @Inject(method = "<init>", at = @At("RETURN"))
    public void constructorHead(ReplayMod core, ReplayModSimplePathing mod, ReplayHandler replayHandler, CallbackInfo ci) {

    }
}
