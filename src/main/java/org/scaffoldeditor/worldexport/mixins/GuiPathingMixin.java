package org.scaffoldeditor.worldexport.mixins;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.Result;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiInfoPopup;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.gui.overlay.GuiReplayOverlay;
import com.replaymod.replaystudio.pathing.path.Timeline;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import com.replaymod.simplepathing.gui.GuiPathing;

import org.scaffoldeditor.worldexport.replaymod.gui.GuiExportSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiPathing.class)
public abstract class GuiPathingMixin {

    @Shadow
    abstract void abortPathPlayback();

    @Shadow
    abstract Result<Timeline, String[]> preparePathsForPlayback(boolean ignoreTimeKeyframes);

    @Shadow
    private ReplayHandler replayHandler;

    @Shadow
    public GuiReplayOverlay overlay;

    @Shadow
    public GuiPanel panel;

    public GuiButton exportButton;
    
    @Inject(method = "<init>", at = @At("RETURN"))
    public void constructorHead(ReplayMod core, ReplayModSimplePathing mod, ReplayHandler replayHandler, CallbackInfo ci) {
        exportButton = new GuiButton(panel).onClick(() -> {
            abortPathPlayback();
    
            GuiScreen screen = new GuiScreen();
            screen.setBackground(AbstractGuiScreen.Background.NONE);
    
            new GuiExportSettings(screen, replayHandler, preparePathsForPlayback(false).okOrElse(err -> {
                GuiInfoPopup.open(overlay, err);
                return null;
            }));
    
            screen.display();
            
        }).setSize(20, 20).setTexture(ReplayMod.TEXTURE, ReplayMod.TEXTURE_SIZE).setSpriteUV(40, 0);
    }
}
