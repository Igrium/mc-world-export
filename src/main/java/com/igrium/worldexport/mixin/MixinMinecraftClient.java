package com.igrium.worldexport.mixin;

import com.igrium.worldexport.event.ClientWorldEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Inject(method = "setWorld", at = @At("RETURN"))
    private void worldexport$onSetWorld(@Nullable ClientWorld world, CallbackInfo ci) {
        ClientWorldEvents.SET_WORLD.invoker().onSetWorld(world);
    }
}
