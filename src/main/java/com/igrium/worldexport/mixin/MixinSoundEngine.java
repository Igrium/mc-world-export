package com.igrium.worldexport.mixin;

import com.igrium.worldexport.audio.SoundCapture;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {
    @Inject(method="play", at=@At(value="INVOKE", target="Lnet/minecraft/client/resources/sounds/SoundInstance;getOrResolve(Lnet/minecraft/client/sounds/SoundManager;)Lnet/minecraft/client/sounds/WeighedSoundEvents;", shift=At.Shift.AFTER))
    private void worldexport$play(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> ci) { SoundCapture.played(sound); }
    @Inject(method="stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",at=@At("HEAD"))
    private void worldexport$stop(SoundInstance sound, CallbackInfo ci) { SoundCapture.stopped(sound); }
    @Inject(method="stop(Lnet/minecraft/resources/Identifier;Lnet/minecraft/sounds/SoundSource;)V",at=@At("HEAD"))
    private void worldexport$stopFilter(Identifier event, SoundSource source, CallbackInfo ci) { SoundCapture.stopped(event,source); }
    @Inject(method="stopAll",at=@At("HEAD"))
    private void worldexport$stopAll(CallbackInfo ci) { SoundCapture.stoppedAll(); }
}
