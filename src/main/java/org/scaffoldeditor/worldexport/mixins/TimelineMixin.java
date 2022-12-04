package org.scaffoldeditor.worldexport.mixins;

import org.scaffoldeditor.worldexport.replaymod.TimelineUpdateCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.replaymod.replaystudio.pathing.impl.TimelineImpl;
import com.replaymod.replaystudio.pathing.path.Timeline;

@Mixin(TimelineImpl.class)
public class TimelineMixin {
    
    @Inject(method = "applyToGame(JLjava/lang/Object;)V", at = @At("RETURN"), remap = false)
    void applyToGame(long time, Object replayHandler, CallbackInfo ci) {
        TimelineUpdateCallback.EVENT.invoker().onUpdate((Timeline) this, replayHandler, time);
    }
}
