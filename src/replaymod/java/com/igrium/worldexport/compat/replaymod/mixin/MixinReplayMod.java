package com.igrium.worldexport.compat.replaymod.mixin;

import com.igrium.worldexport.compat.replaymod.ReplayModHooks;
import com.replaymod.core.ReplayMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ReplayMod.class, remap = false)
public class MixinReplayMod {

    @Inject(method = "initModules", at = @At("RETURN"), remap = false)
    void afterInit(CallbackInfo ci) {
        ReplayModHooks.getOnInit().complete((ReplayMod)(Object) this);
    }
}
